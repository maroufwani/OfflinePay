package com.mw.offlineupi.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/**
 * Manages a full-screen overlay using TYPE_ACCESSIBILITY_OVERLAY from the AccessibilityService.
 * Covers USSD dialogs so the user never sees them.
 * No SYSTEM_ALERT_WINDOW permission needed.
 *
 * Two modes:
 * - Progress: spinner + status text (non-focusable)
 * - PIN Entry: custom numpad PIN input (non-focusable, no system keyboard)
 *
 * Supports light and dark themes matching the app.
 */
object OverlayManager {
    private const val TAG = "OverlayManager"

    private var overlayView: View? = null
    private var miniView: View? = null
    private var statusText: TextView? = null
    private var stepText: TextView? = null
    private var currentStatus: String = ""
    private var currentStep: String = ""
    private var isPinMode: Boolean = false
    private var isAmountMode: Boolean = false
    private var pinMessage: String = ""
    private var amountMessage: String = ""
    private var amountVerifiedName: String? = null
    private var amountCountdownTimer: CountDownTimer? = null
    private var amountCountdownRemainingMs: Long = 60_000L
    private var pinCountdownTimer: CountDownTimer? = null
    private var pinCountdownRemainingMs: Long = 60_000L
    private var pinPayeeName: String? = null
    private var pinAmount: String? = null
    private var currentWrongAttempts: Int = 0
    private var currentMaxAttempts: Int = 3
    private val handler = Handler(Looper.getMainLooper())

    private data class ThemeColors(
        val background: Int,
        val surface: Int,
        val primary: Int,
        val primaryContainer: Int,
        val onPrimary: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textHint: Int,
        val error: Int,
        val warning: Int,
        val warningContainer: Int,
        val warningDark: Int,
        val divider: Int,
        val numpadButton: Int,
        val numpadText: Int,
        val pinDotFilled: Int,
        val pinDotEmpty: Int
    )

