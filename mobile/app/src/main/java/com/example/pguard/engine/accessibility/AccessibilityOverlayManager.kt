package com.example.pguard.engine.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.pguard.R
import java.util.Collections

/**
 * Manages system-level TYPE_ACCESSIBILITY_OVERLAY windows when restricted content,
 * Focus Mode schedules, or emergency urge lockdowns are triggered.
 *
 * Applies cross-window hardware Gaussian blur on Android 12+ and an opaque frosted obsidian
 * fallback on older versions so sensitive content is never visible behind the overlay.
 */
class AccessibilityOverlayManager(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "PGuardOverlayManager"
        private const val COLOR_CORAL = 0xFFFF5252.toInt()
        private const val COLOR_CHIP_BG = 0x26FFFFFF.toInt()
        private const val COLOR_TEXT_MUTED = 0xB3FFFFFF.toInt()
    }

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeViews = Collections.synchronizedSet(mutableSetOf<View>())
    private var countdownRunnable: Runnable? = null

    @Volatile
    var isFullScreenShowing: Boolean = false
        private set

    private fun isCrossWindowBlurSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                windowManager.isCrossWindowBlurEnabled()
            } catch (_: Exception) {
                true
            }
        } else {
            false
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    fun showFullScreenOverlay(
        target: String,
        message: String = "This page is blocked.",
        countdownSeconds: Int = 3,
        redirectUrl: String = "",
        onGoBack: () -> Unit = {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    ) {
        runOnMainThread {
            try {
                if (isFullScreenShowing) return@runOnMainThread
                hideAllImmediate()

                val root = createOverlayContainer(
                    bgBlurColor = 0xDD0D0F14.toInt(),
                    bgSolidColor = 0xFD0D0F14.toInt(),
                    onBack = { dismissFullScreenAndExecute(onGoBack) }
                )
                val contentLayout = createOverlayContentColumn()
                root.addView(
                    contentLayout,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )

                addBrandHeader(
                    contentLayout = contentLayout,
                    iconRes = R.drawable.ic_pguard_globe_shield,
                    boxSizeDp = 88f,
                    iconSizeDp = 56f,
                    cornerRadiusDp = 20f,
                    boxBottomMarginDp = 20f,
                    titleText = "Access Blocked",
                    titleSizeSp = 22f,
                    messageText = message,
                    messageSizeSp = 14.5f,
                    messageBottomMarginDp = 16f
                )

                if (target.isNotBlank()) {
                    addTargetChip(contentLayout, target)
                }

                var currentCountdown = if (countdownSeconds > 0) countdownSeconds else 0
                val goBackButton = createPrimaryButton(
                    label = if (currentCountdown > 0) "Go Back ($currentCountdown s)" else "Go Back to Safety",
                    widthFraction = 0.82f,
                    bottomMarginDp = 12f
                ) {
                    dismissFullScreenAndExecute(onGoBack)
                }
                contentLayout.addView(goBackButton.first, goBackButton.second)

                if (redirectUrl.isNotBlank()) {
                    val redirectBtn = Button(service).apply {
                        text = "Open Safe Site: $redirectUrl"
                        setTextColor(COLOR_TEXT_MUTED)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        typeface = Typeface.DEFAULT_BOLD
                        isAllCaps = false
                        background = GradientDrawable().apply {
                            cornerRadius = dpToPx(12f).toFloat()
                            setColor(COLOR_CHIP_BG)
                            setStroke(dpToPx(1f), 0x33FFFFFF.toInt())
                        }
                        setOnClickListener {
                            handleSafeRedirect(redirectUrl)
                            hideFullScreenOverlay()
                        }
                    }
                    contentLayout.addView(
                        redirectBtn,
                        LinearLayout.LayoutParams(
                            (service.resources.displayMetrics.widthPixels * 0.82f).toInt(),
                            dpToPx(44f)
                        )
                    )
                }

                if (currentCountdown > 0) {
                    val buttonView = goBackButton.first
                    countdownRunnable = object : Runnable {
                        override fun run() {
                            currentCountdown--
                            if (currentCountdown > 0) {
                                buttonView.text = "Go Back ($currentCountdown s)"
                                mainHandler.postDelayed(this, 1000L)
                            } else {
                                dismissFullScreenAndExecute(onGoBack)
                            }
                        }
                    }
                    mainHandler.postDelayed(countdownRunnable!!, 1000L)
                }

                attachFullScreenView(root)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display full-screen accessibility overlay: ${e.message}", e)
            }
        }
    }

    fun showFocusOverlay(
        appName: String,
        scheduleInfo: String,
        onGoHome: () -> Unit = {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    ) {
        runOnMainThread {
            try {
                if (isFullScreenShowing) return@runOnMainThread
                hideAllImmediate()

                val root = createOverlayContainer(
                    bgBlurColor = 0xDD0D0F14.toInt(),
                    bgSolidColor = 0xFD0D0F14.toInt(),
                    onBack = { dismissFullScreenAndExecute(onGoHome) }
                )
                val contentLayout = createOverlayContentColumn()
                root.addView(
                    contentLayout,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )

                addBrandHeader(
                    contentLayout = contentLayout,
                    iconRes = R.drawable.ic_focus_mode,
                    boxSizeDp = 88f,
                    iconSizeDp = 52f,
                    cornerRadiusDp = 20f,
                    boxBottomMarginDp = 20f,
                    titleText = "Focus Mode Active",
                    titleSizeSp = 22f,
                    messageText = "This app is blocked during your scheduled focus time ($scheduleInfo). Stay focused on what matters.",
                    messageSizeSp = 14.5f,
                    messageBottomMarginDp = 16f
                )

                addTargetChip(contentLayout, appName)

                val returnButton = createPrimaryButton(
                    label = "Return to Safety",
                    widthFraction = 0.82f
                ) {
                    dismissFullScreenAndExecute(onGoHome)
                }
                contentLayout.addView(returnButton.first, returnButton.second)

                attachFullScreenView(root)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display focus overlay: ${e.message}")
            }
        }
    }

    fun showUrgeLockdownOverlay(
        appName: String,
        remainingSeconds: Int
    ) {
        runOnMainThread {
            try {
                if (isFullScreenShowing) return@runOnMainThread
                hideAllImmediate()

                val root = createOverlayContainer(
                    bgBlurColor = 0xEE0D0F14.toInt(),
                    bgSolidColor = 0xFF0D0F14.toInt(),
                    onBack = null // Absorb back key during emergency urge lockdown
                )
                val contentLayout = createOverlayContentColumn()
                root.addView(
                    contentLayout,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )

                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                val timeStr = String.format("%d:%02d", mins, secs)

                addBrandHeader(
                    contentLayout = contentLayout,
                    iconRes = R.drawable.ic_feature_lock,
                    boxSizeDp = 84f,
                    iconSizeDp = 48f,
                    cornerRadiusDp = 22f,
                    boxBottomMarginDp = 18f,
                    titleText = "Phone Locked (2-Min Urge Protocol)",
                    titleSizeSp = 20f,
                    messageText = "Your phone is locked down from use for 2 minutes ($timeStr remaining) to break the impulsive craving loop. Breathe and stay strong.",
                    messageSizeSp = 14f,
                    messageBottomMarginDp = 24f
                )

                val returnButton = createPrimaryButton(
                    label = "Return to Urge Breathing Protocol",
                    widthFraction = 0.84f
                ) {
                    dismissFullScreenAndExecute {
                        try {
                            val launchIntent = service.packageManager.getLaunchIntentForPackage(service.packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                service.startActivity(launchIntent)
                            }
                        } catch (_: Exception) {}
                    }
                }
                contentLayout.addView(returnButton.first, returnButton.second)

                attachFullScreenView(root)
                Log.i(TAG, "Urge lockdown overlay displayed (intercepted $appName).")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display urge lockdown overlay: ${e.message}")
            }
        }
    }

    private fun createOverlayContainer(
        bgBlurColor: Int,
        bgSolidColor: Int,
        onBack: (() -> Unit)?
    ): FrameLayout {
        val root = object : FrameLayout(service) {
            init {
                isFocusable = true
                isFocusableInTouchMode = true
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    onBack?.invoke()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.setBackgroundColor(if (isCrossWindowBlurSupported()) bgBlurColor else bgSolidColor)
        return root
    }

    private fun createOverlayContentColumn(): LinearLayout {
        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(24f), dpToPx(32f), dpToPx(24f), dpToPx(32f))
        }
    }

    private fun addBrandHeader(
        contentLayout: LinearLayout,
        iconRes: Int,
        boxSizeDp: Float,
        iconSizeDp: Float,
        cornerRadiusDp: Float,
        boxBottomMarginDp: Float,
        titleText: String,
        titleSizeSp: Float,
        messageText: String,
        messageSizeSp: Float,
        messageBottomMarginDp: Float
    ) {
        val iconBox = FrameLayout(service).apply {
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(cornerRadiusDp).toFloat()
                setColor(COLOR_CORAL)
            }
        }
        val brandIcon = ImageView(service).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconBox.addView(
            brandIcon,
            FrameLayout.LayoutParams(dpToPx(iconSizeDp), dpToPx(iconSizeDp), Gravity.CENTER)
        )
        contentLayout.addView(
            iconBox,
            LinearLayout.LayoutParams(dpToPx(boxSizeDp), dpToPx(boxSizeDp)).apply {
                bottomMargin = dpToPx(boxBottomMarginDp)
            }
        )

        val titleView = TextView(service).apply {
            text = titleText
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSizeSp)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        contentLayout.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8f)
            }
        )

        val messageView = TextView(service).apply {
            text = messageText
            setTextColor(COLOR_TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, messageSizeSp)
            gravity = Gravity.CENTER
            setLineSpacing(dpToPx(2f).toFloat(), 1.15f)
        }
        contentLayout.addView(
            messageView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(messageBottomMarginDp)
            }
        )
    }

    private fun addTargetChip(contentLayout: LinearLayout, target: String) {
        val chipView = TextView(service).apply {
            text = target
            setTextColor(COLOR_CORAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(10f).toFloat()
                setColor(COLOR_CHIP_BG)
                setStroke(dpToPx(1f), 0x4DFF5252.toInt())
            }
            setPadding(dpToPx(14f), dpToPx(7f), dpToPx(14f), dpToPx(7f))
            maxLines = 2
        }
        contentLayout.addView(
            chipView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(28f)
            }
        )
    }

    private fun createPrimaryButton(
        label: String,
        widthFraction: Float,
        bottomMarginDp: Float = 0f,
        onClick: () -> Unit
    ): Pair<Button, LinearLayout.LayoutParams> {
        val button = Button(service).apply {
            isClickable = true
            isFocusable = true
            isEnabled = true
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(14f).toFloat()
                setColor(COLOR_CORAL)
            }
            setOnClickListener { onClick() }
        }
        val params = LinearLayout.LayoutParams(
            (service.resources.displayMetrics.widthPixels * widthFraction).toInt(),
            dpToPx(50f)
        ).apply {
            if (bottomMarginDp > 0f) {
                bottomMargin = dpToPx(bottomMarginDp)
            }
        }
        return button to params
    }

    private fun attachFullScreenView(root: View) {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                layoutParams.blurBehindRadius = dpToPx(40f)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set blurBehindRadius: ${e.message}")
            }
        }
        windowManager.addView(root, layoutParams)
        activeViews.add(root)
        isFullScreenShowing = true
    }

    fun hideFullScreenOverlay() {
        runOnMainThread {
            countdownRunnable?.let { mainHandler.removeCallbacks(it) }
            countdownRunnable = null
            hideAllImmediate()
            isFullScreenShowing = false
        }
    }

    fun hideAll() {
        runOnMainThread {
            hideAllImmediate()
        }
    }

    private fun hideAllImmediate() {
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRunnable = null

        synchronized(activeViews) {
            val iterator = activeViews.iterator()
            while (iterator.hasNext()) {
                val view = iterator.next()
                removeViewSafe(view)
                iterator.remove()
            }
        }
        isFullScreenShowing = false
    }

    private fun removeViewSafe(view: View) {
        try {
            if (view.isAttachedToWindow) {
                windowManager.removeViewImmediate(view)
            } else {
                windowManager.removeView(view)
            }
        } catch (_: Exception) {}
    }

    private fun dismissFullScreenAndExecute(action: () -> Unit) {
        hideFullScreenOverlay()
        try {
            action()
        } catch (e: Exception) {
            Log.e(TAG, "Error during overlay dismiss action: ${e.message}")
        }
    }

    private fun handleSafeRedirect(redirectUrl: String) {
        try {
            val trimmed = redirectUrl.trim()
            if (trimmed.isEmpty()) return
            val fullUrl = when {
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                trimmed.contains(":") -> return
                else -> "https://$trimmed"
            }
            val parsed = Uri.parse(fullUrl)
            val scheme = parsed.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return

            val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch safe redirect: ${e.message}")
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            service.resources.displayMetrics
        ).toInt()
    }
}
