package com.ballade.hwaran

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import androidx.core.animation.doOnEnd
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    private val isVideoReady = mutableStateOf(false)

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val fadeOut = android.animation.ObjectAnimator.ofFloat(
                splashScreenView.view,
                android.view.View.ALPHA,
                1f,
                0f
            )
            fadeOut.duration = 400L
            fadeOut.interpolator = android.view.animation.AccelerateInterpolator()
            fadeOut.doOnEnd { splashScreenView.remove() }
            fadeOut.start()
        }

        super.onCreate(savedInstanceState)

        // Full screen / Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContent {
            val videoAlpha = remember { Animatable(1f) }
            val ready by remember { isVideoReady }

            LaunchedEffect(ready) {
                if (ready) {
                    delay(7500L)

                    val intent = Intent(this@SplashActivity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    startActivity(intent)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }

                    videoAlpha.animateTo(0f, tween(800))
                    delay(1500)
                    finish()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = "Hwaran Launch Icon",
                    modifier = Modifier.size(160.dp)
                )

                AndroidView(
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            webChromeClient = android.webkit.WebChromeClient()
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageFinished(
                                    view: android.webkit.WebView?,
                                    url: String?
                                ) {
                                    isVideoReady.value = true
                                }
                            }
                            loadUrl("file:///android_asset/3d_intro.html")
                            isVideoReady.value = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = videoAlpha.value }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
