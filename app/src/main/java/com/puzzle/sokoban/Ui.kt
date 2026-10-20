package com.puzzle.sokoban

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object Pal {
    const val bgTop = 0xFF223249.toInt()
    const val bgBottom = 0xFF111925.toInt()
    const val panel = 0xFF26364C.toInt()
    const val panelLight = 0xFF33465F.toInt()
    const val panelDark = 0xFF1B2738.toInt()
    const val border = 0x26FFFFFF
    const val text = 0xFFF5EFE3.toInt()
    const val textDim = 0xFF9CADC2.toInt()
    const val accent = 0xFFF4AE3D.toInt()
    const val accentDark = 0xFFC98526.toInt()
    const val onAccent = 0xFF3A2306.toInt()
    const val green = 0xFF5DC47D.toInt()
    const val red = 0xFFE8574D.toInt()
    const val star = 0xFFFFC93E.toInt()
    const val starEmpty = 0x33FFFFFF

    const val floorA = 0xFFEADDC2.toInt()
    const val floorB = 0xFFE3D4B6.toInt()
    const val floorLine = 0x14000000
    const val wallTop = 0xFF5B6C86.toInt()
    const val wallFace = 0xFF3B485D.toInt()
    const val wallHi = 0xFF7A8DA9.toInt()
    const val wallSpeck = 0x1A000000
    const val goal = 0xFFE8574D.toInt()
    const val player = 0xFF3F86F0.toInt()
    const val playerLight = 0xFF86BBFF.toInt()
    const val playerDark = 0xFF2A5DB5.toInt()
    const val leaf = 0xFF6CCB5F.toInt()
}

object Icon {
    const val NONE = 0
    const val BACK = 1
    const val UNDO = 2
    const val REDO = 3
    const val RESTART = 4
    const val HINT = 5
    const val GEAR = 6
    const val GRID = 7
    const val LOCK = 8
    const val PLAY = 9
    const val LEFT = 10
    const val RIGHT = 11
    const val UP = 12
    const val DOWN = 13
    const val HELP = 14
    const val NEXT = 15

    private val path = Path()
    private val rect = RectF()

