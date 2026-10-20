package com.floating.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout

class FloatingBrowserService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var container: LinearLayout
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl("https://www.google.com")
        }
        val urlBar = EditText(this).apply {
            hint = "Digite a URL..."
            setOnEditorActionListener { _, _, _ ->
                val url = text.toString()
                webView.loadUrl(if (url.startsWith("http")) url else "https://$url")
                clearFocus()
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(container, params)
                true
            }
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF222222.toInt())
            addView(urlBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.7f).toInt(),
            (resources.displayMetrics.heightPixels * 0.6f).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
        windowManager.addView(container, params)
        setupDrag(container)
    }

    private fun setupDrag(view: View) {
        var initialX = 0; var initialY = 0
        var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { initialX = params.x; initialY = params.y; touchX = event.rawX; touchY = event.rawY }
                MotionEvent.ACTION_MOVE -> { params.x = initialX + (event.rawX - touchX).toInt(); params.y = initialY + (event.rawY - touchY).toInt(); windowManager.updateViewLayout(view, params) }
            }
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("floating_browser", "Navegador Flutuante", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, "floating_browser").setContentTitle("Navegador Flutuante").setContentText("O navegador está ativo").build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { windowManager.removeView(container) } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
