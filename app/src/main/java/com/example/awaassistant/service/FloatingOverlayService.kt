package com.example.awaassistant.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import android.util.Log

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null

    companion object {
        private const val TAG = "FloatingOverlayService"
        
        @Volatile
        var instance: FloatingOverlayService? = null
            private set
        
        var isRunning = false
            private set

        // P0: 双击时间阈值
        private const val DOUBLE_TAP_THRESHOLD = 300L // ms
        // P0: 长按时间阈值
        private const val LONG_PRESS_THRESHOLD = 500L // ms

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "请先授权悬浮窗权限", Toast.LENGTH_SHORT).show()
                return
            }
            context.startService(Intent(context, FloatingOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingBubble()
    }

    fun reloadConfig() {
        Handler(Looper.getMainLooper()).post {
            floatView?.let {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to remove floatView on reload", e)
                }
                floatView = null
            }
            if (com.example.awaassistant.data.SettingsManager.isFloatingBallEnabled(this)) {
                createFloatingBubble()
            }
        }
    }

    private fun createFloatingBubble() {
        if (!com.example.awaassistant.data.SettingsManager.isFloatingBallEnabled(this)) {
            return
        }
        val size = dpToPx(56)
        
        // 构造悬浮球容器
        val container = FrameLayout(this)
        
        // 绘制一个渐变背景的圆形悬浮球
        val bubble = ImageView(this).apply {
            val drawable = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#4A00E0"), Color.parseColor("#8E2DE2"))
            )
            drawable.shape = GradientDrawable.OVAL
            drawable.setStroke(dpToPx(2), Color.WHITE)
            background = drawable
            
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageResource(android.R.drawable.ic_menu_help)
            setColorFilter(Color.WHITE)
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }

        container.addView(bubble, FrameLayout.LayoutParams(size, size))

        // 配置 WindowManager LayoutParams
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = windowManager.defaultDisplay.width - size - dpToPx(16)
            y = windowManager.defaultDisplay.height / 2
        }

        // P0: 手势状态追踪
        var lastTapTime = 0L
        var downTime = 0L
        var isDragging = false
        var isLongPressed = false

        // 设置触摸与拖拽事件
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private val touchSlop = ViewConfiguration.get(this@FloatingOverlayService).scaledTouchSlop

            // 长按检测 Runnable
            private val longPressRunnable = Runnable {
                isLongPressed = true
                // 长按时触发截图
                onBubbleLongPressed()
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        isLongPressed = false
                        downTime = System.currentTimeMillis()
                        
                        // 启动长按检测
                        Handler(Looper.getMainLooper()).postDelayed(longPressRunnable, LONG_PRESS_THRESHOLD)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        
                        if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            isDragging = true
                            // 取消长按检测
                            Handler(Looper.getMainLooper()).removeCallbacks(longPressRunnable)
                        }
                        
                        if (isDragging) {
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            windowManager.updateViewLayout(container, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 取消长按检测
                        Handler(Looper.getMainLooper()).removeCallbacks(longPressRunnable)
                        
                        val now = System.currentTimeMillis()
                        val pressDuration = now - downTime
                        
                        if (!isDragging && !isLongPressed) {
                            // 判断是双击还是单击
                            if (now - lastTapTime < DOUBLE_TAP_THRESHOLD) {
                                // 双击：弹出 Quick Capture Sheet
                                onBubbleDoubleTapped()
                                lastTapTime = 0
                            } else {
                                lastTapTime = now
                                // 单击：延迟处理，等待双击检测
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (lastTapTime != 0L) {
                                        // 没有检测到双击，执行单击操作（截图）
                                        onBubbleClicked()
                                        lastTapTime = 0
                                    }
                                }, DOUBLE_TAP_THRESHOLD)
                            }
                        } else {
                            // 拖拽结束：自动吸附到屏幕边缘
                            val screenWidth = windowManager.defaultDisplay.width
                            val bubbleCenter = params.x + size / 2
                            if (bubbleCenter < screenWidth / 2) {
                                params.x = dpToPx(8)
                            } else {
                                params.x = screenWidth - size - dpToPx(8)
                            }
                            windowManager.updateViewLayout(container, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        Handler(Looper.getMainLooper()).removeCallbacks(longPressRunnable)
                        return true
                    }
                }
                return false
            }
        })

        floatView = container
        windowManager.addView(container, params)
    }

    // P0: 单击 - 触发截图
    private fun onBubbleClicked() {
        val accessibilityService = AwaAccessibilityService.instance
        if (accessibilityService != null) {
            accessibilityService.triggerScreenCapture()
        } else {
            Toast.makeText(this, "Awa 助手辅助功能未启用，请先开启", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
    }

    // P0: 双击 - 弹出极速录入
    private fun onBubbleDoubleTapped() {
        // 触发极速录入 BottomSheet
        try {
            val intent = Intent(this, com.example.awaassistant.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                action = "com.example.awaassistant.SHOW_QUICK_CAPTURE"
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Quick Capture", e)
            Toast.makeText(this, "无法打开录入界面", Toast.LENGTH_SHORT).show()
        }
    }

    // P0: 长按 - 触发截图（保持原有行为）
    private fun onBubbleLongPressed() {
        onBubbleClicked()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    fun hideFloatingBubble() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            floatView?.visibility = View.GONE
        } else {
            Handler(Looper.getMainLooper()).post {
                floatView?.visibility = View.GONE
            }
        }
    }

    fun showFloatingBubble() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            floatView?.visibility = View.VISIBLE
        } else {
            Handler(Looper.getMainLooper()).post {
                floatView?.visibility = View.VISIBLE
            }
        }
    }

    fun triggerScreenFlash() {
        Handler(Looper.getMainLooper()).post {
            val flashView = RainbowBorderView(this)
            flashView.alpha = 0f
            
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            try {
                windowManager.addView(flashView, params)
                
                flashView.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .withEndAction {
                        Handler(Looper.getMainLooper()).postDelayed({
                            flashView.animate()
                                .alpha(0f)
                                .setDuration(400)
                                .withEndAction {
                                    try {
                                        windowManager.removeView(flashView)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to remove flash view", e)
                                    }
                                }
                                .start()
                        }, 1200)
                    }
                    .start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add flash view", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        floatView?.let {
            windowManager.removeView(it)
            floatView = null
        }
    }
}

