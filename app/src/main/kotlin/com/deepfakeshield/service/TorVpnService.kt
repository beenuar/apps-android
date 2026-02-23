package com.deepfakeshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.deepfakeshield.MainActivity
import com.deepfakeshield.R
import com.deepfakeshield.core.network.TorNetworkModule
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TorVpnService : VpnService() {

    companion object {
        private const val TAG = "TorVPN"
        private const val CHANNEL_ID = "tor_vpn_channel"
        private const val NOTIF_ID = 9051
        private const val MTU = 1500

        @Volatile var isRunning = false
            private set

        fun start(ctx: Context) {
            val i = Intent(ctx, TorVpnService::class.java).apply { action = "START" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, TorVpnService::class.java).apply { action = "STOP" })
        }
    }

    private var vpnFd: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var reader: Thread? = null
    private val pool = Executors.newCachedThreadPool()
    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()
    private var tunOut: FileOutputStream? = null

    override fun onCreate() { super.onCreate(); createChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { shutdown(); stopSelf() } else startTunnel()
        return START_STICKY
    }

    override fun onDestroy() { shutdown(); super.onDestroy() }
    override fun onRevoke() { shutdown(); stopSelf(); super.onRevoke() }

    private fun startTunnel() {
        if (running.get()) return
        if (!TorNetworkModule.isConnected) { Log.e(TAG, "Tor not connected"); stopSelf(); return }
        val socksPort = TorNetworkModule.proxyPort

        try {
            val b = Builder().setSession("Cyble Tor").setMtu(MTU)
                .addAddress("10.10.10.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("10.10.10.1")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hp = org.torproject.jni.TorService.httpTunnelPort
                if (hp > 0) { b.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", hp)); Log.i(TAG, "httpProxy :$hp") }
            }
            try { b.addDisallowedApplication(packageName) } catch (_: Exception) {}

            vpnFd = b.establish() ?: run { Log.e(TAG, "establish() null"); stopSelf(); return }
            startForeground(NOTIF_ID, buildNotif("All traffic through Tor"))
            running.set(true); isRunning = true
            TorNetworkModule.updateVpnState(true)
            tunOut = FileOutputStream(vpnFd!!.fileDescriptor)

            reader = Thread({
                val input = FileInputStream(vpnFd!!.fileDescriptor)
                val buf = ByteArray(MTU)
                Log.i(TAG, "Packet loop, SOCKS=$socksPort")
                while (running.get()) {
                    try {
                        val n = input.read(buf)
                        if (n <= 0) { Thread.sleep(5); continue }
                        dispatch(buf, n, socksPort)
                    } catch (_: InterruptedException) { break }
                    catch (e: IOException) { if (running.get()) Log.w(TAG, "read: ${e.message}"); break }
                }
            }, "VPN-Read").also { it.start() }
            Log.i(TAG, "VPN up")
        } catch (e: Exception) {
            Log.e(TAG, "VPN fail: ${e.message}", e); shutdown(); stopSelf()
        }
    }

    private fun shutdown() {
        running.set(false); isRunning = false
        TorNetworkModule.updateVpnState(false)
        reader?.interrupt(); reader = null
        tcpSessions.values.forEach { it.close() }; tcpSessions.clear()
        try { vpnFd?.close() } catch (_: Exception) {}
        vpnFd = null; tunOut = null
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        Log.i(TAG, "VPN down")
    }

    // ── Dispatch ───────────────────────────────────────────────────────

    private fun dispatch(raw: ByteArray, len: Int, socks: Int) {
        if (len < 20 || (raw[0].toInt() ushr 4 and 0xF) != 4) return
        val ihl = (raw[0].toInt() and 0xF) * 4
        val total = u16(raw, 2)
        if (total > len || total < ihl) return
        val proto = raw[9].toInt() and 0xFF
        val src = raw.copyOfRange(12, 16)
        val dst = raw.copyOfRange(16, 20)
        when (proto) {
            6 -> onTcp(raw, ihl, total, src, dst, socks)
            17 -> onUdp(raw, ihl, total, src, dst, socks)
        }
    }

    // ── TCP ────────────────────────────────────────────────────────────

    private fun onTcp(raw: ByteArray, ihl: Int, total: Int, src: ByteArray, dst: ByteArray, socks: Int) {
        if (total < ihl + 20) return
        val sp = u16(raw, ihl); val dp = u16(raw, ihl + 2)
        val seq = u32(raw, ihl + 4)
        val doff = ((raw[ihl + 12].toInt() ushr 4) and 0xF) * 4
        val fl = raw[ihl + 13].toInt() and 0x3F
        val syn = fl and 2 != 0; val ack = fl and 16 != 0; val fin = fl and 1 != 0; val rst = fl and 4 != 0

        val key = "${ip(src)}:$sp>${ip(dst)}:$dp"

        if (rst) { tcpSessions.remove(key)?.close(); return }

        if (syn && !ack) {
            val existing = tcpSessions[key]
            if (existing != null && (existing.state == SessionState.CONNECTING || existing.state == SessionState.ESTABLISHED)) {
                if (existing.state == SessionState.ESTABLISHED) {
                    tun(tcpPkt(dst, dp, src, sp, existing.ss, seq + 1, 0x12, EMPTY))
                }
                return
            }
            existing?.close()

            val s = TcpSession(src, sp, dst, dp, seq)
            s.state = SessionState.CONNECTING
            tcpSessions[key] = s

            pool.execute {
                try {
                    val sock = Socket()
                    protect(sock)
                    sock.soTimeout = 60_000
                    sock.connect(InetSocketAddress("127.0.0.1", socks), 20_000)
                    socks5(sock, ip(dst), dp)
                    s.remote = sock
                    s.state = SessionState.ESTABLISHED
                    s.cAck = seq + 1
                    tun(tcpPkt(dst, dp, src, sp, s.ss, s.cAck, 0x12, EMPTY))
                    s.ss++

                    val inp = sock.getInputStream()
                    val b = ByteArray(MTU - 40)
                    while (running.get() && s.state == SessionState.ESTABLISHED) {
                        val n = inp.read(b)
                        if (n <= 0) break
                        tun(tcpPkt(dst, dp, src, sp, s.ss, s.cAck, 0x18, b.copyOf(n)))
                        s.ss += n
                    }
                } catch (e: Exception) {
                    if (running.get()) Log.d(TAG, "tcp ${ip(dst)}:$dp: ${e.message}")
                } finally {
                    if (s.state != SessionState.CLOSED) {
                        tun(tcpPkt(dst, dp, src, sp, s.ss, s.cAck, 0x11, EMPTY))
                    }
                    s.state = SessionState.CLOSED; s.close()
                    tcpSessions.remove(key)
                }
            }
            return
        }

        val s = tcpSessions[key] ?: return

        if (fin) {
            tun(tcpPkt(dst, dp, src, sp, s.ss, seq + 1, 0x11, EMPTY))
            s.state = SessionState.CLOSED; s.close(); tcpSessions.remove(key)
            return
        }

        val payOff = ihl + doff; val payLen = total - payOff
        if (payLen > 0 && s.state == SessionState.ESTABLISHED && s.remote != null) {
            s.cAck = seq + payLen
            tun(tcpPkt(dst, dp, src, sp, s.ss, s.cAck, 0x10, EMPTY))
            val data = raw.copyOfRange(payOff, payOff + payLen)
            pool.execute {
                try { s.remote?.getOutputStream()?.apply { write(data); flush() } }
                catch (_: Exception) { s.state = SessionState.CLOSED; s.close(); tcpSessions.remove(key) }
            }
        }
    }

    // ── UDP ────────────────────────────────────────────────────────────

    private fun onUdp(raw: ByteArray, ihl: Int, total: Int, src: ByteArray, dst: ByteArray, socks: Int) {
        if (total < ihl + 8) return
        val sp = u16(raw, ihl); val dp = u16(raw, ihl + 2)
        val uLen = u16(raw, ihl + 4); val po = ihl + 8; val pl = uLen - 8
        if (pl <= 0 || po + pl > total) return

        val payload = raw.copyOfRange(po, po + pl)

        if (dp == 53) {
            pool.execute {
                val resp = dns(payload)
                if (resp != null) tun(udpPkt(dst, dp, src, sp, resp))
            }
        } else {
            pool.execute { fwdUdp(payload, dst, dp, src, sp) }
        }
    }

    private fun dns(query: ByteArray): ByteArray? {
        var ds: DatagramSocket? = null
        return try {
            ds = DatagramSocket(); protect(ds); ds.soTimeout = 10_000
            ds.send(DatagramPacket(query, query.size, InetAddress.getByName("1.1.1.1"), 53))
            val resp = ByteArray(2048)
            val rp = DatagramPacket(resp, resp.size)
            ds.receive(rp); resp.copyOf(rp.length)
        } catch (e: Exception) { Log.d(TAG, "dns: ${e.message}"); null }
        finally { try { ds?.close() } catch (_: Exception) {} }
    }

    private fun fwdUdp(data: ByteArray, dstIp: ByteArray, dstPort: Int, srcIp: ByteArray, srcPort: Int) {
        var ds: DatagramSocket? = null
        try {
            ds = DatagramSocket(); protect(ds); ds.soTimeout = 10_000
            ds.send(DatagramPacket(data, data.size, InetAddress.getByAddress(dstIp), dstPort))
            val resp = ByteArray(2048)
            val rp = DatagramPacket(resp, resp.size)
            ds.receive(rp)
            tun(udpPkt(dstIp, dstPort, srcIp, srcPort, resp.copyOf(rp.length)))
        } catch (_: Exception) {}
        finally { try { ds?.close() } catch (_: Exception) {} }
    }

    // ── SOCKS5 ─────────────────────────────────────────────────────────

    private fun socks5(sock: Socket, host: String, port: Int) {
        val o = sock.getOutputStream(); val i = sock.getInputStream()
        o.write(byteArrayOf(5, 1, 0)); o.flush(); exact(i, 2)
        val hb = host.toByteArray(Charsets.US_ASCII)
        val r = ByteBuffer.allocate(7 + hb.size)
        r.put(5).put(1).put(0).put(3).put(hb.size.toByte()).put(hb)
        r.put((port shr 8 and 0xFF).toByte()).put((port and 0xFF).toByte())
        o.write(r.array()); o.flush()
        val h = exact(i, 4)
        if (h[1] != 0.toByte()) throw IOException("SOCKS5 err ${h[1]}")
        when (h[3].toInt() and 0xFF) {
            1 -> exact(i, 6); 3 -> { val n = exact(i, 1)[0].toInt() and 0xFF; exact(i, n + 2) }
            4 -> exact(i, 18); else -> exact(i, 6)
        }
    }

    private fun exact(i: InputStream, n: Int): ByteArray {
        val b = ByteArray(n); var r = 0
        while (r < n) { val x = i.read(b, r, n - r); if (x < 0) throw IOException("eof"); r += x }
        return b
    }

    // ── Packet builders ────────────────────────────────────────────────

    private val EMPTY = ByteArray(0)

    @Synchronized
    private fun tun(p: ByteArray) { try { tunOut?.write(p); tunOut?.flush() } catch (_: Exception) {} }

    private fun tcpPkt(si: ByteArray, sp: Int, di: ByteArray, dp: Int, seq: Long, ack: Long, fl: Int, pay: ByteArray): ByteArray {
        val t = 40 + pay.size; val p = ByteArray(t)
        p[0] = 0x45.toByte(); w16(p, 2, t); p[6] = 0x40.toByte(); p[8] = 64; p[9] = 6
        System.arraycopy(si, 0, p, 12, 4); System.arraycopy(di, 0, p, 16, 4); ipCk(p)
        w16(p, 20, sp); w16(p, 22, dp); w32(p, 24, seq); w32(p, 28, ack)
        p[32] = (5 shl 4).toByte(); p[33] = fl.toByte(); w16(p, 34, 65535)
        if (pay.isNotEmpty()) System.arraycopy(pay, 0, p, 40, pay.size)
        tcpCk(p, 20, 20 + pay.size, si, di); return p
    }

    private fun udpPkt(si: ByteArray, sp: Int, di: ByteArray, dp: Int, pay: ByteArray): ByteArray {
        val t = 28 + pay.size; val p = ByteArray(t)
        p[0] = 0x45.toByte(); w16(p, 2, t); p[6] = 0x40.toByte(); p[8] = 64; p[9] = 17
        System.arraycopy(si, 0, p, 12, 4); System.arraycopy(di, 0, p, 16, 4); ipCk(p)
        w16(p, 20, sp); w16(p, 22, dp); w16(p, 24, 8 + pay.size)
        System.arraycopy(pay, 0, p, 28, pay.size); return p
    }

    // ── Checksums ──────────────────────────────────────────────────────

    private fun ipCk(p: ByteArray) { p[10] = 0; p[11] = 0; val c = ck(p, 0, 20); p[10] = (c shr 8).toByte(); p[11] = c.toByte() }

    private fun tcpCk(p: ByteArray, off: Int, len: Int, si: ByteArray, di: ByteArray) {
        p[off + 16] = 0; p[off + 17] = 0
        val ps = ByteArray(12 + len)
        System.arraycopy(si, 0, ps, 0, 4); System.arraycopy(di, 0, ps, 4, 4)
        ps[9] = 6; w16(ps, 10, len); System.arraycopy(p, off, ps, 12, len)
        val c = ck(ps, 0, ps.size); p[off + 16] = (c shr 8).toByte(); p[off + 17] = c.toByte()
    }

    private fun ck(d: ByteArray, o: Int, l: Int): Int {
        var s = 0L; var i = o; var n = l
        while (n > 1) { s += ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF); i += 2; n -= 2 }
        if (n > 0) s += (d[i].toInt() and 0xFF) shl 8
        while (s ushr 16 != 0L) s = (s and 0xFFFF) + (s ushr 16)
        return s.toInt().inv() and 0xFFFF
    }

    // ── Util ───────────────────────────────────────────────────────────

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, o: Int): Long = ((b[o].toLong() and 0xFF) shl 24) or ((b[o+1].toLong() and 0xFF) shl 16) or ((b[o+2].toLong() and 0xFF) shl 8) or (b[o+3].toLong() and 0xFF)
    private fun w16(b: ByteArray, o: Int, v: Int) { b[o] = (v shr 8).toByte(); b[o + 1] = v.toByte() }
    private fun w32(b: ByteArray, o: Int, v: Long) { b[o] = (v shr 24).toByte(); b[o+1] = (v shr 16).toByte(); b[o+2] = (v shr 8).toByte(); b[o+3] = v.toByte() }
    private fun ip(b: ByteArray) = "${b[0].toInt() and 0xFF}.${b[1].toInt() and 0xFF}.${b[2].toInt() and 0xFF}.${b[3].toInt() and 0xFF}"

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Tor VPN", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        }
    }
    private fun buildNotif(t: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Tor VPN").setContentText(t)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setOngoing(true).setContentIntent(pi).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private enum class SessionState { CONNECTING, ESTABLISHED, CLOSED }

    private class TcpSession(val ci: ByteArray, val cp: Int, val si: ByteArray, val sp: Int, cSeq: Long) {
        var ss: Long = System.nanoTime() and 0x7FFFFFFFL
        var cAck: Long = cSeq
        var remote: Socket? = null
        @Volatile var state = SessionState.CONNECTING
        fun close() { state = SessionState.CLOSED; try { remote?.close() } catch (_: Exception) {}; remote = null }
    }
}
