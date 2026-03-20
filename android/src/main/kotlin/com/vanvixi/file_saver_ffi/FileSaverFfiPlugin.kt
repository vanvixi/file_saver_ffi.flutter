package com.vanvixi.file_saver_ffi

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.vanvixi.file_saver_ffi.utils.StoragePermissionHandler
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Flutter plugin for file_saver_ffi.
 *
 * Handles:
 * - WRITE_EXTERNAL_STORAGE runtime permission for Android 9 and below
 *
 * Directory picking is handled by the dir_picker package.
 */
class FileSaverFfiPlugin :
    FlutterPlugin,
    ActivityAware,
    RequestPermissionsResultListener {
    companion object {
        private const val TAG = "FileSaverFfi"
        private const val REQUEST_CODE_STORAGE_PERMISSION = 43892

        // Unique key for ActivityResultRegistry
        private const val PERMISSION_REGISTRY_KEY = "com.vanvixi.file_saver_ffi.storage_perm"
    }

    private var activity: Activity? = null
    private var activityBinding: ActivityPluginBinding? = null

    // Storage permission handling
    private var permissionLauncher: ActivityResultLauncher<String>? = null
    private var pendingPermissionContinuation: CancellableContinuation<Boolean>? = null

    // ─────────────────────────────────────────────────────────────────────────
    // FlutterPlugin
    // ─────────────────────────────────────────────────────────────────────────

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        FileSaver.appContext = binding.applicationContext
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        FileSaver.appContext = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ActivityAware
    // ─────────────────────────────────────────────────────────────────────────

    override fun onAttachedToActivity(binding: ActivityPluginBinding) = attachActivity(binding)

    override fun onDetachedFromActivityForConfigChanges() = detachActivity()

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) = attachActivity(binding)

    override fun onDetachedFromActivity() = detachActivity()

    private fun attachActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        activityBinding = binding
        binding.addRequestPermissionsResultListener(this)
        setupPermissionLauncher(binding.activity)
        setupStoragePermissionHandler()
    }

    private fun detachActivity() {
        activityBinding?.removeRequestPermissionsResultListener(this)
        activityBinding = null
        activity = null

        permissionLauncher?.unregister()
        permissionLauncher = null

        FileSaver.storagePermissionHandler = null
        pendingPermissionContinuation?.let { if (it.isActive) it.resume(false) }
        pendingPermissionContinuation = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Storage Permission (Android 9 and below)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sets up ActivityResultLauncher for WRITE_EXTERNAL_STORAGE permission request.
     * Only registers on ComponentActivity (AndroidX).
     */
    private fun setupPermissionLauncher(activity: Activity) {
        if (activity !is ComponentActivity) return

        permissionLauncher =
            activity.activityResultRegistry.register(
                PERMISSION_REGISTRY_KEY,
                ActivityResultContracts.RequestPermission(),
            ) { isGranted: Boolean ->
                handlePermissionResult(isGranted)
            }
    }

    /**
     * Registers a [StoragePermissionHandler] on [FileSaver] that transparently
     * requests WRITE_EXTERNAL_STORAGE when needed (API < 29).
     *
     * The handler:
     * 1. Checks if permission is already granted (early return)
     * 2. Switches to Main thread to show permission dialog
     * 3. Suspends until user responds via [suspendCancellableCoroutine]
     */
    private fun setupStoragePermissionHandler() {
        FileSaver.storagePermissionHandler =
            StoragePermissionHandler {
                val currentActivity = activity ?: return@StoragePermissionHandler false

                // Double-check: might have been granted since FileSaver checked
                if (ContextCompat.checkSelfPermission(
                        currentActivity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    return@StoragePermissionHandler true
                }

                // Must launch permission dialog on Main thread
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { continuation ->
                        pendingPermissionContinuation = continuation

                        continuation.invokeOnCancellation {
                            pendingPermissionContinuation = null
                        }

                        // Try modern launcher first (ComponentActivity)
                        val launcher = permissionLauncher
                        if (launcher != null) {
                            try {
                                launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                return@suspendCancellableCoroutine
                            } catch (e: Exception) {
                                Log.w(TAG, "Permission launcher failed, falling back: ${e.message}")
                            }
                        }

                        // Fallback: ActivityCompat for non-ComponentActivity
                        val act = activity
                        if (act != null) {
                            ActivityCompat.requestPermissions(
                                act,
                                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                REQUEST_CODE_STORAGE_PERMISSION,
                            )
                        } else {
                            pendingPermissionContinuation = null
                            continuation.resume(false)
                        }
                    }
                }
            }
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        val continuation = pendingPermissionContinuation
        pendingPermissionContinuation = null
        if (continuation != null && continuation.isActive) {
            continuation.resume(isGranted)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RequestPermissionsResultListener (fallback for non-ComponentActivity)
    // ─────────────────────────────────────────────────────────────────────────

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ): Boolean {
        if (requestCode != REQUEST_CODE_STORAGE_PERMISSION) return false

        val granted =
            grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
        handlePermissionResult(granted)
        return true
    }
}