    /** 在以 (cx, cy) 为中心、边长 s 的方框里画图标。 */
    fun draw(c: Canvas, icon: Int, cx: Float, cy: Float, s: Float, color: Int, p: Paint) {
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.11f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.shader = null
        val h = s / 2
        when (icon) {
            BACK, LEFT -> {
                path.reset()
                path.moveTo(cx + h * 0.25f, cy - h * 0.6f)
                path.lineTo(cx - h * 0.35f, cy)
                path.lineTo(cx + h * 0.25f, cy + h * 0.6f)
                c.drawPath(path, p)
            }
            RIGHT -> {
                path.reset()
                path.moveTo(cx - h * 0.25f, cy - h * 0.6f)
                path.lineTo(cx + h * 0.35f, cy)
                path.lineTo(cx - h * 0.25f, cy + h * 0.6f)
                c.drawPath(path, p)
            }
            UNDO, REDO -> {
                c.save()
                if (icon == REDO) c.scale(-1f, 1f, cx, cy)
                path.reset()
                path.moveTo(cx - h * 0.55f, cy - h * 0.2f)
                path.lineTo(cx + h * 0.15f, cy - h * 0.2f)
                rect.set(cx - h * 0.25f, cy - h * 0.2f, cx + h * 0.55f, cy + h * 0.6f)
                path.arcTo(rect, -90f, 180f)
                path.lineTo(cx - h * 0.3f, cy + h * 0.6f)
                c.drawPath(path, p)
                path.reset()
                path.moveTo(cx - h * 0.25f, cy - h * 0.55f)
                path.lineTo(cx - h * 0.6f, cy - h * 0.2f)
                path.lineTo(cx - h * 0.25f, cy + h * 0.15f)
                c.drawPath(path, p)
                c.restore()
            }
            RESTART -> {
                // 顺时针的圆弧，箭头停在右上方
                val rr = h * 0.56f
                rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
                c.drawArc(rect, 20f, 265f, false, p)
                val a = 285.0 * PI / 180
                val ex = cx + rr * cos(a).toFloat()
                val ey = cy + rr * sin(a).toFloat()
                val tx = -sin(a).toFloat()
                val ty = cos(a).toFloat()
                val nx = cos(a).toFloat()
                val ny = sin(a).toFloat()
                p.style = Paint.Style.FILL
                path.reset()
                path.moveTo(ex + tx * h * 0.4f, ey + ty * h * 0.4f)
                path.lineTo(ex - tx * h * 0.05f + nx * h * 0.32f, ey - ty * h * 0.05f + ny * h * 0.32f)
                path.lineTo(ex - tx * h * 0.05f - nx * h * 0.32f, ey - ty * h * 0.05f - ny * h * 0.32f)
                path.close()
                c.drawPath(path, p)
            }
            HINT -> {
                c.drawCircle(cx, cy - h * 0.2f, h * 0.45f, p)
                c.drawLine(cx - h * 0.22f, cy + h * 0.45f, cx + h * 0.22f, cy + h * 0.45f, p)
                c.drawLine(cx - h * 0.14f, cy + h * 0.7f, cx + h * 0.14f, cy + h * 0.7f, p)
                p.strokeWidth = s * 0.07f
                c.drawLine(cx, cy - h * 0.05f, cx, cy + h * 0.2f, p)
            }
            GEAR -> {
                p.style = Paint.Style.FILL
                for (k in 0 until 8) {
                    val a = k * PI / 4
                    val x = cx + h * 0.62f * cos(a).toFloat()
                    val y = cy + h * 0.62f * sin(a).toFloat()
                    c.drawCircle(x, y, h * 0.17f, p)
                }
                p.style = Paint.Style.STROKE
                p.strokeWidth = h * 0.3f
                c.drawCircle(cx, cy, h * 0.42f, p)
            }
            GRID -> {
                p.style = Paint.Style.FILL
                val g = h * 0.12f
                val q = h * 0.5f
                for (ix in 0..1) for (iy in 0..1) {
                    val l = cx - q - g / 2 + ix * (q + g)
                    val t = cy - q - g / 2 + iy * (q + g)
                    rect.set(l, t, l + q, t + q)
                    c.drawRoundRect(rect, q * 0.25f, q * 0.25f, p)
                }
            }
            LOCK -> {
                rect.set(cx - h * 0.32f, cy - h * 0.7f, cx + h * 0.32f, cy + h * 0.05f)
                c.drawArc(rect, 180f, 180f, false, p)
                c.drawLine(cx - h * 0.32f, cy - h * 0.32f, cx - h * 0.32f, cy - h * 0.05f, p)
                c.drawLine(cx + h * 0.32f, cy - h * 0.32f, cx + h * 0.32f, cy - h * 0.05f, p)
                p.style = Paint.Style.FILL
                rect.set(cx - h * 0.55f, cy - h * 0.1f, cx + h * 0.55f, cy + h * 0.65f)
                c.drawRoundRect(rect, h * 0.12f, h * 0.12f, p)
            }
            PLAY, NEXT -> {
                p.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx - h * 0.35f, cy - h * 0.55f)
                path.lineTo(cx + h * 0.55f, cy)
                path.lineTo(cx - h * 0.35f, cy + h * 0.55f)
                path.close()
                c.drawPath(path, p)
                if (icon == NEXT) {
                    rect.set(cx + h * 0.5f, cy - h * 0.55f, cx + h * 0.68f, cy + h * 0.55f)
                    c.drawRoundRect(rect, h * 0.05f, h * 0.05f, p)
                }
            }
            UP, DOWN -> {
                val m = if (icon == UP) -1f else 1f
                p.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx - h * 0.55f, cy - m * h * 0.3f)
                path.lineTo(cx, cy + m * h * 0.4f)
                path.lineTo(cx + h * 0.55f, cy - m * h * 0.3f)
                path.close()
                c.drawPath(path, p)
            }
            HELP -> {
                c.drawCircle(cx, cy, h * 0.72f, p)
                p.style = Paint.Style.FILL
                p.textAlign = Paint.Align.CENTER
                p.textSize = s * 0.7f
                p.typeface = Typeface.DEFAULT_BOLD
                c.drawText("?", cx, cy + s * 0.25f, p)
            }
        }
        p.style = Paint.Style.FILL
    }

    /** 五角星，填充 */
    fun star(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, p: Paint) {
        path.reset()
        for (k in 0 until 10) {
            val rr = if (k % 2 == 0) r else r * 0.45f
            val a = -PI / 2 + k * PI / 5
            val x = cx + rr * cos(a).toFloat()
            val y = cy + rr * sin(a).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = color
        c.drawPath(path, p)
    }
}

