package com.floating.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class FloatingBrowserService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var expandedView: LinearLayout
    private lateinit var bubbleView: ImageView
    private lateinit var webView: WebView
    private var isBubbleMode = false

    private lateinit var expandedParams: WindowManager.LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "floating_browser_channel")
                .setContentTitle("Floating Browser ativo")
                .setContentText("Toque na bolinha para abrir")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            Notification.Builder(this).setContentTitle("Floating Browser").build()
        }
        startForeground(1, notification)

        if (!::expandedView.isInitialized) {
            createExpandedView()
            createBubbleView()
            showExpanded()
        }
        return START_STICKY
    }

    private fun createExpandedView() {
        expandedParams = WindowManager.LayoutParams(
            800, 1100,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 100; y = 200 }

        expandedView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF2B2B2B.toInt())
        }

        // Barra de topo com minimizar e fechar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1A1A1A.toInt())
            setPadding(10,10,10,10)
        }

        val btnMinimize = TextView(this).apply {
            text = " — "
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(30,10,30,10)
            setOnClickListener { minimize() }
        }

        val title = TextView(this).apply {
            text = " Browser"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(this).apply {
            text = " X "
            textSize = 20f
            setTextColor(0xFFFF5555.toInt())
            setPadding(30,10,30,10)
            setOnClickListener { stopSelf() }
        }

        topBar.addView(btnMinimize)
        topBar.addView(title)
        topBar.addView(btnClose)

        val urlBar = EditText(this).apply {
            hint = "Digite a URL"
            setText("https://google.com")
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFAAAAAA.toInt())
            setBackgroundColor(0xFF3A3A3A.toInt())
            setOnEditorActionListener { _, _, _ ->
                var url = text.toString()
                if (!url.startsWith("http")) url = "https://$url"
                webView.loadUrl(url)
                true
            }
        }

        webView = WebView(this).apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            loadUrl("https://google.com")
        }

        expandedView.addView(topBar, LinearLayout.LayoutParams.MATCH_PARENT, 110)
        expandedView.addView(urlBar, LinearLayout.LayoutParams.MATCH_PARENT, 120)
        expandedView.addView(webView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)

        // Arrastar pela topBar
        makeDraggable(topBar, expandedParams)
    }

    private fun createBubbleView() {
        bubbleParams = WindowManager.LayoutParams(
            150, 150, // BEM MENOR QUE ICONE DA CALCULADORA
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 50; y = 300 }

        bubbleView = ImageView(this).apply {
            // Bolinha redonda azul com icone
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF2196F3.toInt())
                setStroke(4, 0xFFFFFFFF.toInt())
            }
            background = drawable
            setImageResource(android.R.drawable.ic_search_category_default)
            setPadding(30,30,30,30)
            elevation = 20f
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoving = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isMoving = true
                    bubbleParams.x = initialX + dx
                    bubbleParams.y = initialY + dy
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) showExpanded()
                    true
                }
                else -> false
            }
        }
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(expandedView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun minimize() {
        try { windowManager.removeView(expandedView) } catch (e: Exception) {}
        if (!isBubbleMode) {
            try { windowManager.addView(bubbleView, bubbleParams) } catch (e: Exception) {}
            isBubbleMode = true
        }
    }

    private fun showExpanded() {
        try { windowManager.removeView(bubbleView) } catch (e: Exception) {}
        try {
            if (!expandedView.isAttachedToWindow) windowManager.addView(expandedView, expandedParams)
        } catch (e: Exception) {
            windowManager.addView(expandedView, expandedParams)
        }
        isBubbleMode = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("floating_browser_channel", "Floating Browser", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { windowManager.removeView(expandedView) } catch (e: Exception) {}
        try { windowManager.removeView(bubbleView) } catch (e: Exception) {}
    }
}