    private fun resolveTheme(context: Context): ThemeColors {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            ThemeColors(
                background = Color.parseColor("#121212"),
                surface = Color.parseColor("#1E1E1E"),
                primary = Color.parseColor("#5C6BC0"),
                primaryContainer = Color.parseColor("#1A237E"),
                onPrimary = Color.WHITE,
                textPrimary = Color.parseColor("#E0E0E0"),
                textSecondary = Color.parseColor("#9E9E9E"),
                textHint = Color.parseColor("#757575"),
                error = Color.parseColor("#EF5350"),
                warning = Color.parseColor("#FFB74D"),
                warningContainer = Color.parseColor("#3E2723"),
                warningDark = Color.parseColor("#FF8A65"),
                divider = Color.parseColor("#333333"),
                numpadButton = Color.parseColor("#2C2C2C"),
                numpadText = Color.parseColor("#E0E0E0"),
                pinDotFilled = Color.parseColor("#5C6BC0"),
                pinDotEmpty = Color.parseColor("#444444")
            )
        } else {
            ThemeColors(
                background = Color.parseColor("#F8F9FD"),
                surface = Color.WHITE,
                primary = Color.parseColor("#283593"),
                primaryContainer = Color.parseColor("#E8EAF6"),
                onPrimary = Color.WHITE,
                textPrimary = Color.parseColor("#1A1A2E"),
                textSecondary = Color.parseColor("#5F6368"),
                textHint = Color.parseColor("#9E9E9E"),
                error = Color.parseColor("#C62828"),
                warning = Color.parseColor("#E65100"),
                warningContainer = Color.parseColor("#FFF3E0"),
                warningDark = Color.parseColor("#BF360C"),
                divider = Color.parseColor("#E0E0E0"),
                numpadButton = Color.parseColor("#F0F0F5"),
                numpadText = Color.parseColor("#1A1A2E"),
                pinDotFilled = Color.parseColor("#283593"),
                pinDotEmpty = Color.parseColor("#D0D0D0")
            )
        }
    }

    fun showProgress(status: String = "Processing...", step: String = "Please wait") {
        handler.post {
            amountCountdownTimer?.cancel()
            amountCountdownTimer = null
            pinCountdownTimer?.cancel()
            pinCountdownTimer = null
            val svc = UssdAccessibilityService.getInstance() ?: run {
                Log.w(TAG, "No accessibility service instance for overlay")
                return@post
            }
            currentStatus = status
            currentStep = step
            isPinMode = false
            isAmountMode = false
            removeMini(svc)
            removeOverlay(svc)
            val wm = svc.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            val view = createProgressView(svc, status, step)
            val params = createLayoutParams()

            try {
                wm.addView(view, params)
                overlayView = view
                Log.d(TAG, "Progress overlay shown")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show progress overlay", e)
            }
        }
    }

    fun showPinEntry(message: String, wrongAttempts: Int = 0, maxAttempts: Int = 3, payeeName: String? = null, amount: String? = null, resetTimer: Boolean = true) {
        handler.post {
            val svc = UssdAccessibilityService.getInstance() ?: return@post
            isPinMode = true
            isAmountMode = false
            pinMessage = message
            currentWrongAttempts = wrongAttempts
            currentMaxAttempts = maxAttempts
            pinPayeeName = payeeName ?: amountVerifiedName
            pinAmount = amount ?: UssdManager.lastSubmittedAmount.ifEmpty { null }
            if (resetTimer) pinCountdownRemainingMs = 60_000L
            removeMini(svc)
            removeOverlay(svc)

            val wm = svc.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            val view = createPinView(svc, message, wrongAttempts, maxAttempts)
            val params = createLayoutParams()

            try {
                wm.addView(view, params)
                overlayView = view
                Log.d(TAG, "PIN overlay shown (wrongAttempts=$wrongAttempts)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show PIN overlay", e)
            }
        }
    }

    fun showAmountEntry(verifiedName: String?, upiId: String, resetTimer: Boolean = true) {
        handler.post {
            val svc = UssdAccessibilityService.getInstance() ?: return@post
            isPinMode = false
            isAmountMode = true
            amountVerifiedName = verifiedName
            amountMessage = upiId
            if (resetTimer) amountCountdownRemainingMs = 60_000L
            removeMini(svc)
            removeOverlay(svc)

            val wm = svc.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            val view = createAmountView(svc, verifiedName, upiId)
            val params = createFocusableLayoutParams()

            try {
                wm.addView(view, params)
                overlayView = view
                Log.d(TAG, "Amount overlay shown for $verifiedName ($upiId)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show amount overlay", e)
            }
        }
    }

    fun updateStatus(status: String, step: String = "") {
        handler.post {
            currentStatus = status
            if (step.isNotEmpty()) currentStep = step
            statusText?.text = status
            if (step.isNotEmpty()) stepText?.text = step
        }
    }

    fun hide() {
        handler.post {
            amountCountdownTimer?.cancel()
            amountCountdownTimer = null
            pinCountdownTimer?.cancel()
            pinCountdownTimer = null
            val svc = UssdAccessibilityService.getInstance()
            if (svc != null) {
                removeOverlay(svc)
                removeMini(svc)
            } else {
                overlayView = null
                miniView = null
                statusText = null
                stepText = null
            }
            isPinMode = false
            isAmountMode = false
            Log.d(TAG, "Overlay hidden")
        }
    }

    private fun collapse() {
        val svc = UssdAccessibilityService.getInstance() ?: return
        removeOverlay(svc)
        if (miniView != null) return
        val wm = svc.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val pill = createMiniPill(svc)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(svc, 48)
        }
        try {
            wm.addView(pill, params)
            miniView = pill
            Log.d(TAG, "Mini pill shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show mini pill", e)
        }
    }

    private fun expand() {
        if (isPinMode) {
            showPinEntry(pinMessage, currentWrongAttempts, currentMaxAttempts, pinPayeeName, pinAmount, resetTimer = false)
        } else if (isAmountMode) {
            showAmountEntry(amountVerifiedName, amountMessage, resetTimer = false)
        } else {
            showProgress(currentStatus, currentStep)
        }
    }

    private fun removeOverlay(svc: UssdAccessibilityService) {
        overlayView?.let { view ->
            try {
                val wm = svc.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeView(view)
            } catch (_: Exception) {}
        }
        overlayView = null
        statusText = null
        stepText = null
    }

    private fun removeMini(svc: UssdAccessibilityService) {
        miniView?.let { view ->
            try {
                val wm = svc.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeView(view)
            } catch (_: Exception) {}
        }
        miniView = null
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun createFocusableLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else dp(context, 24)
    }

    private fun createProgressView(context: Context, status: String, step: String): View {
        val t = resolveTheme(context)
        val statusBarH = getStatusBarHeight(context)

        val container = FrameLayout(context).apply {
            setBackgroundColor(t.background)
            setPadding(0, statusBarH, 0, 0)
        }

        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), 0)
        }
        topBar.addView(TextView(context).apply {
            text = "Minimize"
            textSize = 14f
            setTextColor(t.primary)
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            setOnClickListener { collapse() }
        })
        container.addView(topBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP })

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 32), dp(context, 32), dp(context, 32), dp(context, 32))
        }

        statusText = TextView(context).apply {
            text = status
            textSize = 18f
            setTextColor(t.textPrimary)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 20), 0, dp(context, 16))
        }
        content.addView(statusText)

        content.addView(ProgressBar(context).apply {
            isIndeterminate = true
            setPadding(0, 0, 0, dp(context, 16))
        })

        stepText = TextView(context).apply {
            text = ""
            textSize = 14f
            setTextColor(t.textSecondary)
            gravity = Gravity.CENTER
        }
        content.addView(stepText)

        content.addView(TextView(context).apply {
            text = ""
            textSize = 12f
            setTextColor(t.textHint)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 32), 0, 0)
            visibility = View.GONE
        })

        content.addView(TextView(context).apply {
            text = "Cancel Payment"
            textSize = 14f
            setTextColor(t.error)
            gravity = Gravity.CENTER
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            setOnClickListener {
                UssdManager.requestCancel()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 16)
            }
        })

        container.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        return container
    }

    private fun createMiniPill(context: Context): View {
        val t = resolveTheme(context)

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
            val gd = GradientDrawable().apply {
                setColor(t.primary)
                cornerRadius = dp(context, 24).toFloat()
            }
            background = gd
            elevation = dp(context, 6).toFloat()
        }

        pill.addView(ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)).apply {
                marginEnd = dp(context, 8)
            }
        })

        pill.addView(TextView(context).apply {
            text = "Payment in progress"
            textSize = 13f
            setTextColor(t.onPrimary)
            typeface = Typeface.DEFAULT_BOLD
        })

        pill.addView(TextView(context).apply {
            text = " | Expand"
            textSize = 13f
            setTextColor(Color.argb(200, Color.red(t.onPrimary), Color.green(t.onPrimary), Color.blue(t.onPrimary)))
            setPadding(dp(context, 4), 0, 0, 0)
        })

        pill.setOnClickListener { expand() }
        return pill
    }

    private fun createPinView(context: Context, message: String, wrongAttempts: Int = 0, maxAttempts: Int = 3): View {
        val t = resolveTheme(context)
        val statusBarH = getStatusBarHeight(context)

        val container = ScrollView(context).apply {
            setBackgroundColor(t.background)
            isFillViewport = true
            setPadding(0, statusBarH, 0, 0)
        }

        val outerWrap = FrameLayout(context)

        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), 0)
        }
        topBar.addView(TextView(context).apply {
            text = "Minimize"
            textSize = 14f
            setTextColor(t.primary)
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            setOnClickListener { collapse() }
        })
        outerWrap.addView(topBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP })

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(context, 24), dp(context, 40), dp(context, 24), dp(context, 16))
        }

        content.addView(TextView(context).apply {
            text = "Enter UPI PIN"
            textSize = 20f
            setTextColor(t.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 16), 0, dp(context, 4))
        })
        content.addView(TextView(context).apply {
            text = "Authorize this transaction"
            textSize = 13f
            setTextColor(t.textSecondary)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(context, 8))
        })

        // 60s countdown timer
        val pinTimerText = TextView(context).apply {
            text = "Session expires in 60s"
            textSize = 13f
            setTextColor(t.warning)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 4), 0, 0)
        }
        content.addView(pinTimerText)

        pinCountdownTimer?.cancel()
        pinCountdownTimer = object : CountDownTimer(pinCountdownRemainingMs, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                pinCountdownRemainingMs = millisUntilFinished
                val secs = (millisUntilFinished / 1000).toInt()
                handler.post {
                    pinTimerText.text = "Session expires in ${secs}s"
                    if (secs <= 10) {
                        pinTimerText.setTextColor(t.error)
                    }
                }
            }
            override fun onFinish() {
                handler.post {
                    pinTimerText.text = "Session expired"
                    pinTimerText.setTextColor(t.error)
                }
            }
        }.start()

        // Transaction summary card
        if (pinPayeeName != null || pinAmount != null) {
            val summaryCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val gd = GradientDrawable().apply {
                    setColor(t.primaryContainer)
                    cornerRadius = dp(context, 16).toFloat()
                }
                background = gd
                setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 16))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 12)
                    bottomMargin = dp(context, 8)
                }
            }
            if (pinAmount != null) {
                summaryCard.addView(TextView(context).apply {
                    text = "₹$pinAmount"
                    textSize = 22f
                    setTextColor(t.textPrimary)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                })
            }
            if (pinPayeeName != null) {
                summaryCard.addView(TextView(context).apply {
                    text = "to $pinPayeeName"
                    textSize = 14f
                    setTextColor(t.textSecondary)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(context, 4), 0, 0)
                })
            }
            content.addView(summaryCard)
        }

        // Wrong PIN error banner
        if (wrongAttempts > 0) {
            val errorBanner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val gd = GradientDrawable().apply {
                    setColor(t.warningContainer)
                    cornerRadius = dp(context, 12).toFloat()
                    setStroke(dp(context, 1), Color.parseColor("#FF9800"))
                }
                background = gd
                setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(context, 16) }
            }
            errorBanner.addView(TextView(context).apply {
                text = "\u26A0 Wrong PIN entered $wrongAttempts/$maxAttempts times"
                textSize = 15f
                setTextColor(t.warning)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })
            val remaining = maxAttempts - wrongAttempts
            errorBanner.addView(TextView(context).apply {
                text = if (remaining == 1) {
                    "LAST ATTEMPT! Your UPI will be suspended for 24 hours after $maxAttempts wrong attempts."
                } else {
                    "$remaining attempts remaining. UPI will be suspended for 24 hours after $maxAttempts wrong attempts."
                }
                textSize = 12f
                setTextColor(t.warningDark)
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 4), 0, 0)
            })
            content.addView(errorBanner)
        }

        // PIN dots display
        var currentPin = ""
        val pinDotsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 8), 0, dp(context, 8))
        }
        val pinDots = mutableListOf<View>()
        for (i in 0 until 6) {
            val dot = View(context).apply {
                val gd = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(t.pinDotEmpty)
                    setSize(dp(context, 14), dp(context, 14))
                }
                background = gd
                layoutParams = LinearLayout.LayoutParams(dp(context, 14), dp(context, 14)).apply {
                    marginStart = dp(context, 8)
                    marginEnd = dp(context, 8)
                }
            }
            pinDots.add(dot)
            pinDotsContainer.addView(dot)
        }
        content.addView(pinDotsContainer)

        val errorText = TextView(context).apply {
            text = ""
            textSize = 12f
            setTextColor(t.error)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 4), 0, dp(context, 4))
        }
        content.addView(errorText)

        fun updateDots() {
            for (i in pinDots.indices) {
                val filled = i < currentPin.length
                val gd = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (filled) t.pinDotFilled else t.pinDotEmpty)
                    setSize(dp(context, 14), dp(context, 14))
                }
                pinDots[i].background = gd
            }
        }

        // Custom numpad
        val numpadContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), 0)
        }

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )

        for (row in keys) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(context, 6) }
            }
            for (key in row) {
                val btnSize = dp(context, 64)
                val btn = TextView(context).apply {
                    text = key
                    textSize = 22f
                    setTextColor(if (key == "⌫") t.error else t.numpadText)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                        marginStart = dp(context, 6)
                        marginEnd = dp(context, 6)
                    }
                    if (key.isNotEmpty()) {
                        val gd = GradientDrawable().apply {
                            setColor(t.numpadButton)
                            cornerRadius = dp(context, 32).toFloat()
                        }
                        background = gd
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            when (key) {
                                "⌫" -> {
                                    if (currentPin.isNotEmpty()) {
                                        currentPin = currentPin.dropLast(1)
                                        updateDots()
                                        errorText.text = ""
                                    }
                                }
                                else -> {
                                    if (currentPin.length < 6) {
                                        currentPin += key
                                        updateDots()
                                        errorText.text = ""
                                    }
                                }
                            }
                        }
                    }
                }
                rowLayout.addView(btn)
            }
            numpadContainer.addView(rowLayout)
        }
        content.addView(numpadContainer)

        // Action buttons
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), 0)
        }

        val cancelBtn = TextView(context).apply {
            text = "Cancel"
            textSize = 15f
            setTextColor(t.textSecondary)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val gd = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(context, 14).toFloat()
                setStroke(dp(context, 1), t.divider)
            }
            background = gd
            setPadding(dp(context, 28), dp(context, 14), dp(context, 28), dp(context, 14))
            setOnClickListener {
                UssdManager.requestCancel()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 8)
            }
        }
        buttonRow.addView(cancelBtn)

        val confirmBtn = TextView(context).apply {
            text = "Confirm & Pay"
            textSize = 15f
            setTextColor(t.onPrimary)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val gd = GradientDrawable().apply {
                setColor(t.primary)
                cornerRadius = dp(context, 14).toFloat()
            }
            background = gd
            setPadding(dp(context, 28), dp(context, 14), dp(context, 28), dp(context, 14))
            setOnClickListener {
                if (currentPin.length < 4) {
                    errorText.text = "PIN must be 4-6 digits"
                    return@setOnClickListener
                }
                showProgress("Verifying PIN...", "Please wait")
                UssdManager.sendPinResponse(currentPin)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(context, 8)
            }
        }
        buttonRow.addView(confirmBtn)

        content.addView(buttonRow)
        outerWrap.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })
        container.addView(outerWrap)

        return container
    }

    private fun createAmountView(context: Context, verifiedName: String?, upiId: String): View {
        val t = resolveTheme(context)
        val statusBarH = getStatusBarHeight(context)

        val container = ScrollView(context).apply {
            setBackgroundColor(t.background)
            isFillViewport = true
            setPadding(0, statusBarH, 0, 0)
        }

        val outerWrap = FrameLayout(context)

        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), 0)
        }
        topBar.addView(TextView(context).apply {
            text = "Minimize"
            textSize = 14f
            setTextColor(t.primary)
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
            setOnClickListener { collapse() }
        })
        outerWrap.addView(topBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP })

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(context, 24), dp(context, 40), dp(context, 24), dp(context, 16))
        }

        // 60s countdown timer
        val timerText = TextView(context).apply {
            text = "Session expires in 60s"
            textSize = 13f
            setTextColor(t.warning)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 8), 0, 0)
        }
        content.addView(timerText)

        amountCountdownTimer?.cancel()
        amountCountdownTimer = object : CountDownTimer(amountCountdownRemainingMs, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                amountCountdownRemainingMs = millisUntilFinished
                val secs = (millisUntilFinished / 1000).toInt()
                handler.post {
                    timerText.text = "Session expires in ${secs}s"
                    if (secs <= 10) {
                        timerText.setTextColor(t.error)
                    }
                }
            }
            override fun onFinish() {
                handler.post {
                    timerText.text = "Session expired"
                    timerText.setTextColor(t.error)
                }
            }
        }.start()

        // Verified recipient info
        val recipientCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val gd = GradientDrawable().apply {
                setColor(t.primaryContainer)
                cornerRadius = dp(context, 16).toFloat()
            }
            background = gd
            setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 12)
                bottomMargin = dp(context, 16)
            }
        }
        recipientCard.addView(TextView(context).apply {
            text = verifiedName ?: upiId
            textSize = 18f
            setTextColor(t.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        if (verifiedName != null) {
            recipientCard.addView(TextView(context).apply {
                text = upiId
                textSize = 13f
                setTextColor(t.textSecondary)
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 2), 0, 0)
            })
        }
        content.addView(recipientCard)

        // Amount input field
        content.addView(TextView(context).apply {
            text = "Amount (₹)"
            textSize = 14f
            setTextColor(t.textSecondary)
            setPadding(dp(context, 4), 0, 0, dp(context, 4))
        })

        val amountInput = EditText(context).apply {
            hint = "Enter amount"
            textSize = 20f
            setTextColor(t.textPrimary)
            setHintTextColor(t.textHint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            filters = arrayOf(InputFilter.LengthFilter(10))
            val gd = GradientDrawable().apply {
                setColor(t.numpadButton)
                cornerRadius = dp(context, 14).toFloat()
                setStroke(dp(context, 1), t.divider)
            }
            background = gd
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        content.addView(amountInput)

        // Remarks input field
        content.addView(TextView(context).apply {
            text = "Remarks (optional)"
            textSize = 14f
            setTextColor(t.textSecondary)
            setPadding(dp(context, 4), dp(context, 16), 0, dp(context, 4))
        })

        val remarksInput = EditText(context).apply {
            hint = "Add a note"
            textSize = 16f
            setTextColor(t.textPrimary)
            setHintTextColor(t.textHint)
            inputType = InputType.TYPE_CLASS_TEXT
            filters = arrayOf(InputFilter.LengthFilter(30))
            val gd = GradientDrawable().apply {
                setColor(t.numpadButton)
                cornerRadius = dp(context, 14).toFloat()
                setStroke(dp(context, 1), t.divider)
            }
            background = gd
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        content.addView(remarksInput)

        val errorText = TextView(context).apply {
            text = ""
            textSize = 12f
            setTextColor(t.error)
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 8), 0, dp(context, 4))
        }
        content.addView(errorText)

        // Action buttons
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), 0)
        }

        val cancelBtn = TextView(context).apply {
            text = "Cancel"
            textSize = 15f
            setTextColor(t.textSecondary)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val gd = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(context, 14).toFloat()
                setStroke(dp(context, 1), t.divider)
            }
            background = gd
            setPadding(dp(context, 28), dp(context, 14), dp(context, 28), dp(context, 14))
            setOnClickListener {
                amountCountdownTimer?.cancel()
                amountCountdownTimer = null
                UssdManager.requestCancel()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 8)
            }
        }
        buttonRow.addView(cancelBtn)

        val confirmBtn = TextView(context).apply {
            text = "Continue"
            textSize = 15f
            setTextColor(t.onPrimary)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val gd = GradientDrawable().apply {
                setColor(t.primary)
                cornerRadius = dp(context, 14).toFloat()
            }
            background = gd
            setPadding(dp(context, 28), dp(context, 14), dp(context, 28), dp(context, 14))
            setOnClickListener {
                val amount = amountInput.text.toString().trim()
                if (amount.isEmpty() || amount == "." || (amount.toDoubleOrNull() ?: 0.0) <= 0) {
                    errorText.text = "Enter a valid amount"
                    return@setOnClickListener
                }
                val remarks = remarksInput.text.toString().trim()
                amountCountdownTimer?.cancel()
                amountCountdownTimer = null
                showProgress("Sending amount...", "Please wait")
                UssdManager.continueSession(amount, remarks.ifEmpty { "1" })
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(context, 8)
            }
        }
        buttonRow.addView(confirmBtn)

        content.addView(buttonRow)
        outerWrap.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })
        container.addView(outerWrap)

        return container
    }

    private fun dp(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
