package com.deepfakeshield.av.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive device integrity checker.
 * Detects root, Magisk, Xposed, Frida, hooking frameworks, emulators, and tampering.
 */
@Singleton
class RootDetector @Inject constructor() {

    data class RootStatus(
        val isRooted: Boolean,
        val reasons: List<String>,
        val riskLevel: String,
        val hookingDetected: Boolean,
        val emulatorDetected: Boolean,
        val tamperingDetected: Boolean
    )

    fun checkRootStatus(): RootStatus {
        val reasons = mutableListOf<String>()
        var hooking = false
        var emulator = false
        var tampering = false

        // ── 1. Root binary paths (25 paths) ─────────────────────
        ROOT_BINARIES.forEach { path ->
            if (File(path).exists()) reasons += "Root binary: $path"
        }

        // ── 2. Root management apps (15 packages) ───────────────
        ROOT_PACKAGES.forEach { pkg ->
            if (isPackageInstalled(pkg)) reasons += "Root app: $pkg"
        }

        // ── 3. Magisk detection ─────────────────────────────────
        MAGISK_PATHS.forEach { path ->
            if (File(path).exists()) reasons += "Magisk: $path"
        }
        if (isPropSet("init.svc.magisk_daemon")) reasons += "Magisk daemon running"
        if (isPropSet("persist.magisk.hide")) reasons += "MagiskHide active"

        // ── 4. Xposed framework ─────────────────────────────────
        XPOSED_INDICATORS.forEach { path ->
            if (File(path).exists()) { reasons += "Xposed: $path"; hooking = true }
        }
        XPOSED_PACKAGES.forEach { pkg ->
            if (isPackageInstalled(pkg)) { reasons += "Xposed app: $pkg"; hooking = true }
        }
        try {
            val stack = Throwable().stackTrace.map { it.className }
            if (stack.any { it.contains("de.robv.android.xposed") }) { reasons += "Xposed in call stack"; hooking = true }
        } catch (_: Exception) {}

        // ── 5. Frida detection ──────────────────────────────────
        FRIDA_INDICATORS.forEach { path ->
            if (File(path).exists()) { reasons += "Frida: $path"; hooking = true }
        }
        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("frida") || maps.contains("gadget")) { reasons += "Frida in process maps"; hooking = true }
        } catch (_: Exception) {}
        FRIDA_PORTS.forEach { port ->
            try {
                val sock = java.net.Socket()
                sock.connect(java.net.InetSocketAddress("127.0.0.1", port), 500)
                sock.close()
                reasons += "Frida server on port $port"; hooking = true
            } catch (_: Exception) {}
        }

        // ── 6. Other hooking frameworks ─────────────────────────
        HOOK_LIBRARIES.forEach { lib ->
            try {
                val maps = File("/proc/self/maps").readText()
                if (maps.contains(lib)) { reasons += "Hook library: $lib"; hooking = true }
            } catch (_: Exception) {}
        }

        // ── 7. SELinux status ───────────────────────────────────
        try {
            val selinux = exec("getenforce")
            if (selinux.equals("permissive", true) || selinux.equals("disabled", true)) {
                reasons += "SELinux: $selinux"; tampering = true
            }
        } catch (_: Exception) {}

        // ── 8. Build properties ─────────────────────────────────
        if (Build.TAGS?.contains("test-keys") == true) { reasons += "Test-keys build"; tampering = true }
        if (Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("unknown")) { reasons += "Suspicious fingerprint: ${Build.FINGERPRINT.take(30)}"; tampering = true }
        val debuggable = getProp("ro.debuggable")
        if (debuggable == "1") { reasons += "ro.debuggable=1"; tampering = true }
        val secure = getProp("ro.secure")
        if (secure == "0") { reasons += "ro.secure=0"; tampering = true }

        // ── 9. su command execution ─────────────────────────────
        try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val result = reader.readLine()
            p.waitFor()
            if (!result.isNullOrBlank()) reasons += "su found at: $result"
        } catch (_: Exception) {}

        // ── 10. Writable system partitions ──────────────────────
        try {
            val mounts = File("/proc/mounts").readText()
            if (mounts.contains("/system") && mounts.contains("rw,")) { reasons += "/system mounted read-write"; tampering = true }
        } catch (_: Exception) {}

        // ── 11. Emulator detection ──────────────────────────────
        val emuReasons = mutableListOf<String>()
        if (Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.startsWith("unknown")) emuReasons += "Generic fingerprint"
        if (Build.MODEL.contains("sdk", true) || Build.MODEL.contains("Emulator") || Build.MODEL.contains("Android SDK")) emuReasons += "Emulator model: ${Build.MODEL}"
        if (Build.MANUFACTURER.contains("Genymotion")) emuReasons += "Genymotion manufacturer"
        if (Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu")) emuReasons += "Emulator hardware: ${Build.HARDWARE}"
        if (Build.PRODUCT.contains("sdk") || Build.PRODUCT.contains("vbox") || Build.PRODUCT.contains("emulator")) emuReasons += "Emulator product: ${Build.PRODUCT}"
        if (Build.BOARD.contains("unknown", true) || Build.BOARD == "") emuReasons += "Unknown board"
        EMULATOR_FILES.forEach { f -> if (File(f).exists()) emuReasons += "Emulator file: $f" }
        try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            if (cpuInfo.contains("hypervisor", true) || cpuInfo.contains("GenuineIntel") || cpuInfo.contains("QEMU")) emuReasons += "Virtual CPU detected"
        } catch (_: Exception) {}
        if (emuReasons.isNotEmpty()) {
            emulator = true
            reasons += emuReasons.map { "Emulator: $it" }
        }

        // ── 12. Debugger detection ──────────────────────────────
        if (android.os.Debug.isDebuggerConnected()) { reasons += "Debugger attached"; tampering = true }
        try {
            val tracerPid = File("/proc/self/status").readLines().find { it.startsWith("TracerPid:") }
            val pid = tracerPid?.substringAfter(":")?.trim()?.toIntOrNull()
            if (pid != null && pid > 0) { reasons += "Process being traced (PID: $pid)"; tampering = true }
        } catch (_: Exception) {}

        // ── 13. Busybox ─────────────────────────────────────────
        BUSYBOX_PATHS.forEach { path ->
            if (File(path).exists()) reasons += "BusyBox: $path"
        }

        // ── Risk level ──────────────────────────────────────────
        val risk = when {
            hooking -> "CRITICAL"
            reasons.size >= 5 -> "HIGH"
            reasons.size >= 2 -> "MEDIUM"
            reasons.isNotEmpty() -> "LOW"
            else -> "CLEAN"
        }

        return RootStatus(reasons.isNotEmpty(), reasons, risk, hooking, emulator, tampering)
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            File("/data/data/$pkg").exists() ||
            File("/data/user/0/$pkg").exists()
        } catch (_: Exception) { false }
    }

    private fun getProp(name: String): String {
        return try { exec("getprop $name").trim() } catch (_: Exception) { "" }
    }

    private fun isPropSet(name: String): Boolean = getProp(name).isNotBlank()

    private fun exec(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(cmd.split(" ").toTypedArray())
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val result = r.readText().trim()
            p.waitFor()
            result
        } catch (_: Exception) { "" }
    }

    companion object {
        private val ROOT_BINARIES = listOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/tmp/su", "/su/bin/su", "/su/bin",
            "/system/xbin/daemonsu", "/system/etc/.installed_su_daemon", "/dev/com.koushikdutta.superuser.daemon",
            "/system/app/Superuser", "/system/app/superuser", "/system/app/SuperSU",
            "/system/etc/init.d/99SuperSUDaemon", "/system/xbin/.su", "/data/adb/su/bin/su",
            "/vendor/bin/su", "/odm/bin/su", "/product/bin/su",
            "/apex/com.android.runtime/bin/su", "/system_ext/bin/su"
        )

        private val ROOT_PACKAGES = listOf(
            "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.noshufou.android.su",
            "com.koushikdutta.superuser", "com.thirdparty.superuser", "com.yellowes.su",
            "com.kingroot.kinguser", "com.kingo.root", "com.smedialink.oneclean",
            "com.zhiqupk.root.global", "com.alephzain.framaroot", "com.tencent.superroot",
            "com.devadvance.rootcloak", "com.devadvance.rootcloakplus",
            "de.robv.android.xposed.installer"
        )

        private val MAGISK_PATHS = listOf(
            "/data/adb/magisk", "/sbin/.magisk", "/cache/.disable_magisk",
            "/dev/.magisk.unblock", "/data/adb/magisk.img", "/data/adb/magisk.db",
            "/data/adb/modules", "/data/magisk.apk"
        )

        private val XPOSED_INDICATORS = listOf(
            "/system/framework/XposedBridge.jar", "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so", "/system/xposed.prop",
            "/data/misc/riru/modules/edxp", "/data/adb/modules/riru_edxposed",
            "/data/adb/modules/zygisk_lsposed", "/data/adb/lspd"
        )

        private val XPOSED_PACKAGES = listOf(
            "de.robv.android.xposed.installer", "org.meowcat.edxposed.manager",
            "org.lsposed.manager", "com.solohsu.android.edxp.manager",
            "io.github.lsposed.manager"
        )

        private val FRIDA_INDICATORS = listOf(
            "/data/local/tmp/frida-server", "/data/local/tmp/re.frida.server",
            "/data/local/tmp/frida-agent", "/data/local/tmp/frida-gadget.so",
            "/system/lib/libfrida-gadget.so", "/system/lib64/libfrida-gadget.so"
        )

        private val FRIDA_PORTS = listOf(27042, 27043)

        private val HOOK_LIBRARIES = listOf(
            "libsubstrate.so", "libxhook.so", "libandroid_runtime.so.bak",
            "libart.so.bak", "cydia", "substrate"
        )

        private val EMULATOR_FILES = listOf(
            "/dev/socket/qemud", "/dev/qemu_pipe", "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace", "/system/bin/qemu-props", "/dev/goldfish_pipe",
            "/system/lib/libdroid4x.so", "/system/bin/windroye", "/system/bin/microvirtd",
            "/system/bin/nox-prop", "/system/lib/libhoudini.so"
        )

        private val BUSYBOX_PATHS = listOf(
            "/system/xbin/busybox", "/system/bin/busybox", "/sbin/busybox",
            "/data/local/tmp/busybox", "/su/bin/busybox"
        )
    }
}
