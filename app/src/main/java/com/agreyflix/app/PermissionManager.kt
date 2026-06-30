package com.agreyflix.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: AppCompatActivity) {

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private var onPermissionsResultListener: ((Map<String, Boolean>) -> Unit)? = null

    init {
        initLauncher()
    }

    private fun initLauncher() {
        requestPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            onPermissionsResultListener?.invoke(permissions)
        }
    }

    /**
     * Check if specific permission is already granted.
     */
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get a list of permissions that are not yet granted.
     */
    fun getPendingPermissions(): List<String> {
        val pendingList = mutableListOf<String>()

        // 1. Core Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
                pendingList.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Media / Storage Access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES)) {
                pendingList.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (!isPermissionGranted(Manifest.permission.READ_MEDIA_VIDEO)) {
                pendingList.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (!isPermissionGranted(Manifest.permission.READ_MEDIA_AUDIO)) {
                pendingList.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (!isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                pendingList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // 3. Camera Access (for profile images / stream features)
        if (!isPermissionGranted(Manifest.permission.CAMERA)) {
            pendingList.add(Manifest.permission.CAMERA)
        }

        // 4. Record Audio (for voice queries)
        if (!isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
            pendingList.add(Manifest.permission.RECORD_AUDIO)
        }

        // 5. Geolocation / Location
        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            pendingList.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            pendingList.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        return pendingList
    }

    /**
     * Requests critical permissions with a beautiful rationale explaining why they are needed.
     */
    fun requestAppPermissions(
        onResult: (Map<String, Boolean>) -> Unit
    ) {
        val pending = getPendingPermissions()
        if (pending.isEmpty()) {
            onResult(emptyMap())
            return
        }

        this.onPermissionsResultListener = onResult

        // Build rationale description dynamically
        val rationaleMessage = StringBuilder()
        rationaleMessage.append("To ensure the most immersive streaming experience on AgreyFlix, we request the following permissions:\n\n")

        if (pending.contains(Manifest.permission.POST_NOTIFICATIONS) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && pending.contains("android.permission.POST_NOTIFICATIONS"))) {
            rationaleMessage.append("• Notifications: Stay updated about newly released blockbuster titles.\n")
        }
        if (pending.contains(Manifest.permission.CAMERA)) {
            rationaleMessage.append("• Camera: Upload profile avatars and scan streaming codes.\n")
        }
        if (pending.contains(Manifest.permission.RECORD_AUDIO)) {
            rationaleMessage.append("• Microphone: Search for movies and TV shows using voice commands.\n")
        }
        if (pending.any { it.contains("MEDIA") || it.contains("STORAGE") }) {
            rationaleMessage.append("• Media & Storage: Cache watchlist metadata and select high-quality background art.\n")
        }
        if (pending.contains(Manifest.permission.ACCESS_FINE_LOCATION) || pending.contains(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            rationaleMessage.append("• Location: Stream region-locked local channels and receive relevant content recommendations.\n")
        }

        AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(activity.getString(R.string.permission_storage_title))
            .setMessage(rationaleMessage.toString())
            .setPositiveButton(activity.getString(R.string.btn_allow)) { _, _ ->
                requestPermissionLauncher.launch(pending.toTypedArray())
            }
            .setNegativeButton(activity.getString(R.string.btn_cancel)) { dialog, _ ->
                dialog.dismiss()
                // Return empty map but indicate they are cancelled
                onResult(pending.associateWith { false })
            }
            .setCancelable(false)
            .show()
    }
}
