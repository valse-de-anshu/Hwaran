package com.ballade.hwaran.ui.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DevilJellyBall(
    modifier: Modifier = Modifier,
    isHappy: Boolean = false
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(isHappy) {
        webViewRef?.evaluateJavascript("if(window.setHappy) window.setHappy(${isHappy});", null)
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                object : WebView(ctx) {
                    override fun onTouchEvent(event: MotionEvent?): Boolean {
                        // Pass through all touch events directly to Compose parent
                        return false
                    }

                    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
                        // Never consume touch events in WebView
                        return false
                    }
                }.apply {
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(0) // Transparent background
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    isClickable = false
                    isFocusable = false
                    isLongClickable = false
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript("if(window.setHappy) window.setHappy(${isHappy});", null)
                        }
                    }
                    loadUrl("file:///android_asset/devil_jelly.html")
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
