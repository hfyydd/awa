package com.example.awaassistant.service

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.content.Context
import android.view.WindowManager
import android.view.Display
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import com.example.awaassistant.MainActivity
import com.example.awaassistant.R
import com.example.awaassistant.util.AsrManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private var recordingView: View? = null
    private var recordingStartTime = 0L
    
    private val handler = Handler(Looper.getMainLooper())
    private val recordingDurationUpdater = object : Runnable {
        override fun run() {
            updateRecordingDuration()
            handler.postDelayed(this, 1000)
        }
    }

    private var isAnalyzing = false
    private var pulseAnimator: ValueAnimator? = null

    fun setAnalyzingState(analyzing: Boolean) {
        handler.post {
            if (isAnalyzing == analyzing) return@post
            isAnalyzing = analyzing
            val container = floatView as? FrameLayout ?: return@post
            val bubble = container.getChildAt(0) as? ImageView ?: return@post
            
            if (analyzing) {
                // Change stroke/tint to gold/yellow when thinking
                bubble.setColorFilter(Color.parseColor("#FFD700"))
                
                pulseAnimator?.cancel()
                pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.15f).apply {
                    duration = 800
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { anim ->
                        val value = anim.animatedValue as Float
                        bubble.scaleX = value
                        bubble.scaleY = value
                    }
                    start()
                }
            } else {
                pulseAnimator?.cancel()
                pulseAnimator = null
                bubble.scaleX = 1.0f
                bubble.scaleY = 1.0f
                bubble.setColorFilter(Color.WHITE)
            }
        }
    }

    companion object {
        private const val TAG = "FloatingOverlayService"
        private const val LONG_PRESS_THRESHOLD = 400L
        private const val DOUBLE_TAP_THRESHOLD = 300L

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
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingBubble()
    }

    fun reloadConfig() {
        handler.post {
            floatView?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
                floatView = null
            }
            if (com.example.awaassistant.data.SettingsManager.isFloatingBallEnabled(this)) {
                createFloatingBubble()
            }
        }
    }

    private fun createFloatingBubble() {
        if (!com.example.awaassistant.data.SettingsManager.isFloatingBallEnabled(this)) return

        val size = dpToPx(56)
        val container = FrameLayout(this)

        // 渐变发光球体
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

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            size, size, layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = windowManager.defaultDisplay.width - size - dpToPx(16)
            y = windowManager.defaultDisplay.height / 2
        }

        // 手势状态
        var isDragging = false
        var isLongPressing = false

        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private val touchSlop = ViewConfiguration.get(this@FloatingOverlayService).scaledTouchSlop

            private val longPressRunnable = Runnable {
                isLongPressing = true
                onLongPressStarted()
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        isLongPressing = false
                        handler.postDelayed(longPressRunnable, LONG_PRESS_THRESHOLD)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            isDragging = true
                            handler.removeCallbacks(longPressRunnable)
                        }
                        if (isDragging) {
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            windowManager.updateViewLayout(container, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPressRunnable)

                        when {
                            isDragging -> {
                                // 吸附到边缘
                                val screenW = windowManager.defaultDisplay.width
                                params.x = if (params.x + size / 2 < screenW / 2) dpToPx(8) else screenW - size - dpToPx(8)
                                windowManager.updateViewLayout(container, params)
                            }
                            isLongPressing -> {
                                // 长按结束 → 停止录音
                                onLongPressEnded()
                            }
                            else -> {
                                // 单击 → 立即截图分析！
                                onSingleTapped()
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        return true
                    }
                }
                return false
            }
        })

        floatView = container
        windowManager.addView(container, params)
    }

    // ─── 悬浮球手势响应逻辑 ───────────────────────────────────

    private fun onSingleTapped() {
        triggerScreenCapture()
    }

    private fun triggerScreenCapture() {
        if (isAnalyzing) {
            Toast.makeText(this, "AI 正在分析中，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }
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

    // ─── 长按 → 录音 + 悬浮指示器 UI ───────────────────────────────────

    private fun onLongPressStarted() {
        // 震动反馈
        vibrateShort()

        // 启动 ASR 录音
        val started = AsrManager.startRecording()
        if (!started) {
            Toast.makeText(this, "语音引擎初始化中，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }

        // 显示录音指示器
        showRecordingIndicator()

        // 悬浮球放大脉冲动画
        animateBubblePress(true)
    }

    private fun onLongPressEnded() {
        // 停止录音
        val audioText = AsrManager.stopRecordingSync()

        // 隐藏录音指示器
        hideRecordingIndicator()

        // 悬浮球恢复
        animateBubblePress(false)

        // 有录音内容 → 弹出 Quick Capture 处理
        if (audioText.isNotBlank()) {
            showQuickCaptureWithText(audioText)
        } else {
            Toast.makeText(this, "未检测到语音", Toast.LENGTH_SHORT).show()
        }
    }

    /** 震动反馈（15ms） */
    private fun vibrateShort() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(15)
                }
            }
        } catch (_: Exception) {}
    }

    /** 悬浮球按压动画 */
    private fun animateBubblePress(pressed: Boolean) {
        val bubble = (floatView as? FrameLayout)?.getChildAt(0) as? ImageView ?: return
        val scale = if (pressed) 1.25f else 1f
        bubble.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    // ─── 录音指示器 UI（悬浮球上方显示）──────────────────────────────────

    private fun showRecordingIndicator() {
        if (recordingView != null) return
        recordingStartTime = System.currentTimeMillis()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // 气泡主体
        val bubble = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(28).toFloat()
                setColor(Color.parseColor("#F0202020"))
                setStroke(dpToPx(1), Color.parseColor("#40FFFFFF"))
            }
            background = bg
            elevation = dpToPx(12).toFloat()
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(14))
        }

        // 脉冲红点
        val dot = View(this).apply {
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B30"))
            }
            background = dotBg
        }
        content.addView(dot, FrameLayout.LayoutParams(dpToPx(12), dpToPx(12)))

        // 脉冲动画
        ValueAnimator.ofFloat(0.3f, 1f, 0.3f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { dot.alpha = it.animatedValue as Float }
            start()
        }

        // 麦克风图标
        val micIcon = TextView(this).apply {
            text = "🎤"
            textSize = 16f
            setPadding(dpToPx(10), 0, 0, 0)
        }
        content.addView(micIcon)

        // 录音文字
        val label = TextView(this).apply {
            text = "正在录音"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(8), 0, 0, 0)
        }
        content.addView(label)

        // 时长
        val durationText = TextView(this).apply {
            text = "0:00"
            setTextColor(Color.parseColor("#999999"))
            textSize = 14f
            setPadding(dpToPx(16), 0, 0, 0)
        }
        content.addView(durationText)

        bubble.addView(content)
        container.addView(bubble)

        // 获取悬浮球位置，定位在上方
        val floatPos = getFloatingBubblePosition()

        val indicatorParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = floatPos.first - dpToPx(40)
            y = floatPos.second - dpToPx(90)
        }

        // 入场动画：缩小弹入
        bubble.scaleX = 0.3f
        bubble.scaleY = 0.3f
        bubble.alpha = 0f
        bubble.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()

        recordingView = container
        windowManager.addView(container, indicatorParams)

        handler.post(recordingDurationUpdater)
    }

    private fun getFloatingBubblePosition(): Pair<Int, Int> {
        return try {
            val view = floatView as? FrameLayout
            val lp = view?.layoutParams as? WindowManager.LayoutParams
            Pair(lp?.x ?: 0, lp?.y ?: 0)
        } catch (_: Exception) {
            Pair(windowManager.defaultDisplay.width - dpToPx(200), windowManager.defaultDisplay.height / 2)
        }
    }

    private fun updateRecordingDuration() {
        val view = recordingView ?: return
        val bubble = (view as? FrameLayout)?.getChildAt(0) as? FrameLayout
        val content = bubble?.getChildAt(0) as? LinearLayout
        val durationText = content?.getChildAt(3) as? TextView
        
        val elapsed = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
        val min = elapsed / 60
        val sec = elapsed % 60
        durationText?.text = String.format(Locale.getDefault(), "%d:%02d", min, sec)
    }

    private fun hideRecordingIndicator() {
        handler.removeCallbacks(recordingDurationUpdater)
        val view = recordingView ?: return

        val bubble = (view as? FrameLayout)?.getChildAt(0)
        bubble?.animate()
            ?.scaleX(0.5f)
            ?.scaleY(0.5f)
            ?.alpha(0f)
            ?.setDuration(200)
            ?.withEndAction {
                try { windowManager.removeView(view) } catch (_: Exception) {}
            }
            ?.start()

        recordingView = null
    }

    private fun showQuickCaptureWithText(text: String) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                action = MainActivity.ACTION_SHOW_QUICK_CAPTURE_WITH_TEXT
                putExtra(MainActivity.EXTRA_INITIAL_TEXT, text)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Quick Capture", e)
        }
    }

    // ─── 优化彩虹闪光效果 ───────────────────────────────────

    /** 全屏彩色闪光（截图时触发） */
    fun triggerScreenFlash() {
        handler.post {
            val flashView = RainbowFlashView(this)
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
                        handler.postDelayed({
                            flashView.animate()
                                .alpha(0f)
                                .setDuration(400)
                                .withEndAction {
                                    try { windowManager.removeView(flashView) } catch (_: Exception) {}
                                }
                                .start()
                        }, 500)
                    }
                    .start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add flash view", e)
            }
        }
    }

    // ─── 辅助方法 ───────────────────────────────────

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    fun hideFloatingBubble() {
        handler.post { floatView?.visibility = View.GONE }
    }

    fun showFloatingBubble() {
        handler.post { floatView?.visibility = View.VISIBLE }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        handler.removeCallbacks(recordingDurationUpdater)
        pulseAnimator?.cancel()
        pulseAnimator = null
        floatView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        recordingView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        floatView = null
        recordingView = null
    }
}

/** 全屏彩虹边框闪光视图：屏幕四周旋转的彩虹边框 */
class RainbowFlashView(context: Context) : View(context) {

    private val paintInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(6).toFloat()
    }
    
    private val paintOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(16).toFloat()
        alpha = 76
    }
    
    private var shader: SweepGradient? = null
    private val shaderMatrix = Matrix()
    private var rotationAngle = 0f
    private var animator: ValueAnimator? = null

    init {
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { animation ->
                rotationAngle = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shader = SweepGradient(
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

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