/** 共享的画笔与文字工具 */
class Ui(val dp: Float) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val icon = Paint(Paint.ANTI_ALIAS_FLAG)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val rect = RectF()
    private val fm = Paint.FontMetrics()

    fun d(v: Float) = v * dp

    fun text(
        c: Canvas, s: String, x: Float, cy: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.CENTER, bold: Boolean = true,
    ) {
        text.textSize = size
        text.color = color
        text.textAlign = align
        text.isFakeBoldText = false
        text.typeface = if (bold) BOLD else NORMAL
        text.getFontMetrics(fm)
        c.drawText(s, x, cy - (fm.ascent + fm.descent) / 2, text)
    }

    fun measure(s: String, size: Float, bold: Boolean = true): Float {
        text.textSize = size
        text.typeface = if (bold) BOLD else NORMAL
        return text.measureText(s)
    }

    /** 按宽度折行（逐字符，适合中文），保留原有换行。 */
    fun wrap(s: String, size: Float, maxW: Float, bold: Boolean = false): List<String> {
        text.textSize = size
        text.typeface = if (bold) BOLD else NORMAL
        val out = ArrayList<String>()
        for (para in s.split('\n')) {
            var start = 0
            while (start < para.length) {
                var end = start + 1
                while (end <= para.length && text.measureText(para, start, end) <= maxW) end++
                end = (end - 1).coerceAtLeast(start + 1)
                // 避头：标点不放在行首，挤到上一行末尾
                while (end < para.length && para[end] in NO_LINE_START) end++
                out.add(para.substring(start, end))
                start = end
            }
            if (para.isEmpty()) out.add("")
        }
        return out
    }

    fun roundRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        fill.shader = null
        fill.style = Paint.Style.FILL
        fill.color = color
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, rad, rad, fill)
    }

    fun panel(c: Canvas, r: RectF, rad: Float, color: Int = Pal.panel) {
        fill.shader = null
        fill.style = Paint.Style.FILL
        fill.color = 0x40000000
        rect.set(r.left, r.top + d(4f), r.right, r.bottom + d(4f))
        c.drawRoundRect(rect, rad, rad, fill)
        fill.color = color
        c.drawRoundRect(r, rad, rad, fill)
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = d(1f)
        fill.color = Pal.border
        c.drawRoundRect(r, rad, rad, fill)
        fill.style = Paint.Style.FILL
    }

    /** 拨动开关 */
    fun toggle(c: Canvas, cx: Float, cy: Float, on: Float) {
        val w = d(46f)
        val h = d(26f)
        val bg = blend(0xFF4A5A70.toInt(), Pal.green, on)
        roundRect(c, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, h / 2, bg)
        fill.color = 0xFFFFFFFF.toInt()
        val kx = cx - w / 2 + h / 2 + (w - h) * on
        c.drawCircle(kx, cy, h / 2 - d(3f), fill)
    }

    companion object {
        val BOLD: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val NORMAL: Typeface = Typeface.DEFAULT
        private const val NO_LINE_START = "，。、；：！？）」』”’,.;:!?)%"

        fun blend(a: Int, b: Int, t: Float): Int {
            val k = t.coerceIn(0f, 1f)
            fun ch(sh: Int) = (((a shr sh) and 0xFF) * (1 - k) + ((b shr sh) and 0xFF) * k).toInt() shl sh
            return ch(24) or ch(16) or ch(8) or ch(0)
        }

        fun withAlpha(color: Int, alpha: Float) =
            (color and 0x00FFFFFF) or ((((color ushr 24) and 0xFF) * alpha.coerceIn(0f, 1f)).toInt() shl 24)

        fun easeOutBack(t: Float): Float {
            val x = t.coerceIn(0f, 1f) - 1f
            return 1f + x * x * (2.70158f * x + 1.70158f)
        }

        fun easeOut(t: Float): Float {
            val x = 1f - t.coerceIn(0f, 1f)
            return 1f - x * x * x
        }
    }
}

