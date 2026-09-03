package com.ustc.timetable.school.ustc.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.lifecycleScope
import com.ustc.timetable.school.ustc.portal.PortalDescriptor
import com.ustc.timetable.school.ustc.portal.PortalDescriptorRules
import kotlinx.coroutines.launch

data class WebViewLoginDependencies(
    val descriptor: PortalDescriptor,
    val sessionManager: UstcSessionManager,
)

interface WebViewLoginDependenciesProvider {
    fun webViewLoginDependencies(): WebViewLoginDependencies?
}

class WebViewLoginActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var fallbackButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val dependencies = (application as? WebViewLoginDependenciesProvider)
            ?.webViewLoginDependencies()
        if (dependencies == null || !isValid(dependencies.descriptor)) {
            finish()
            return
        }

        val coordinator = LoginCompletionCoordinator(dependencies.descriptor) {
            dependencies.sessionManager.captureAndVerify()
        }
        val bootstrapGate = ModuleBootstrapGate(dependencies.descriptor)
        val fallback = Button(this).apply {
            text = "我已完成登录"
            visibility = View.GONE
            setOnClickListener {
                lifecycleScope.launch {
                    handleResult(coordinator.onManualProbe(), coordinator)
                }
            }
        }
        fallbackButton = fallback

        val browser = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !isHttp(request.url)

                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    !isHttp(Uri.parse(url))

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    coordinator.onPageStarted()
                    bootstrapGate.onPageStarted()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (bootstrapGate.shouldBootstrap(url)) {
                        view.loadUrl(dependencies.descriptor.probeUrl)
                    }
                    lifecycleScope.launch {
                        handleResult(coordinator.onPageFinished(url), coordinator)
                    }
                }
            }
        }
        webView = browser

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(browser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(fallback, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
        )
        browser.loadUrl(dependencies.descriptor.loginUrl)
    }

    private fun handleResult(
        result: LoginCompletionResult,
        coordinator: LoginCompletionCoordinator,
    ) {
        when (result) {
            LoginCompletionResult.Verified -> {
                setResult(RESULT_OK)
                finish()
            }
            LoginCompletionResult.Failed,
            LoginCompletionResult.Ignored,
            -> fallbackButton?.visibility = if (coordinator.isFallbackVisible) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            webViewClient = WebViewClient()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun isValid(descriptor: PortalDescriptor): Boolean = try {
        PortalDescriptorRules.validate(descriptor)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun isHttp(uri: Uri): Boolean =
        uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)

    private class ModuleBootstrapGate(
        private val descriptor: PortalDescriptor,
    ) {
        private var navigationGeneration = 0
        private var handledHomeGeneration = -1
        private var attempts = 0

        fun onPageStarted() {
            navigationGeneration++
        }

        fun shouldBootstrap(rawUrl: String): Boolean {
            if (!PortalDescriptorRules.isAutomaticModuleBootstrapNavigation(descriptor, rawUrl)) {
                return false
            }
            if (handledHomeGeneration == navigationGeneration) return false
            handledHomeGeneration = navigationGeneration
            if (attempts >= MAX_ATTEMPTS) return false
            attempts++
            return true
        }

        private companion object {
            const val MAX_ATTEMPTS = 2
        }
    }
}

class WebViewLoginContract : ActivityResultContract<Unit, Boolean>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, WebViewLoginActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}
