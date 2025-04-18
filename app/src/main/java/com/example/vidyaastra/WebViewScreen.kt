package com.example.vidyaastra

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.*
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.Composable
import android.webkit.WebView
import androidx.core.net.toUri

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    navController: NavController,
    incomingIntent: Intent?,
    isDashboard: Boolean = false
) {
    val context = LocalContext.current
    val websiteUrl = "https://vidyastraa-jeeneet.vercel.app/"
    val appRedirectScheme = "com.example.vidyastraa_app"
    var webView: WebView? by remember { mutableStateOf(null) }

    AndroidView(factory = {
        WebView(context).apply {
            webView = this
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
                setCookie(websiteUrl, "SameSite=None; Secure")
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    return when {
                        url.startsWith("https://accounts.google.com/") || url.contains("/auth/signin") -> {
                            openCustomTab(context, url)
                            true
                        }
                        url.contains("/auth/mobile-callback") -> {
                            view.loadUrl(url)
                            true
                        }
                        else -> false
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()

                    if (url.startsWith(websiteUrl) && !url.contains("/auth/")) {
                        verifySession(view)
                    }
                }
            }

            loadUrl(if (isDashboard) "$websiteUrl/student/dashboard" else websiteUrl)

            // Handle deep link intent
            incomingIntent?.data?.let { uri ->
                if (uri.toString().startsWith("$appRedirectScheme://auth")) {
                    val token = uri.getQueryParameter("token")
                    if (token != null) {
                        injectToken(token)
                        navController.navigate("dashboard") {
                            popUpTo("webview") { inclusive = true }
                        }
                    }
                }
            }
        }
    }, update = {
        // Optional: reload dashboard when resumed
        it.evaluateJavascript(
            "if (window.location.pathname === '/student/dashboard') { window.location.reload(); }",
            null
        )
    })
}

// ---------- Utilities ----------

private fun openCustomTab(context: Context, url: String) {
    try {
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        customTabsIntent.launchUrl(context, url.toUri())
    } catch (e: Exception) {
        Log.e("CustomTab", "Fallback to WebView", e)
        (context as? android.app.Activity)?.findViewById<WebView?>(android.R.id.content)?.loadUrl(url)
    }
}

private fun verifySession(view: WebView) {
    val js = """
        (function() {
            const token = localStorage.getItem('next-auth.session-token') || 
                document.cookie.match('(^|;)\\s*__Secure-next-auth.session-token\\s*=\\s*([^;]+)')?.[2];
            if (!token) {
                window.location.href = '/auth/signin';
            }
        })();
    """.trimIndent()
    view.evaluateJavascript(js, null)
}

private fun WebView.injectToken(token: String) {
    val js = """
        (function() {
            try {
                const tokenData = JSON.parse(decodeURIComponent('$token'));
                localStorage.setItem('next-auth.session-token', JSON.stringify(tokenData));
                document.cookie = '__Secure-next-auth.session-token=' + 
                    encodeURIComponent('$token') + 
                    '; path=/; SameSite=None; Secure';
                window.location.href = '/student/dashboard';
            } catch(e) {
                console.error('Token injection failed:', e);
                window.location.href = '/auth/signin';
            }
        })();
    """.trimIndent()
    evaluateJavascript(js, null)
}