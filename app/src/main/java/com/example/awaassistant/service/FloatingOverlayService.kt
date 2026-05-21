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
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.graphics.PointF
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.awaassistant.MainActivity

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
                intArrayOf(Color.parseColor("#4A00E0"), Color.parseColor("#8E2DE2")) // 炫酷紫蓝渐变色
            )
            drawable.shape = GradientDrawable.OVAL
            drawable.setStroke(dpToPx(2), Color.WHITE) // 白色细边框
            background = drawable
            
            // 可以设置一个 AI 图标（这里简单添加一个小白点，或者可以通过代码绘制一个星芒）
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageResource(android.R.drawable.ic_menu_help) // 默认使用系统问号图标
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
            x = windowManager.defaultDisplay.width - size - dpToPx(16) // 默认靠右显示
            y = windowManager.defaultDisplay.height / 2
        }

        // 设置触摸与拖拽事件
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private val touchSlop = ViewConfiguration.get(this@FloatingOverlayService).scaledTouchSlop
            private var isDragging = false
            private val pathPoints = mutableListOf<PointF>()

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        pathPoints.clear()
                        pathPoints.add(PointF(event.rawX, event.rawY))
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        pathPoints.add(PointF(event.rawX, event.rawY))
                        
                        if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            isDragging = true
                        }
                        
                        if (isDragging) {
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            windowManager.updateViewLayout(container, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        pathPoints.add(PointF(event.rawX, event.rawY))
                        if (checkLGesture(pathPoints)) {
                            // 识别到 L 手势，复位悬浮球并触发截图
                            params.x = initialX
                            params.y = initialY
                            windowManager.updateViewLayout(container, params)
                            
                            Toast.makeText(this@FloatingOverlayService, "识别到 L 手势，开始分析...", Toast.LENGTH_SHORT).show()
                            onBubbleClicked()
                        } else if (!isDragging) {
                            // 轻按事件：触发当前屏幕捕获
                            onBubbleClicked()
                        } else {
                            // 拖拽结束：自动吸附到屏幕边缘
                            val screenWidth = windowManager.defaultDisplay.width
                            val bubbleCenter = params.x + size / 2
                            if (bubbleCenter < screenWidth / 2) {
                                params.x = dpToPx(8) // 靠左
                            } else {
                                params.x = screenWidth - size - dpToPx(8) // 靠右
                            }
                            windowManager.updateViewLayout(container, params)
                        }
                        return true
                    }
                }
                return false
            }

            private fun checkLGesture(points: List<PointF>): Boolean {
                if (points.size < 10) return false

                val p0 = points.first()
                val pn = points.last()

                // 寻找极值点作为潜在的拐点
                var maxIndex = 0
                var maxY = p0.y
                var minIndex = 0
                var minY = p0.y

                for (i in points.indices) {
                    if (points[i].y > maxY) {
                        maxY = points[i].y
                        maxIndex = i
                    }
                    if (points[i].y < minY) {
                        minY = points[i].y
                        minIndex = i
                    }
                }

                val density = resources.displayMetrics.density
                val threshold = 70 * density  // 手势各笔划的最小偏移量 (70dp)
                val tolerance = 60 * density  // 垂直或水平垂直分量的容差 (60dp)

                // 1. 校验【先下后横】(极值点 maxIndex 为拐点)
                val pivotDown = points[maxIndex]
                val downStrokeY = pivotDown.y - p0.y
                val downStrokeX = Math.abs(pivotDown.x - p0.x)
                val horizStrokeX1 = Math.abs(pn.x - pivotDown.x)
                val horizStrokeY1 = Math.abs(pn.y - pivotDown.y)

                if (downStrokeY >= threshold && downStrokeX <= tolerance &&
                    horizStrokeX1 >= threshold && horizStrokeY1 <= tolerance &&
                    maxIndex > 2 && maxIndex < points.size - 3) {
                    Log.d(TAG, "L Gesture Detected: Down then Horizontal")
                    return true
                }

                // 2. 校验【先上后横】(极值点 minIndex 为拐点)
                val pivotUp = points[minIndex]
                val upStrokeY = p0.y - pivotUp.y
                val upStrokeX = Math.abs(pivotUp.x - p0.x)
                val horizStrokeX2 = Math.abs(pn.x - pivotUp.x)
                val horizStrokeY2 = Math.abs(pn.y - pivotUp.y)

                if (upStrokeY >= threshold && upStrokeX <= tolerance &&
                    horizStrokeX2 >= threshold && horizStrokeY2 <= tolerance &&
                    minIndex > 2 && minIndex < points.size - 3) {
                    Log.d(TAG, "L Gesture Detected: Up then Horizontal")
                    return true
                }

                return false
            }
        })

        floatView = container
        windowManager.addView(container, params)
    }

    private fun onBubbleClicked() {
        val accessibilityService = AwaAccessibilityService.instance
        if (accessibilityService != null) {
            // 触发辅助服务截图分析
            accessibilityService.triggerScreenCapture()
        } else {
            Toast.makeText(this, "Awa 助手辅助功能未启用，请先开启", Toast.LENGTH_LONG).show()
            // 引导前往开启辅助服务
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
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
            flashView.alpha = 0f // 初始完全透明，避免闪烁
            
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
                
                // 1. 淡入动画 (300ms)
                flashView.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .withEndAction {
                        // 2. 停留流光溢彩时间 (1200ms) 随后淡出
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
        alpha = 76 // ~30% 不透明度，营造霓虹霓虹晕染感
    }
    
    private var shader: android.graphics.SweepGradient? = null
    private val shaderMatrix = android.graphics.Matrix()
    private var rotationAngle = 0f
    private var animator: android.animation.ValueAnimator? = null

    init {
        // 创建无限旋转的属性动画，驱动渐变旋转
        animator = android.animation.ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1500 // 1.5秒旋转一圈，速度灵动自然
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
        // 使用 SweepGradient 环绕屏幕中心形成闭环彩虹
        shader = android.graphics.SweepGradient(
            w.toFloat() / 2f,
            h.toFloat() / 2f,
            intArrayOf(
                Color.parseColor("#FF007F"), // 玫红
                Color.parseColor("#7F00FF"), // 炫酷紫
                Color.parseColor("#00F0FF"), // 极光青
                Color.parseColor("#00FF66"), // 荧光绿
                Color.parseColor("#FF007F")  // 闭环
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

        // 旋转渐变 Matrix
        shaderMatrix.setRotate(rotationAngle, w / 2f, h / 2f)
        shader?.setLocalMatrix(shaderMatrix)

        // 1. 绘制外层发光晕染
        val outerHalf = paintOuter.strokeWidth / 2f
        canvas.drawRect(outerHalf, outerHalf, w - outerHalf, h - outerHalf, paintOuter)

        // 2. 绘制内层高亮边缘
        val innerHalf = paintInner.strokeWidth / 2f
        canvas.drawRect(innerHalf, innerHalf, w - innerHalf, h - innerHalf, paintInner)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
