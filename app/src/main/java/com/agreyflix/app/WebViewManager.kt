package com.agreyflix.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity

class WebViewManager(
    private val activity: AppCompatActivity,
    private val fileChooserLauncher: ActivityResultLauncher<Intent>
) {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineContainer: LinearLayout

    var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    /**
     * Initializes and configures the WebView.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(
        webView: WebView,
        progressBar: ProgressBar,
        offlineContainer: LinearLayout
    ) {
        this.webView = webView
        this.progressBar = progressBar
        this.offlineContainer = offlineContainer

        val settings = webView.settings

        // Enable necessary modern web engine configurations
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        
        // Security hardening
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW // HTTPS ONLY
        
        // Custom User Agent to detect Android Shell
        val currentUA = settings.userAgentString
        settings.userAgentString = "$currentUA ${activity.getString(R.string.user_agent_suffix)}"

        // Setup Cookie persistence
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // Setup custom Client handlers
        webView.webViewClient = AgreyFlixWebViewClient()
        webView.webChromeClient = AgreyFlixWebChromeClient()

        // Load the production web app
        loadDefaultUrl()
    }

    /**
     * Loads the default AgreyFlix landing URL.
     */
    fun loadDefaultUrl() {
        val targetUrl = activity.getString(R.string.app_url)
        webView.loadUrl(targetUrl)
    }

    /**
     * Handles back navigation requests.
     * @return true if the back action was consumed, false if the activity should exit.
     */
    fun handleBackPressed(): Boolean {
        return if (webView.canGoBack()) {
            webView.goBack()
            true
        } else {
            false
        }
    }

    /**
     * Handles results returned from the file selector launcher.
     */
    fun onFileChooserResult(resultCode: Int, data: Intent?) {
        if (resultCode == AppCompatActivity.RESULT_OK) {
            val results = when {
                data?.dataString != null -> arrayOf(Uri.parse(data.dataString))
                data?.clipData != null -> {
                    val clipData = data.clipData!!
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                }
                else -> null
            }
            fileUploadCallback?.onReceiveValue(results)
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    // Custom WebViewClient to control URL Loading & Deep Linking
    private inner class AgreyFlixWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url ?: return false
            val urlString = url.toString()

            // 1. Allow domestic domain loads inside WebView
            val domesticHosts = arrayOf("agrey-flix.vercel.app", "ais-dev-ciilnuwihlos6d567zw3f5")
            val isDomestic = domesticHosts.any { host -> url.host?.contains(host) == true }

            if (isDomestic) {
                return false // load inside
            }

            // 2. Handle deep linking for 1DM app, Play Store, and other third party intent Uri protocols
            try {
                if (urlString.startsWith("intent://") || urlString.startsWith("market://")) {
                    val intent = Intent.parseUri(urlString, Intent.URI_INTENT_SCHEME)
                    if (intent != null) {
                        val packageManager = activity.packageManager
                        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                        if (info != null) {
                            activity.startActivity(intent)
                        } else {
                            val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                            if (fallbackUrl != null) {
                                view?.loadUrl(fallbackUrl)
                            } else {
                                val packName = intent.`package` ?: "idm.internet.download.manager"
                                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packName")))
                            }
                        }
                        return true
                    }
                }

                // 3. Handle general system links (mailto, tel, whatsapp, etc.)
                if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
                    activity.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(activity, "App Link Error. Ensure recommended downloader 1DM is installed.", Toast.LENGTH_LONG).show()
                return true
            }

            // Standard external browser load for other random domains
            val intent = Intent(Intent.ACTION_VIEW, url)
            activity.startActivity(intent)
            return true
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            progressBar.visibility = View.VISIBLE
            offlineContainer.visibility = View.GONE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            progressBar.visibility = View.GONE
            CookieManager.getInstance().flush() // Flush cookie tokens safely
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            if (request?.isForMainFrame == true) {
                progressBar.visibility = View.GONE
                offlineContainer.visibility = View.VISIBLE
            }
        }
    }

    // Custom WebChromeClient to manage loading progression & file chooser
    private inner class AgreyFlixWebChromeClient : WebChromeClient() {
        
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            progressBar.progress = newProgress
            if (newProgress == 100) {
                progressBar.visibility = View.GONE
            } else {
                progressBar.visibility = View.VISIBLE
            }
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = filePathCallback

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                val mimetypes = arrayOf("image/*", "video/*", "audio/*")
                putExtra(Intent.EXTRA_MIME_TYPES, mimetypes)
            }

            try {
                fileChooserLauncher.launch(Intent.createChooser(intent, "Select Media Upload"))
            } catch (e: ActivityNotFoundException) {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = null
                Toast.makeText(activity, "No secure File Chooser apps found.", Toast.LENGTH_SHORT).show()
                return false
            }
            return true
        }
    }
}
