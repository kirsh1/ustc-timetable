package com.ustc.timetable.school.ustc.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
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
import android.widget.FrameLayout
import android.widget.ImageButton
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.lifecycleScope
import com.ustc.timetable.school.ustc.portal.PortalDescriptor
import com.ustc.timetable.school.ustc.portal.PortalDescriptorRules
import com.ustc.timetable.school.ustc.portal.UstcCurrentTurnDiscovery
import com.ustc.timetable.school.ustc.portal.UstcPortalDescriptor
import kotlinx.coroutines.launch

data class WebViewLoginDependencies(
    val descriptor: UstcPortalDescriptor,
    val sessionManager: UstcSessionManager,
)

interface WebViewLoginDependenciesProvider {
    fun webViewLoginDependencies(): WebViewLoginDependencies?
}

class WebViewLoginActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var fallbackButton: Button? = null
    private var displayMode = WebViewDisplayMode.MOBILE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val dependencies = (application as? WebViewLoginDependenciesProvider)
            ?.webViewLoginDependencies()
        if (dependencies == null || !isValid(dependencies.descriptor)) {
            finish()
            return
        }

        val coordinator = LoginCompletionCoordinator(dependencies.descriptor) { completionUrl ->
            dependencies.sessionManager.captureAndVerify(completionUrl)
        }
        val bootstrapGate = ModuleBootstrapGate(dependencies.descriptor)
        val currentTurnDiscovery = UstcCurrentTurnDiscovery(dependencies.descriptor)
        val domNavigationGate = DomNavigationGate()
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
                    domNavigationGate.onPageStarted()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (bootstrapGate.shouldBootstrap(url)) {
                        view.loadUrl(dependencies.descriptor.probeUrl)
                    }
                    if (dependencies.descriptor.isCurrentTurnLandingUrl(url)) {
                        val generation = domNavigationGate.currentGeneration
                        view.evaluateJavascript(currentTurnDiscovery.webViewLinkDiscoveryScript) { result ->
                            if (!domNavigationGate.isCurrent(generation)) return@evaluateJavascript
                            currentTurnDiscovery.selectionUrlFromWebViewResult(result)?.let(view::loadUrl)
                        }
                        return
                    }
                    lifecycleScope.launch {
                        handleResult(coordinator.onPageFinished(url), coordinator)
                    }
                }
            }
        }
        webView = browser
        val capturedMobile = WebViewSettingsSnapshot(
            browser.settings.userAgentString.orEmpty(),
            browser.settings.useWideViewPort,
            browser.settings.loadWithOverviewMode,
        )
        val modeButton = ImageButton(this).apply {
            setImageDrawable(DisplayModeDrawable())
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "切换到桌面版"
            setOnClickListener {
                displayMode = if (displayMode == WebViewDisplayMode.MOBILE) WebViewDisplayMode.DESKTOP else WebViewDisplayMode.MOBILE
                val projection = webViewSettingsFor(displayMode, capturedMobile)
                browser.settings.userAgentString = projection.userAgent
                browser.settings.useWideViewPort = projection.useWideViewPort
                browser.settings.loadWithOverviewMode = projection.loadWithOverviewMode
                contentDescription = if (displayMode == WebViewDisplayMode.MOBILE) "切换到桌面版" else "切换到手机版"
                browser.url?.takeIf(::isHttpUrl)?.let(browser::loadUrl)
            }
        }
        val topControl = FrameLayout(this).apply {
            addView(modeButton, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.CENTER_VERTICAL))
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(topControl, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
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

    private fun isHttpUrl(url: String): Boolean = isHttp(Uri.parse(url))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class DisplayModeDrawable : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        override fun draw(canvas: Canvas) {
            val b = bounds
            val insetX = b.width() * .2f
            val top = b.height() * .25f
            val bottom = b.height() * .68f
            canvas.drawRoundRect(insetX, top, b.width() - insetX, bottom, 3f, 3f, paint)
            canvas.drawLine(b.width() * .5f, bottom, b.width() * .5f, b.height() * .78f, paint)
            canvas.drawLine(b.width() * .35f, b.height() * .78f, b.width() * .65f, b.height() * .78f, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

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

    private class DomNavigationGate {
        var currentGeneration: Int = 0
            private set

        fun onPageStarted() {
            currentGeneration++
        }

        fun isCurrent(generation: Int): Boolean = generation == currentGeneration
    }
}

class WebViewLoginContract : ActivityResultContract<Unit, Boolean>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, WebViewLoginActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}
