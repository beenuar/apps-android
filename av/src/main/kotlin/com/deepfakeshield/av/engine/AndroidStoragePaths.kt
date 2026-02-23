package com.deepfakeshield.av.engine

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

object AndroidStoragePaths {
    private const val TAG = "AndroidStoragePaths"

    fun getDownloadsPath(context: Context): String? {
        val paths = listOfNotNull(
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath.takeIf { File(it).exists() },
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath,
            "${context.filesDir}/downloads"
        )
        return paths.firstOrNull { File(it).exists() && File(it).isDirectory }
    }

    /** Check if we have full storage access on current Android version */
    fun hasFullStorageAccess(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            else -> true
        }
    }

    /** Paths for real-time watching (smaller set) - uses app-accessible dirs for Android 10+ */
    fun getScanPaths(context: Context): List<String> {
        val paths = mutableListOf<String>()
        getDownloadsPath(context)?.let { paths.add(it) }
        context.getExternalFilesDir(null)?.absolutePath?.let { paths.add(it) }
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath?.let { paths.add(it) }
        context.getExternalCacheDir()?.absolutePath?.let { paths.add(it) }
        // NOTE: context.filesDir and context.cacheDir are EXCLUDED intentionally.
        // They contain app-internal data (hash DBs, quarantine, Room DBs, DataStore,
        // WorkManager DBs, export temp files) — never user-downloaded files.
        // Scanning them wastes resources and risks false positives from our own
        // malware signature database being flagged as a threat.

        // If we have full storage access, also watch public directories
        if (hasFullStorageAccess(context)) {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads.exists() && downloads.canRead()) paths.add(downloads.absolutePath)
        }

        return paths.filter { File(it).exists() && File(it).isDirectory }.distinct()
    }

    /** Paths for full scan - Download, Documents, DCIM, and app-specific storage */
    fun getFullScanPaths(context: Context): List<String> {
        val paths = mutableListOf<String>()
        val hasAccess = hasFullStorageAccess(context)

        if (hasAccess) {
            val dirs = listOf(
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DOCUMENTS,
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_PICTURES,
                Environment.DIRECTORY_MOVIES,
                Environment.DIRECTORY_MUSIC
            )
            for (dir in dirs) {
                @Suppress("DEPRECATION")
                val p = Environment.getExternalStoragePublicDirectory(dir)
                if (p.exists() && p.canRead()) {
                    paths.add(p.absolutePath)
                    Log.d(TAG, "Added scan path: ${p.absolutePath}")
                } else {
                    Log.w(TAG, "Cannot access: ${p.absolutePath} (exists=${p.exists()}, canRead=${p.canRead()})")
                }
            }

            // Also scan external storage root on Android 11+ with MANAGE_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val externalRoot = Environment.getExternalStorageDirectory()
                if (externalRoot.exists() && externalRoot.canRead()) {
                    // Add common app install locations
                    val apkDirs = listOf("Android/data", "Android/obb")
                    for (apkDir in apkDirs) {
                        val dir = File(externalRoot, apkDir)
                        if (dir.exists() && dir.canRead()) {
                            paths.add(dir.absolutePath)
                        }
                    }
                }
            }
        } else {
            Log.w(TAG, "No full storage access - can only scan app-private directories")
        }

        // Include external app-specific directories (accessible without permissions).
        // NOTE: context.filesDir and context.cacheDir are EXCLUDED — they only contain
        // app-internal data (hash DBs, quarantine, Room DBs, DataStore prefs, caches).
        context.getExternalFilesDir(null)?.absolutePath?.let { paths.add(it) }
        context.getExternalCacheDir()?.absolutePath?.let { paths.add(it) }

        return paths.distinct().filter { File(it).exists() }
    }
}
