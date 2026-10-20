package com.puzzle.sokoban

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets

/**
 * 唯一的 View：管理当前界面、弹窗栈、提示气泡和帧刷新。
 * 所有界面都自绘，以保证在各种机型上风格一致。
 */
class GameView(context: Context) : View(context) {
    val ui = Ui(resources.displayMetrics.density)
    val progress = Progress(context)
    val sound = Sound(context).also { it.enabled = progress.sound }
    /** 系统栏 / 刘海占用的边距 */
    val insets = Rect()

    private var screen: Screen
    private var prevScreen: Screen? = null
    private var switchAt = 0L
    private val dialogs = ArrayList<Dialog>()

    private var toastMsg: String? = null
    private var toastAt = 0L
    private var toastLong = false

    private val bgPaint = Paint()
    private val vignette = Paint()

    init {
        LevelPack.load(context)
        isHapticFeedbackEnabled = true
        screen = MenuScreen(this)
        screen.onEnter()
    }

    override fun onApplyWindowInsets(wi: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 30) {
            val i = wi.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            insets.set(i.left, i.top, i.right, i.bottom)
        } else {
            @Suppress("DEPRECATION")
            insets.set(wi.systemWindowInsetLeft, wi.systemWindowInsetTop, wi.systemWindowInsetRight, wi.systemWindowInsetBottom)
        }
        relayout()
        return wi
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Pal.bgTop, Pal.bgBottom, Shader.TileMode.CLAMP)
        vignette.shader = RadialGradient(
            w / 2f, h * 0.35f, maxOf(w, h) * 0.75f,
            intArrayOf(0x00000000, 0x00000000, 0x55000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        relayout()
    }

    private fun relayout() {
        if (width == 0) return
        screen.layout(width, height)
        for (d in dialogs) d.layout(width, height)
        invalidate()
    }

    fun show(next: Screen) {
        screen.onLeave()
        prevScreen = screen
        switchAt = SystemClock.uptimeMillis()
        screen = next
        if (width > 0) next.layout(width, height)
        next.onEnter()
        invalidate()
    }

    fun showDialog(d: Dialog) {
        dialogs.add(d)
        if (width > 0) d.layout(width, height)
        invalidate()
    }

    fun closeDialog(d: Dialog) {
        dialogs.remove(d)
        invalidate()
    }

    fun hasDialog() = dialogs.isNotEmpty()

    fun toast(msg: String, long: Boolean = false) {
        toastMsg = msg
        toastAt = SystemClock.uptimeMillis()
        toastLong = long
        invalidate()
    }

    fun haptic(strong: Boolean = false) {
        if (!progress.haptic) return
        val kind = if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
        performHapticFeedback(kind)
    }

    /** 返回键：先关弹窗，再交给当前界面；返回 false 表示应当退出应用。 */
    fun onBack(): Boolean {
        val top = dialogs.lastOrNull()
        if (top != null) {
            top.onBack()
            invalidate()
            return true
        }
        val handled = screen.onBack()
        invalidate()
        return handled
    }

    fun onPause() {
        screen.onPause()
    }

    fun applySettings() {
        sound.enabled = progress.sound
        relayout()
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignette)

        var anim = false
        val prev = prevScreen
        val k = (now - switchAt) / 260f
        if (prev != null && k < 1f) {
            c.saveLayerAlpha(null, (255 * (1f - k)).toInt())
            prev.draw(c, now)
            c.restore()
            c.saveLayerAlpha(null, (255 * k).toInt())
            screen.draw(c, now)
            c.restore()
            anim = true
        } else {
            prevScreen = null
            screen.draw(c, now)
        }
        anim = anim or screen.animating(now)

        for (d in dialogs.toList()) {
            d.draw(c, now)
            anim = anim or d.animating(now)
        }

        anim = anim or drawToast(c, now)
        if (anim) postInvalidateOnAnimation()
    }

    private fun drawToast(c: Canvas, now: Long): Boolean {
        val msg = toastMsg ?: return false
        val life = if (toastLong) 3200L else 2000L
        val t = now - toastAt
        if (t > life) {
            toastMsg = null
            return false
        }
        val a = when {
            t < 150 -> t / 150f
            t > life - 300 -> (life - t) / 300f
            else -> 1f
        }
        val size = ui.d(15f)
        val maxW = width - ui.d(64f)
        val lines = ui.wrap(msg, size, maxW - ui.d(36f), bold = true)
        val tw = lines.maxOf { ui.measure(it, size) }
        val lh = size * 1.45f
        val bw = tw + ui.d(36f)
        val bh = lines.size * lh + ui.d(20f)
        val cx = width / 2f
        val cy = height - insets.bottom - ui.d(150f) + (1f - a) * ui.d(10f)
        ui.roundRect(c, cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2, ui.d(18f), Ui.withAlpha(0xEE0B121C.toInt(), a))
        for ((i, line) in lines.withIndex()) {
            ui.text(c, line, cx, cy - bh / 2 + ui.d(10f) + lh * (i + 0.5f), size, Ui.withAlpha(Pal.text, a))
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        // 只处理第一根手指
        if (e.actionMasked == MotionEvent.ACTION_POINTER_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_UP) return true
        if (e.actionIndex != 0 && e.actionMasked != MotionEvent.ACTION_MOVE) return true
        val now = SystemClock.uptimeMillis()
        if (prevScreen != null && now - switchAt < 200) return true
        val top = dialogs.lastOrNull()
        if (top != null) top.onTouch(e) else screen.onTouch(e)
        invalidate()
        return true
    }
}
