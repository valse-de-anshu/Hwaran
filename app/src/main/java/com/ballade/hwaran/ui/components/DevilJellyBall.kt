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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ballade.hwaran.ui.theme.LocalBatterySaving

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DevilJellyBall(
    modifier: Modifier = Modifier,
    isHappy: Boolean = false
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val batterySaving = LocalBatterySaving.current
    val shouldBeActive = !batterySaving && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(shouldBeActive) {
        if (shouldBeActive) {
            webViewRef?.onResume()
        } else {
            webViewRef?.onPause()
        }
    }

    LaunchedEffect(isHappy, shouldBeActive) {
        if (shouldBeActive) {
            webViewRef?.evaluateJavascript("if(window.setHappy) window.setHappy(${isHappy});", null)
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            webViewRef?.onPause()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                object : WebView(ctx) {
                    override fun onTouchEvent(event: MotionEvent?): Boolean = false
                    override fun dispatchTouchEvent(event: MotionEvent?): Boolean = false
                    override fun isOpaque(): Boolean = false
                    override fun onWindowVisibilityChanged(visibility: Int) {
                        super.onWindowVisibilityChanged(visibility)
                        if (visibility == android.view.View.VISIBLE && shouldBeActive) {
                            onResume()
                        } else {
                            onPause()
                        }
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
                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    isClickable = false
                    isFocusable = false
                    isLongClickable = false
                    overScrollMode = android.view.View.OVER_SCROLL_NEVER
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (shouldBeActive) {
                                view?.evaluateJavascript("if(window.setHappy) window.setHappy(${isHappy});", null)
                            }
                        }
                    }
                    loadUrl("file:///android_asset/devil_jelly.html")
                    webViewRef = this
                }
            },
            update = { view ->
                if (shouldBeActive) {
                    view.onResume()
                } else {
                    view.onPause()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
