package com.ballade.hwaran.frontend.intro

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IntroScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateToHome: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webViewRef?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    clearHistory()
                    removeAllViews()
                    destroy()
                }
                webViewRef = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false

                    // JavaScript interface to communicate from WebView to Compose
                    addJavascriptInterface(object : Any() {
                        @JavascriptInterface
                        fun completeIntro() {
                            post {
                                settingsViewModel.setHasSeenIntro(true)
                                onNavigateToHome()
                            }
                        }
                    }, "Android")

                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()

                    loadUrl("file:///android_asset/intro_app.html")
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
