package com.soltini.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * A WebView that never takes touch events, so the LazyColumn around it keeps scrolling smoothly.
 */
@SuppressLint("ViewConstructor")
private class PassThroughWebView(context: Context) : WebView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

/**
 * The permanent main-screen orb: "Curl Noise Orb" (self-contained WebGL, loads from app assets, works offline).
 *
 * The colour is fixed (violet → pink → blue iridescent gradient) and never changes.
 * [level] (0..1, mic or speaker amplitude) only makes the orb flow a little faster and swell slightly.
 * Rendering pauses automatically when the app is in the background or the screen is left, so it costs no battery.
 *
 * NOTE: we deliberately do NOT call WebView.pauseTimers() — that is process-wide and would also freeze the
 * in-app browser's background music playback.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CurlNoiseOrb(
    level: Float,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PassThroughWebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = false
                isFocusableInTouchMode = false
                settings.apply {
                    javaScriptEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowFileAccess = true
                    allowContentAccess = false
                    builtInZoomControls = false
                    displayZoomControls = false
                    mediaPlaybackRequiresUserGesture = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageReady = true
                    }
                }
                loadUrl("file:///android_asset/orb/curl_noise_orb.html")
                webView = this
            }
        }
    )

    // Pause drawing when the app isn't visible; resume when it comes back.
    DisposableEffect(lifecycleOwner, webView, pageReady) {
        val wv = webView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> wv?.evaluateJavascript("window.orbSetRunning && window.orbSetRunning(true)", null)
                Lifecycle.Event.ON_PAUSE -> wv?.evaluateJavascript("window.orbSetRunning && window.orbSetRunning(false)", null)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Free the WebView when the screen leaves composition.
    DisposableEffect(Unit) {
        onDispose {
            webView?.let {
                it.stopLoading()
                it.loadUrl("about:blank")
                it.removeAllViews()
                it.destroy()
            }
            webView = null
        }
    }

    // Feed the live voice amplitude (quantised so we don't flood the JS bridge).
    val quantised = (level.coerceIn(0f, 1f) * 20f).toInt() / 20f
    LaunchedEffect(quantised, pageReady, webView) {
        if (pageReady) {
            webView?.evaluateJavascript("window.orbSetLevel && window.orbSetLevel($quantised)", null)
        }
    }
}
