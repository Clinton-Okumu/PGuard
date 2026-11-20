package com.example.pguard.engine.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
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
 * Manages non-destructive UI state transitions when restricted content is detected:
 *
 * Tier 2: System-level overlay window attached via WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
 *         with true cross-window hardware Gaussian blur (FLAG_BLUR_BEHIND + blurBehindRadius) on Android 12+,
 *         and opaque frosted obsidian fallback on pre-Android 12 so content is NEVER see-through or transparent.
 *
 * Tier 1: Targeted container-level frosted blur overlay for feed items in supported native lists.
 *
 * Guaranteed cleanup: Synchronous immediate removal (removeViewImmediate), single active overlay enforcement,
 * and zero sticky ghost views.
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
    private var autoDismissRunnable: Runnable? = null

    @Volatile
    var isFullScreenShowing: Boolean = false
        private set

    @Volatile
    var isContainerShowing: Boolean = false
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

    /**
     * Tier 2: Full-Screen Display Overlay Window.
     * Features authentic hardware Gaussian blur (FLAG_BLUR_BEHIND + blurBehindRadius)
     * on Android 12+, and solid frosted obsidian on pre-Android 12 so that sensitive
     * content is NEVER see-through or transparent.
     */
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
                if (isFullScreenShowing) {
                    Log.d(TAG, "Full-screen overlay already showing, ignoring duplicate call.")
                    return@runOnMainThread
                }

                // Clean up ANY existing overlays immediately to prevent sticking
                hideAllImmediate()

                val root = object : FrameLayout(service) {
                    init {
                        isFocusable = true
                        isFocusableInTouchMode = true
                    }

                    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                            Log.i(TAG, "Back key intercepted in overlay, navigating back.")
                            dismissFullScreenAndExecute(onGoBack)
                            return true
                        }
                        return super.dispatchKeyEvent(event)
                    }
                }

                val hasBlur = isCrossWindowBlurSupported()
                // If device supports hardware cross-window blur: frosted translucent obsidian.
                // If not supported: opaque solid obsidian so content is never visible through glass.
                val bgColor = if (hasBlur) 0xDD0D0F14.toInt() else 0xFD0D0F14.toInt()
                root.setBackgroundColor(bgColor)

                val contentLayout = LinearLayout(service).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dpToPx(24f), dpToPx(32f), dpToPx(24f), dpToPx(32f))
                }

                val frameParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                root.addView(contentLayout, frameParams)

                // 1. PGuard Squircle Brand Icon
                val iconBox = FrameLayout(service).apply {
                    val bg = GradientDrawable().apply {
                        cornerRadius = dpToPx(20f).toFloat()
                        setColor(COLOR_CORAL)
                    }
                    background = bg
                }
                val iconBoxParams = LinearLayout.LayoutParams(dpToPx(88f), dpToPx(88f)).apply {
                    bottomMargin = dpToPx(20f)
                }

                val brandIcon = ImageView(service).apply {
                    setImageResource(R.drawable.ic_pguard_globe_shield)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                val brandIconParams = FrameLayout.LayoutParams(dpToPx(56f), dpToPx(56f), Gravity.CENTER)
                iconBox.addView(brandIcon, brandIconParams)
                contentLayout.addView(iconBox, iconBoxParams)

                // 2. Title
                val titleView = TextView(service).apply {
                    text = "Access Blocked"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
                val titleParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(8f)
                }
                contentLayout.addView(titleView, titleParams)

                // 3. Subtitle / Message
                val messageView = TextView(service).apply {
                    text = message
                    setTextColor(COLOR_TEXT_MUTED)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                    gravity = Gravity.CENTER
                    setLineSpacing(dpToPx(2f).toFloat(), 1.1f)
                }
                val messageParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(16f)
                }
                contentLayout.addView(messageView, messageParams)

                // 4. Blocked Target Chip
                if (target.isNotBlank()) {
                    val chipView = TextView(service).apply {
                        text = target
                        setTextColor(COLOR_CORAL)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        val chipBg = GradientDrawable().apply {
                            cornerRadius = dpToPx(10f).toFloat()
                            setColor(COLOR_CHIP_BG)
                            setStroke(dpToPx(1f), 0x4DFF5252.toInt())
                        }
                        background = chipBg
                        setPadding(dpToPx(14f), dpToPx(7f), dpToPx(14f), dpToPx(7f))
                        maxLines = 2
                    }
                    val chipParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(28f)
                    }
                    contentLayout.addView(chipView, chipParams)
                }

                // 5. Prominent "Go Back" Action Button
                var currentCountdown = if (countdownSeconds > 0) countdownSeconds else 0

                val goBackButton = Button(service).apply {
                    isClickable = true
                    isFocusable = true
                    isEnabled = true
                    text = if (currentCountdown > 0) "Go Back ($currentCountdown s)" else "Go Back to Safety"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.DEFAULT_BOLD
                    isAllCaps = false
                    val btnBg = GradientDrawable().apply {
                        cornerRadius = dpToPx(14f).toFloat()
                        setColor(COLOR_CORAL)
                    }
                    background = btnBg
                    setOnClickListener {
                        Log.i(TAG, "User clicked 'Go Back' on overlay.")
                        dismissFullScreenAndExecute(onGoBack)
                    }
                }
                val btnParams = LinearLayout.LayoutParams(
                    (service.resources.displayMetrics.widthPixels * 0.82f).toInt(),
                    dpToPx(50f)
                ).apply {
                    bottomMargin = dpToPx(12f)
                }
                contentLayout.addView(goBackButton, btnParams)

                // 6. Safe Redirect / Alternate Escape
                if (redirectUrl.isNotBlank()) {
                    val redirectBtn = Button(service).apply {
                        text = "Open Safe Site: $redirectUrl"
                        setTextColor(COLOR_TEXT_MUTED)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        typeface = Typeface.DEFAULT_BOLD
                        isAllCaps = false
                        val secBg = GradientDrawable().apply {
                            cornerRadius = dpToPx(12f).toFloat()
                            setColor(COLOR_CHIP_BG)
                            setStroke(dpToPx(1f), 0x33FFFFFF.toInt())
                        }
                        background = secBg
                        setOnClickListener {
                            handleSafeRedirect(redirectUrl)
                            hideFullScreenOverlay()
                        }
                    }
                    val redirectParams = LinearLayout.LayoutParams(
                        (service.resources.displayMetrics.widthPixels * 0.82f).toInt(),
                        dpToPx(44f)
                    )
                    contentLayout.addView(redirectBtn, redirectParams)
                }

                // Countdown Timer Logic
                if (currentCountdown > 0) {
                    countdownRunnable = object : Runnable {
                        override fun run() {
                            currentCountdown--
                            if (currentCountdown > 0) {
                                goBackButton.text = "Go Back ($currentCountdown s)"
                                mainHandler.postDelayed(this, 1000L)
                            } else {
                                Log.i(TAG, "Countdown finished, automatically executing safe back navigation.")
                                dismissFullScreenAndExecute(onGoBack)
                            }
                        }
                    }
                    mainHandler.postDelayed(countdownRunnable!!, 1000L)
                }

                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )

                // Apply Hardware Cross-Window Gaussian Blur on Android 12+
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
                Log.i(TAG, "Full-screen TYPE_ACCESSIBILITY_OVERLAY rendered successfully with blur.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display full-screen accessibility overlay: ${e.message}", e)
            }
        }
    }

    /**
     * Focus Mode Overlay Window:
     * Displayed when a user opens a restricted app during a scheduled Focus Mode session.
     */
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

                val root = object : FrameLayout(service) {
                    init {
                        isFocusable = true
                        isFocusableInTouchMode = true
                    }

                    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                            dismissFullScreenAndExecute(onGoHome)
                            return true
                        }
                        return super.dispatchKeyEvent(event)
                    }
                }

                val hasBlur = isCrossWindowBlurSupported()
                val bgColor = if (hasBlur) 0xDD0D0F14.toInt() else 0xFD0D0F14.toInt()
                root.setBackgroundColor(bgColor)

                val contentLayout = LinearLayout(service).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dpToPx(24f), dpToPx(32f), dpToPx(24f), dpToPx(32f))
                }

                root.addView(
                    contentLayout,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                )

                // 1. Focus Mode Icon Box
                val iconBox = FrameLayout(service).apply {
                    val bg = GradientDrawable().apply {
                        cornerRadius = dpToPx(20f).toFloat()
                        setColor(COLOR_CORAL)
                    }
                    background = bg
                }
                val iconBoxParams = LinearLayout.LayoutParams(dpToPx(88f), dpToPx(88f)).apply {
                    bottomMargin = dpToPx(20f)
                }
                val focusIcon = ImageView(service).apply {
                    setImageResource(R.drawable.ic_focus_mode)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                iconBox.addView(focusIcon, FrameLayout.LayoutParams(dpToPx(52f), dpToPx(52f), Gravity.CENTER))
                contentLayout.addView(iconBox, iconBoxParams)

                // 2. Title
                val titleView = TextView(service).apply {
                    text = "Focus Mode Active"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
                contentLayout.addView(titleView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(8f)
                })

                // 3. Message
                val messageView = TextView(service).apply {
                    text = "This app is blocked during your scheduled focus time ($scheduleInfo). Stay focused on what matters."
                    setTextColor(COLOR_TEXT_MUTED)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                    gravity = Gravity.CENTER
                    setLineSpacing(dpToPx(2f).toFloat(), 1.1f)
                }
                contentLayout.addView(messageView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(16f)
                })

                // 4. App Chip
                val chipView = TextView(service).apply {
                    text = appName
                    setTextColor(COLOR_CORAL)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    val chipBg = GradientDrawable().apply {
                        cornerRadius = dpToPx(10f).toFloat()
                        setColor(COLOR_CHIP_BG)
                        setStroke(dpToPx(1f), 0x4DFF5252.toInt())
                    }
                    background = chipBg
                    setPadding(dpToPx(14f), dpToPx(7f), dpToPx(14f), dpToPx(7f))
                }
                contentLayout.addView(chipView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(28f)
                })

                // 5. Return to Home Button
                val returnButton = Button(service).apply {
                    text = "Return to Safety"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.DEFAULT_BOLD
                    isAllCaps = false
                    val btnBg = GradientDrawable().apply {
                        cornerRadius = dpToPx(14f).toFloat()
                        setColor(COLOR_CORAL)
                    }
                    background = btnBg
                    setOnClickListener {
                        dismissFullScreenAndExecute(onGoHome)
                    }
                }
                contentLayout.addView(returnButton, LinearLayout.LayoutParams(
                    (service.resources.displayMetrics.widthPixels * 0.82f).toInt(),
                    dpToPx(50f)
                ))

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
                    } catch (_: Exception) {}
                }

                windowManager.addView(root, layoutParams)
                activeViews.add(root)
                isFullScreenShowing = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display focus overlay: ${e.message}")
            }
        }
    }

    /**
     * Tier 1: Container-Level Mask for Native Feed Items.
     * Uses synchronous tracking and immediate cleanup to ensure it NEVER sticks on screen.
     */
    fun showContainerOverlay(bounds: Rect, label: String = "Restricted content") {
        runOnMainThread {
            try {
                if (isFullScreenShowing) return@runOnMainThread

                // Synchronously wipe all previous container views
                hideContainerOverlayImmediate()

                val displayMetrics = service.resources.displayMetrics
                val cardWidth = displayMetrics.widthPixels
                val cardHeight = bounds.height().coerceIn(dpToPx(140f), dpToPx(340f))

                val containerRoot = FrameLayout(service).apply {
                    val hasBlur = isCrossWindowBlurSupported()
                    val bg = GradientDrawable().apply {
                        cornerRadius = dpToPx(16f).toFloat()
                        setColor(if (hasBlur) 0xEE121520.toInt() else 0xFA121520.toInt())
                        setStroke(dpToPx(1.5f), COLOR_CORAL)
                    }
                    background = bg
                }

                val innerContent = LinearLayout(service).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
                }
                containerRoot.addView(
                    innerContent,
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                )

                // Shield Icon
                val icon = ImageView(service).apply {
                    setImageResource(R.drawable.ic_pguard_shield)
                    setColorFilter(COLOR_CORAL)
                }
                innerContent.addView(icon, LinearLayout.LayoutParams(dpToPx(32f), dpToPx(32f)).apply {
                    bottomMargin = dpToPx(8f)
                })

                // Text details
                val header = TextView(service).apply {
                    text = "Restricted Content Hidden"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
                val subtext = TextView(service).apply {
                    text = "$label • Scroll past to proceed"
                    setTextColor(COLOR_TEXT_MUTED)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    gravity = Gravity.CENTER
                }
                innerContent.addView(header)
                innerContent.addView(subtext)

                val safeY = bounds.top.coerceIn(0, displayMetrics.heightPixels - cardHeight)

                val layoutParams = WindowManager.LayoutParams(
                    cardWidth,
                    cardHeight,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = safeY
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                        layoutParams.blurBehindRadius = dpToPx(35f)
                    } catch (_: Exception) {}
                }

                windowManager.addView(containerRoot, layoutParams)
                activeViews.add(containerRoot)
                isContainerShowing = true

                // Auto-clear after 3 seconds so it never sticks
                autoDismissRunnable = Runnable { hideContainerOverlay() }
                mainHandler.postDelayed(autoDismissRunnable!!, 3000L)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display container overlay: ${e.message}")
            }
        }
    }

    fun hideFullScreenOverlay() {
        runOnMainThread {
            countdownRunnable?.let { mainHandler.removeCallbacks(it) }
            countdownRunnable = null
            hideAllImmediate()
            isFullScreenShowing = false
        }
    }

    fun hideContainerOverlay() {
        runOnMainThread {
            hideContainerOverlayImmediate()
        }
    }

    private fun hideContainerOverlayImmediate() {
        autoDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        autoDismissRunnable = null

        synchronized(activeViews) {
            val iterator = activeViews.iterator()
            while (iterator.hasNext()) {
                val view = iterator.next()
                if (!isFullScreenShowing || view.layoutParams.width != WindowManager.LayoutParams.MATCH_PARENT) {
                    removeViewSafe(view)
                    iterator.remove()
                }
            }
        }
        isContainerShowing = false
    }

    fun hideAll() {
        runOnMainThread {
            hideAllImmediate()
        }
    }

    private fun hideAllImmediate() {
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRunnable = null
        autoDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        autoDismissRunnable = null

        synchronized(activeViews) {
            val iterator = activeViews.iterator()
            while (iterator.hasNext()) {
                val view = iterator.next()
                removeViewSafe(view)
                iterator.remove()
            }
        }
        isFullScreenShowing = false
        isContainerShowing = false
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
            val fullUrl = if (!redirectUrl.startsWith("http://") && !redirectUrl.startsWith("https://")) {
                "https://$redirectUrl"
            } else {
                redirectUrl
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
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