class RainbowBorderView(context: Context) : View(context) {
    private val paintInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6 * resources.displayMetrics.density
    }
    
    private val paintOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16 * resources.displayMetrics.density
        alpha = 76
    }
    
    private var shader: android.graphics.SweepGradient? = null
    private val shaderMatrix = android.graphics.Matrix()
    private var rotationAngle = 0f
    private var animator: android.animation.ValueAnimator? = null

    init {
        animator = android.animation.ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1500
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { animation ->
                rotationAngle = animation.animatedValue as Float
                invalidate()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shader = android.graphics.SweepGradient(
            w.toFloat() / 2f,
            h.toFloat() / 2f,
            intArrayOf(
                Color.parseColor("#FF007F"),
                Color.parseColor("#7F00FF"),
                Color.parseColor("#00F0FF"),
                Color.parseColor("#00FF66"),
                Color.parseColor("#FF007F")
            ),
            null
        )
        paintInner.shader = shader
        paintOuter.shader = shader
        animator?.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        shaderMatrix.setRotate(rotationAngle, w / 2f, h / 2f)
        shader?.setLocalMatrix(shaderMatrix)

        val outerHalf = paintOuter.strokeWidth / 2f
        canvas.drawRect(outerHalf, outerHalf, w - outerHalf, h - outerHalf, paintOuter)

        val innerHalf = paintInner.strokeWidth / 2f
        canvas.drawRect(innerHalf, innerHalf, w - innerHalf, h - innerHalf, paintInner)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
