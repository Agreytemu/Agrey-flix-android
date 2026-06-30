package com.agreyflix.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var webViewManager: WebViewManager
    private lateinit var mediaAccessManager: MediaAccessManager

    private lateinit var progressBar: ProgressBar
    private lateinit var offlineContainer: LinearLayout
    private lateinit var btnRetry: Button

    // Register file chooser activity launcher and delegate results to WebViewManager
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (::webViewManager.isInitialized) {
            webViewManager.onFileChooserResult(result.resultCode, result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Android 12+ Splash Screen support
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        progressBar = findViewById(R.id.progressBar)
        offlineContainer = findViewById(R.id.offlineContainer)
        btnRetry = findViewById(R.id.btnRetry)

        // Create modular native controllers
        webViewManager = WebViewManager(this, fileChooserLauncher)
        permissionManager = PermissionManager(this)
        mediaAccessManager = MediaAccessManager(this)

        // Setup WebView via modern WebViewManager
        val webView = findViewById<android.webkit.WebView>(R.id.webView)
        webViewManager.initialize(webView, progressBar, offlineContainer)

        // Setup Back Button Navigation
        setupBackNavigation()

        // Ask for permissions upon launching the app
        requestAppPermissionsOnLaunch()

        // Handle retry connection clicks
        btnRetry.setOnClickListener {
            webViewManager.loadDefaultUrl()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!webViewManager.handleBackPressed()) {
                    // Show double tap to exit or direct confirmation
                    AlertDialog.Builder(this@MainActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Exit AgreyFlix")
                        .setMessage("Are you sure you want to exit the application?")
                        .setPositiveButton("Exit") { _, _ ->
                            finish()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        })
    }

    /**
     * Launch the dynamic system permissions flow.
     */
    private fun requestAppPermissionsOnLaunch() {
        permissionManager.requestAppPermissions { result ->
            val allGranted = result.values.all { it }
            if (allGranted) {
                // Perform post-grant optimizations if needed (e.g. read media info safely)
                val localVideos = mediaAccessManager.queryLocalVideos()
                System.out.println("Discovered ${localVideos.size} local video items under privacy-respecting protocol.")
            } else {
                Toast.makeText(this, "Some features may be limited without permissions", Toast.LENGTH_LONG).show()
            }
        }
    }
}
