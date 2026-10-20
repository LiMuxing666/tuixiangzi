package com.puzzle.sokoban

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class LevelSelectScreen(view: GameView) : Screen(view) {
    private val buttons = ButtonSet(view)
    private val backBtn = buttons.add(Button(icon = Icon.BACK, kind = Button.ICON) { view.show(MenuScreen(view)) })

    private val scroller = OverScroller(view.context)
    private var velocity: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop

    private var w = 0
    private var h = 0
    private var headerBottom = 0f
    private var contentBottom = 0f
    private var scrollY = 0f
    private var maxScroll = 0f

    /** 内容坐标系下每个关卡格子的位置（未减去滚动量） */
    private val tiles = ArrayList<RectF>()
    private val chapterY = ArrayList<Float>()
    private var tileSize = 0f

    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var pressedTile = -1
    private var firstLayout = true

    override fun layout(w: Int, h: Int) {
        this.w = w
        this.h = h
        val top = view.insets.top.toFloat()
        backBtn.r.set(view.insets.left + d(14f), top + d(10f), view.insets.left + d(58f), top + d(54f))
        headerBottom = top + d(96f)
        contentBottom = h.toFloat()

        val side = d(16f) + max(view.insets.left, view.insets.right)
        val cw = w - side * 2
        val cols = (cw / d(68f)).toInt().coerceIn(4, 10)
        val gap = d(10f)
        tileSize = (cw - gap * (cols - 1)) / cols
        tiles.clear()
        chapterY.clear()
        var y = d(8f)
        val n = LevelPack.size
        for (ch in 0 until LevelPack.chapterCount()) {
            chapterY.add(y)
            y += d(48f)
            val from = ch * LevelPack.CHAPTER_SIZE
            val to = min(n, from + LevelPack.CHAPTER_SIZE)
            for (i in from until to) {
                val k = i - from
                val x = side + (k % cols) * (tileSize + gap)
                val ty = y + (k / cols) * (tileSize + gap)
                tiles.add(RectF(x, ty, x + tileSize, ty + tileSize))
            }
            val rows = (to - from + cols - 1) / cols
            y += rows * (tileSize + gap) + d(14f)
        }
        val contentH = y + view.insets.bottom + d(16f)
        maxScroll = max(0f, contentH - (contentBottom - headerBottom))
        if (firstLayout) {
            firstLayout = false
            // 让“继续”的关卡出现在视野中
            val cur = view.progress.continueLevel(n)
            val r = tiles.getOrNull(cur)
            if (r != null) scrollY = (r.centerY() - (contentBottom - headerBottom) / 2).coerceIn(0f, maxScroll)
        }
        scrollY = scrollY.coerceIn(0f, maxScroll)
    }

    override fun draw(c: Canvas, now: Long) {
        if (scroller.computeScrollOffset()) scrollY = scroller.currY.toFloat()
        val p = view.progress
        val n = LevelPack.size
        val cont = p.continueLevel(n)

        c.save()
        c.clipRect(0f, headerBottom, w.toFloat(), contentBottom)
        c.translate(0f, headerBottom - scrollY)
        val visTop = scrollY - tileSize
        val visBottom = scrollY + (contentBottom - headerBottom) + tileSize

        for ((ch, cy) in chapterY.withIndex()) {
            if (cy < visTop - d(60f) || cy > visBottom) continue
            val from = ch * LevelPack.CHAPTER_SIZE
            val to = min(n, from + LevelPack.CHAPTER_SIZE)
            val stars = (from until to).sumOf { p.stars(it) }
            val left = tiles[from].left
            ui.text(c, "第 ${ch + 1} 章 · ${LevelPack.chapterName(ch)}", left, cy + d(22f), d(17f), Pal.text, Paint.Align.LEFT)
            val sx = w - left
            ui.text(c, "$stars / ${(to - from) * 3}", sx, cy + d(22f), d(14f), Pal.textDim, Paint.Align.RIGHT)
            val tw = ui.measure("$stars / ${(to - from) * 3}", d(14f))
            Icon.star(c, sx - tw - d(12f), cy + d(21f), d(8f), Pal.star, ui.fill)
        }

        for ((i, r) in tiles.withIndex()) {
            if (r.bottom < visTop || r.top > visBottom) continue
            drawTile(c, i, r, i == cont, now)
        }
        c.restore()

        // 顶栏
        ui.fill.shader = null
        ui.fill.color = 0xE6141D29.toInt()
        c.drawRect(0f, 0f, w.toFloat(), headerBottom, ui.fill)
        ui.fill.color = Pal.border
        c.drawRect(0f, headerBottom - d(1f), w.toFloat(), headerBottom, ui.fill)
        val top = view.insets.top.toFloat()
        ui.text(c, "选择关卡", w / 2f, top + d(32f), d(20f), Pal.text)
        val summary = "已通关 ${p.doneCount(n)} / $n    ★ ${p.starCount(n)} / ${n * 3}"
        ui.text(c, summary, w / 2f, top + d(70f), d(13f), Pal.textDim, bold = false)
        buttons.draw(c, ui, now)
    }

    private fun drawTile(c: Canvas, i: Int, r: RectF, current: Boolean, now: Long) {
        val p = view.progress
        val unlocked = p.isUnlocked(i)
        val done = p.isDone(i)
        val rad = d(14f)
        val sc = if (i == pressedTile) 0.94f else 1f
        c.save()
        c.scale(sc, sc, r.centerX(), r.centerY())
        val bg = when {
            !unlocked -> Pal.panelDark
            done -> 0xFF2C4A47.toInt()
            else -> Pal.panelLight
        }
        ui.roundRect(c, r.left, r.top + d(3f), r.right, r.bottom + d(3f), rad, 0x33000000)
        ui.roundRect(c, r.left, r.top, r.right, r.bottom, rad, bg)
        if (current && unlocked) {
            val pulse = (kotlin.math.sin(now / 260.0) * 0.5 + 0.5).toFloat()
            ui.fill.style = Paint.Style.STROKE
            ui.fill.strokeWidth = d(2.5f)
            ui.fill.color = Ui.withAlpha(Pal.accent, 0.55f + 0.45f * pulse)
            c.drawRoundRect(r, rad, rad, ui.fill)
            ui.fill.style = Paint.Style.FILL
        }
        if (!unlocked) {
            Icon.draw(c, Icon.LOCK, r.centerX(), r.centerY(), r.height() * 0.36f, 0x55FFFFFF, ui.icon)
        } else {
            val num = "${i + 1}"
            val ty = if (done) r.top + r.height() * 0.4f else r.centerY()
            ui.text(c, num, r.centerX(), ty, r.height() * 0.32f, if (done) Pal.text else Pal.text)
            if (done) {
                val st = p.stars(i)
                val sr = r.height() * 0.085f
                for (k in 0 until 3) {
                    val sx = r.centerX() + (k - 1) * sr * 2.4f
                    Icon.star(c, sx, r.top + r.height() * 0.74f, sr, if (k < st) Pal.star else Pal.starEmpty, ui.fill)
                }
            }
        }
        c.restore()
    }

    override fun animating(now: Long) = true

    override fun onTouch(e: MotionEvent) {
        if (buttons.onTouch(e)) return
        val y = e.y
        // 在顶栏按下的手势不参与滚动，否则会沿用上一次手势残留的坐标导致列表跳动
        if (e.actionMasked != MotionEvent.ACTION_DOWN && velocity == null) return
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (y < headerBottom) return
                scroller.forceFinished(true)
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(e) }
                downX = e.x
                downY = y
                lastY = y
                dragging = false
                pressedTile = tileAt(e.x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(e)
                if (!dragging && abs(y - downY) > touchSlop) {
                    dragging = true
                    pressedTile = -1
                }
                if (dragging) {
                    scrollY = (scrollY - (y - lastY)).coerceIn(0f, maxScroll)
                }
                lastY = y
            }
            MotionEvent.ACTION_UP -> {
                val vt = velocity
                if (dragging && vt != null) {
                    vt.addMovement(e)
                    vt.computeCurrentVelocity(1000)
                    scroller.fling(0, scrollY.toInt(), 0, -vt.yVelocity.toInt(), 0, 0, 0, maxScroll.toInt())
                } else if (pressedTile >= 0 && tileAt(e.x, y) == pressedTile) {
                    open(pressedTile)
                }
                pressedTile = -1
                dragging = false
                velocity?.recycle()
                velocity = null
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedTile = -1
                dragging = false
                velocity?.recycle()
                velocity = null
            }
        }
    }

    private fun tileAt(x: Float, y: Float): Int {
        if (y < headerBottom) return -1
        val cy = y - headerBottom + scrollY
        return tiles.indexOfFirst { it.contains(x, cy) }
    }

    private fun open(i: Int) {
        if (!view.progress.isUnlocked(i)) {
            view.sound.play(Sound.BUMP)
            view.toast("完成前面的关卡即可解锁")
            return
        }
        view.sound.play(Sound.CLICK, 0.6f)
        view.show(PlayScreen(view, i))
    }

    override fun onBack(): Boolean {
        view.show(MenuScreen(view))
        return true
    }
}
