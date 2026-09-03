package com.ustc.timetable.school.ustc.auth

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import com.ustc.timetable.school.ustc.portal.CookieAwareFetcher
import com.ustc.timetable.school.ustc.portal.PortalDescriptor
import com.ustc.timetable.school.ustc.portal.PortalHttpClientFactory
import com.ustc.timetable.school.ustc.portal.UstcPortalDescriptor
import java.time.Clock
import java.time.Instant
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestWebViewLoginApplication::class, sdk = [27])
class WebViewLoginActivityTest {
    private lateinit var server: MockWebServer
    private lateinit var app: TestWebViewLoginApplication

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        app = RuntimeEnvironment.getApplication() as TestWebViewLoginApplication
        app.dependencies = null
    }

    @After fun tearDown() {
        app.dependencies = null
        server.close()
    }

    @Test fun unconfigured_activity_finishes_canceled() {
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        val shadow = shadowOf(activity)
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadow.resultCode)
    }

    @Test fun configured_activity_loads_login_url() {
        val descriptor = descriptor()
        app.dependencies = dependencies(descriptor)
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        val webView = findView(activity.window.decorView, WebView::class.java)!!
        assertEquals(descriptor.loginUrl, shadowOf(webView).lastLoadedUrl)
    }

    @Test fun authenticated_home_loads_stable_bootstrap_without_capturing_or_finishing() {
        var cookieReads = 0
        val descriptor = descriptor()
        app.dependencies = dependencies(
            descriptor,
            cookies = CookieRetriever { cookieReads++; null },
        )
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        val webView = findView(activity.window.decorView, WebView::class.java)!!

        finishPage(activity, portalHome())

        assertEquals(descriptor.probeUrl, shadowOf(webView).lastLoadedUrl)
        assertEquals(0, cookieReads)
        assertEquals(0, server.requestCount)
        assertFalse(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
    }

    @Test fun duplicate_home_finished_event_does_not_reload_stable_bootstrap() {
        val descriptor = descriptor()
        app.dependencies = dependencies(descriptor, cookies = CookieRetriever { null })
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        val webView = findView(activity.window.decorView, WebView::class.java)!!
        finishPage(activity, portalHome())
        assertEquals(descriptor.probeUrl, shadowOf(webView).lastLoadedUrl)

        val sentinel = "https://sentinel.invalid/unchanged"
        webView.loadUrl(sentinel)
        finishPage(activity, portalHome())

        assertEquals(sentinel, shadowOf(webView).lastLoadedUrl)
    }

    @Test fun one_intermediate_home_return_gets_one_bounded_retry() {
        val descriptor = descriptor()
        app.dependencies = dependencies(descriptor, cookies = CookieRetriever { null })
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        val webView = findView(activity.window.decorView, WebView::class.java)!!

        finishPage(activity, portalHome())
        startPage(activity, descriptor.probeUrl)
        startPage(activity, portalHome())
        finishPage(activity, portalHome())
        assertEquals(descriptor.probeUrl, shadowOf(webView).lastLoadedUrl)

        val sentinel = "https://sentinel.invalid/bound-reached"
        webView.loadUrl(sentinel)
        startPage(activity, descriptor.probeUrl)
        startPage(activity, portalHome())
        finishPage(activity, portalHome())
        assertEquals(sentinel, shadowOf(webView).lastLoadedUrl)
        assertFalse(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
    }

    @Test fun dynamic_student_selection_landing_finishes_result_ok() {
        server.enqueue(okPage("verified"))
        val descriptor = descriptor()
        app.dependencies = dependencies(descriptor)
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        finishPage(activity, dynamicLanding())
        awaitCondition { activity.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
    }

    @Test fun login_ticket_and_external_routes_do_not_capture_or_finish() {
        var cookieReads = 0
        val descriptor = descriptor()
        val ignoredUrls = listOf(
            "${descriptor.loginUrl}?refer=%2Ffixture",
            server.url("/ucas-sso/login?ticket=%3Credacted%3E").toString(),
            server.url("/home?next=1").toString(),
            server.url("/home#section").toString(),
            "https://evil.example/done",
        )
        app.dependencies = dependencies(
            descriptor,
            cookies = CookieRetriever { cookieReads++; null },
        )
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()

        ignoredUrls.forEach { finishPage(activity, it) }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, cookieReads)
        assertEquals(0, server.requestCount)
        assertFalse(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
    }

    @Test fun arbitrary_same_host_page_does_not_capture_or_finish() {
        var cookieReads = 0
        val descriptor = descriptor()
        app.dependencies = dependencies(
            descriptor,
            cookies = CookieRetriever { cookieReads++; null },
        )
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()

        finishPage(activity, server.url("/dashboard").toString())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, cookieReads)
        assertEquals(0, server.requestCount)
        assertFalse(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
    }

    @Test fun invalid_dynamic_id_does_not_capture_or_finish() {
        var cookieReads = 0
        val descriptor = descriptor()
        app.dependencies = dependencies(
            descriptor,
            cookies = CookieRetriever { cookieReads++; null },
        )
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()

        listOf("0", "-1", "not-an-id").forEach { finishPage(activity, dynamicLanding(it)) }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, cookieReads)
        assertEquals(0, server.requestCount)
        assertFalse(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
    }

    @Test fun successful_dynamic_landing_capture_runs_only_once() {
        server.enqueue(okPage("verified"))
        val descriptor = descriptor()
        app.dependencies = dependencies(descriptor)
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        finishPage(activity, dynamicLanding())
        awaitCondition { activity.isFinishing }
        finishPage(activity, dynamicLanding("202"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, server.requestCount)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
    }

    @Test fun missing_session_cookie_keeps_activity_incomplete_and_canceled() {
        val descriptor = descriptor()
        app.dependencies = dependencies(descriptor, cookies = CookieRetriever { null })
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        finishPage(activity, dynamicLanding())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, server.requestCount)
        assertFalse(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
    }

    @Test fun three_auto_failures_show_fallback() {
        repeat(3) { server.enqueue(okPage("login")) }
        var attempts = 0
        val descriptor = descriptor()
        app.dependencies = dependencies(descriptor, LoginPageDetector { _, _ -> attempts++; true })
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        repeat(3) {
            finishPage(activity, dynamicLanding())
            awaitCondition { attempts == it + 1 }
        }
        awaitCondition { findFallback(activity)?.visibility == View.VISIBLE }
        assertFalse(activity.isFinishing)
    }

    @Test fun manual_fallback_success_finishes_result_ok() {
        repeat(3) { server.enqueue(okPage("login")) }
        server.enqueue(okPage("verified"))
        var attempts = 0
        val detector = LoginPageDetector { _, _ -> ++attempts <= 3 }
        val descriptor = descriptor()
        app.dependencies = dependencies(descriptor, detector)
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        repeat(3) {
            finishPage(activity, dynamicLanding())
            awaitCondition { attempts == it + 1 }
        }
        val fallback = findFallback(activity)
        assertNotNull(fallback)
        fallback!!.performClick()
        awaitCondition { activity.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
    }

    @Test fun manual_fallback_cannot_bypass_dynamic_landing_gate() {
        repeat(3) { server.enqueue(okPage("login")) }
        server.enqueue(okPage("verified"))
        var attempts = 0
        val descriptor = descriptor()
        app.dependencies = dependencies(
            descriptor,
            LoginPageDetector { _, _ -> ++attempts <= 3 },
        )
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        repeat(3) {
            finishPage(activity, dynamicLanding())
            awaitCondition { attempts == it + 1 }
        }
        val fallback = findFallback(activity)!!
        finishPage(activity, "https://evil.example/done")

        fallback.performClick()
        settleAsyncWork()

        assertEquals(3, server.requestCount)
        assertFalse(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
    }

    @Test fun disallowed_navigation_start_immediately_revokes_manual_fallback_eligibility() {
        var probeCookieReads = 0
        val descriptor = descriptor()
        app.dependencies = dependencies(
            descriptor,
            cookies = CookieRetriever { url ->
                if (url == descriptor.probeUrl) probeCookieReads++
                null
            },
        )
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        repeat(3) {
            finishPage(activity, dynamicLanding())
            awaitCondition { probeCookieReads == it + 1 }
        }
        val fallback = findFallback(activity)!!
        val disallowedUrls = listOf(
            "https://evil.example/done",
            descriptor.loginUrl,
            server.url("/ucas-sso/login?ticket=%3Credacted%3E").toString(),
            server.url("/dashboard").toString(),
        )

        disallowedUrls.forEach { url ->
            finishPage(activity, dynamicLanding())
            val readsBeforeNavigation = probeCookieReads
            startPage(activity, url)
            fallback.performClick()
            settleAsyncWork()

            assertEquals(readsBeforeNavigation, probeCookieReads)
            assertFalse(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        }
    }

    @Test fun result_ok_parses_true() {
        assertTrue(WebViewLoginContract().parseResult(Activity.RESULT_OK, null))
    }

    @Test fun canceled_parses_false() {
        assertFalse(WebViewLoginContract().parseResult(Activity.RESULT_CANCELED, null))
    }

    @Test fun intent_targets_webview_login_activity() {
        val context = RuntimeEnvironment.getApplication() as Application
        val intent = WebViewLoginContract().createIntent(context, Unit)
        assertEquals(WebViewLoginActivity::class.java.name, intent.component?.className)
    }

    @Test fun webview_disables_file_content_and_mixed_content_access() {
        val descriptor = descriptor()
        app.dependencies = dependencies(descriptor)
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        val webView = findView(activity.window.decorView, WebView::class.java)!!
        assertFalse(webView.settings.allowFileAccess)
        assertFalse(webView.settings.allowContentAccess)
        assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW, webView.settings.mixedContentMode)
    }

    @Test fun webview_enables_dom_storage_for_official_cas() {
        val descriptor = descriptor()
        app.dependencies = dependencies(descriptor)
        val activity = Robolectric.buildActivity(WebViewLoginActivity::class.java).setup().get()
        val webView = findView(activity.window.decorView, WebView::class.java)!!
        assertTrue(webView.settings.domStorageEnabled)
    }

    private fun dependencies(
        descriptor: PortalDescriptor,
        detector: LoginPageDetector = LoginPageDetector { _, _ -> false },
        cookies: CookieRetriever = CookieRetriever { url ->
            if (url == descriptor.probeUrl) "SESSION=fixture" else null
        },
    ): WebViewLoginDependencies {
        val storage = object : SessionStorage {
            private var bytes: ByteArray? = null
            override fun read(): ByteArray? = bytes
            override fun write(data: ByteArray?) { bytes = data }
        }
        val key = object : SecretKeyProvider {
            override fun getOrCreateKey(): SecretKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        }
        return WebViewLoginDependencies(
            descriptor,
            UstcSessionManager(
                descriptor,
                SessionStore(key, storage),
                cookies,
                CookieAwareFetcher(PortalHttpClientFactory.create()),
                detector,
                Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC),
            ),
        )
    }

    private fun descriptor(): UstcPortalDescriptor = UstcPortalDescriptor(server.url("/").toString())

    private fun portalHome(): String = server.url("/home").toString()

    private fun dynamicLanding(id: String = "101"): String =
        server.url("/for-std/course-select/turns/$id").toString()

    private fun startPage(activity: WebViewLoginActivity, url: String) {
        val webView = findView(activity.window.decorView, WebView::class.java)!!
        webView.webViewClient.onPageStarted(webView, url, null)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun finishPage(activity: WebViewLoginActivity, url: String) {
        val webView = findView(activity.window.decorView, WebView::class.java)!!
        webView.webViewClient.onPageFinished(webView, url)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun findFallback(activity: Activity): Button? =
        findViews(activity.window.decorView, Button::class.java).firstOrNull { it.text.toString() == "我已完成登录" }

    private fun <T : View> findView(root: View, type: Class<T>): T? = findViews(root, type).firstOrNull()

    private fun <T : View> findViews(root: View, type: Class<T>): List<T> {
        val result = mutableListOf<T>()
        fun visit(view: View) {
            if (type.isInstance(view)) result += type.cast(view)
            if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
        }
        visit(root)
        return result
    }

    private fun awaitCondition(condition: () -> Boolean) {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition was not reached")
    }

    private fun settleAsyncWork() {
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun okPage(body: String) = MockResponse.Builder().code(200).body(body).build()
}

class TestWebViewLoginApplication : Application(), WebViewLoginDependenciesProvider {
    var dependencies: WebViewLoginDependencies? = null
    override fun webViewLoginDependencies(): WebViewLoginDependencies? = dependencies
}