class Button(
    var label: String = "",
    var icon: Int = Icon.NONE,
    var kind: Int = NORMAL,
    val onClick: () -> Unit,
) {
    companion object {
        const val NORMAL = 0
        const val PRIMARY = 1
        const val TOOL = 2
        const val ICON = 3
        const val DANGER = 4
        const val DPAD = 5
    }

    val r = RectF()
    var pressed = false
    var enabled = true
    var visible = true
    /** 按住连发（撤销、方向键） */
    var repeat = false
    /** 当前这次 onClick 是否由连发触发 */
    var repeating = false
    var busy = false
    internal var downAt = 0L
    internal var lastFire = 0L

    fun hit(x: Float, y: Float, slop: Float) =
        visible && x >= r.left - slop && x <= r.right + slop && y >= r.top - slop && y <= r.bottom + slop

    fun draw(c: Canvas, ui: Ui, now: Long) {
        if (!visible) return
        val alpha = if (enabled) 1f else 0.35f
        val sc = if (pressed) 0.95f else 1f
        c.save()
        c.scale(sc, sc, r.centerX(), r.centerY())
        val rad = min(r.height() / 2, ui.d(16f))
        when (kind) {
            PRIMARY, NORMAL, DANGER -> {
                val bg = when (kind) {
                    PRIMARY -> if (pressed) Pal.accentDark else Pal.accent
                    DANGER -> if (pressed) 0xFFB8443C.toInt() else Pal.red
                    else -> if (pressed) Pal.panel else Pal.panelLight
                }
                ui.fill.color = 0x40000000
                ui.rect.set(r.left, r.top + ui.d(3f), r.right, r.bottom + ui.d(3f))
                ui.fill.alpha = (64 * alpha).toInt()
                c.drawRoundRect(ui.rect, rad, rad, ui.fill)
                ui.roundRect(c, r.left, r.top, r.right, r.bottom, rad, Ui.withAlpha(bg, alpha))
                if (kind != PRIMARY) {
                    ui.fill.style = Paint.Style.STROKE
                    ui.fill.strokeWidth = ui.d(1f)
                    ui.fill.color = Ui.withAlpha(Pal.border, alpha)
                    c.drawRoundRect(r, rad, rad, ui.fill)
                    ui.fill.style = Paint.Style.FILL
                }
                val fg = Ui.withAlpha(if (kind == PRIMARY) Pal.onAccent else Pal.text, alpha)
                val ts = min(r.height() * 0.36f, ui.d(18f))
                if (icon != Icon.NONE && label.isNotEmpty()) {
                    val iw = ts * 1.1f
                    val tw = ui.measure(label, ts)
                    val total = iw + ui.d(8f) + tw
                    val x0 = r.centerX() - total / 2
                    Icon.draw(c, icon, x0 + iw / 2, r.centerY(), iw, fg, ui.icon)
                    ui.text(c, label, x0 + iw + ui.d(8f), r.centerY(), ts, fg, Paint.Align.LEFT)
                } else if (icon != Icon.NONE) {
                    Icon.draw(c, icon, r.centerX(), r.centerY(), ts * 1.3f, fg, ui.icon)
                } else {
                    ui.text(c, label, r.centerX(), r.centerY(), ts, fg)
                }
            }
            TOOL -> {
                if (pressed) ui.roundRect(c, r.left, r.top, r.right, r.bottom, ui.d(14f), 0x22FFFFFF)
                val fg = Ui.withAlpha(Pal.text, alpha)
                val isz = min(r.width(), r.height()) * 0.36f
                val iy = r.top + r.height() * 0.38f
                if (busy) {
                    // 思考中：旋转的圆弧
                    ui.icon.style = Paint.Style.STROKE
                    ui.icon.strokeWidth = isz * 0.12f
                    ui.icon.color = Pal.accent
                    ui.rect.set(r.centerX() - isz / 2, iy - isz / 2, r.centerX() + isz / 2, iy + isz / 2)
                    c.drawArc(ui.rect, (now % 1000) * 0.36f, 270f, false, ui.icon)
                    ui.icon.style = Paint.Style.FILL
                } else {
                    Icon.draw(c, icon, r.centerX(), iy, isz, fg, ui.icon)
                }
                ui.text(c, label, r.centerX(), r.top + r.height() * 0.78f, ui.d(12f), Ui.withAlpha(Pal.textDim, alpha))
            }
            ICON -> {
                val bg = if (pressed) 0x33FFFFFF else 0x14FFFFFF
                c.drawCircle(r.centerX(), r.centerY(), r.height() / 2, ui.fill.apply { color = bg; shader = null })
                Icon.draw(c, icon, r.centerX(), r.centerY(), r.height() * 0.5f, Ui.withAlpha(Pal.text, alpha), ui.icon)
            }
            DPAD -> {
                ui.roundRect(c, r.left, r.top, r.right, r.bottom, ui.d(14f), if (pressed) 0x44FFFFFF else 0x1FFFFFFF)
                Icon.draw(c, icon, r.centerX(), r.centerY(), r.height() * 0.45f, Ui.withAlpha(Pal.text, 0.85f), ui.icon)
            }
        }
        c.restore()
    }
}

/** 一组按钮的触摸分发：按下高亮、移出取消、抬起触发；可选按住连发。 */
class ButtonSet(private val view: GameView) {
    val list = ArrayList<Button>()
    private var active: Button? = null

    fun add(b: Button): Button {
        list.add(b)
        return b
    }

    val tracking get() = active != null

    fun onTouch(e: MotionEvent): Boolean {
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val b = list.lastOrNull { it.enabled && it.hit(x, y, view.ui.d(4f)) } ?: return false
                active = b
                b.pressed = true
                b.downAt = SystemClock.uptimeMillis()
                if (b.repeat) {
                    b.lastFire = b.downAt
                    fire(b, false)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val b = active ?: return false
                b.pressed = b.hit(x, y, view.ui.d(16f))
                return true
            }
            MotionEvent.ACTION_UP -> {
                val b = active ?: return false
                active = null
                if (b.pressed && !b.repeat && b.enabled) fire(b, false)
                b.pressed = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                active?.pressed = false
                active = null
            }
        }
        return false
    }

    /** 每帧调用，处理按住连发。 */
    fun tick(now: Long): Boolean {
        val b = active ?: return false
        if (b.repeat && b.pressed && b.enabled && now - b.downAt > 380 && now - b.lastFire > 110) {
            b.lastFire = now
            fire(b, true)
        }
        return b.repeat && b.pressed
    }

    private fun fire(b: Button, repeat: Boolean) {
        if (!repeat && b.kind != Button.DPAD) {
            view.sound.play(Sound.CLICK, 0.6f)
            view.haptic()
        }
        b.repeating = repeat
        b.onClick()
        b.repeating = false
    }

    fun draw(c: Canvas, ui: Ui, now: Long) {
        for (b in list) b.draw(c, ui, now)
    }
}

abstract class Screen(val view: GameView) {
    val ui get() = view.ui
    fun d(v: Float) = v * view.ui.dp
    abstract fun layout(w: Int, h: Int)
    abstract fun draw(c: Canvas, now: Long)
    abstract fun onTouch(e: MotionEvent)
    /** 返回 true 表示已处理返回键 */
    open fun onBack(): Boolean = false
    /** 是否还需要继续刷新下一帧 */
    open fun animating(now: Long): Boolean = false
    open fun onEnter() {}
    open fun onLeave() {}
    open fun onPause() {}
}

/** 居中弹出的对话框基类 */
abstract class Dialog(val view: GameView) {
    val ui get() = view.ui
    fun d(v: Float) = v * view.ui.dp
    val panel = RectF()
    val buttons = ButtonSet(view)
    val openedAt = SystemClock.uptimeMillis()
    open val cancelable = true
    var w = 0
    var h = 0

    open fun layout(w: Int, h: Int) {
        this.w = w
        this.h = h
    }

    abstract fun drawContent(c: Canvas, now: Long)

    fun draw(c: Canvas, now: Long) {
        val t = ((now - openedAt) / 220f).coerceIn(0f, 1f)
        ui.fill.shader = null
        ui.fill.color = Ui.withAlpha(0xB0050A12.toInt(), Ui.easeOut(t))
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), ui.fill)
        val s = 0.88f + 0.12f * Ui.easeOutBack(t)
        c.save()
        c.scale(s, s, panel.centerX(), panel.centerY())
        if (t < 1f) c.saveLayerAlpha(null, (255 * Ui.easeOut(t)).toInt())
        ui.panel(c, panel, d(22f))
        drawContent(c, now)
        buttons.draw(c, ui, now)
        if (t < 1f) c.restore()
        c.restore()
    }

    open fun animating(now: Long) = now - openedAt < 400 || buttons.tracking

    open fun onTouch(e: MotionEvent) {
        if (buttons.onTouch(e)) return
        if (e.actionMasked == MotionEvent.ACTION_UP && cancelable && !panel.contains(e.x, e.y)) dismiss()
    }

    open fun onBack() {
        if (cancelable) dismiss()
    }

    fun dismiss() = view.closeDialog(this)

    /** 底部一排按钮的布局 */
    fun layoutButtonRow(bs: List<Button>, bottom: Float, height: Float = d(48f)) {
        val gap = d(10f)
        val l = panel.left + d(20f)
        val r = panel.right - d(20f)
        val bw = (r - l - gap * (bs.size - 1)) / bs.size
        for ((i, b) in bs.withIndex()) {
            val x = l + i * (bw + gap)
            b.r.set(x, bottom - height, x + bw, bottom)
        }
    }
}
