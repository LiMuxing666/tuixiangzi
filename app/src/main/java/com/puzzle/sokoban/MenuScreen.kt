package com.puzzle.sokoban

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import kotlin.math.floor
import kotlin.math.min

class MenuScreen(view: GameView) : Screen(view) {
    private val tiles = TileRenderer()
    private val buttons = ButtonSet(view)
    private val playBtn = buttons.add(Button("开始游戏", Icon.PLAY, Button.PRIMARY) { play() })
    private val levelsBtn = buttons.add(Button("选择关卡", Icon.GRID) { view.show(LevelSelectScreen(view)) })
    private val helpBtn = buttons.add(Button("玩法说明", Icon.HELP) { view.showDialog(HelpDialog(view)) })
    private val settingsBtn = buttons.add(Button("设置", Icon.GEAR) { view.showDialog(SettingsDialog(view)) })

    private var w = 0
    private var h = 0
    private var titleY = 0f
    private var sceneX = 0f
    private var sceneY = 0f
    private var cell = 0f
    private val start = System.nanoTime()

    override fun onEnter() {
        refreshLabels()
    }

    private fun refreshLabels() {
        val p = view.progress
        val total = LevelPack.size
        playBtn.label = if (p.doneCount(total) == 0 && p.savedGame(0) == null) "开始游戏"
        else "继续 · 第 ${p.continueLevel(total) + 1} 关"
    }

    private fun play() {
        val i = view.progress.continueLevel(LevelPack.size)
        view.show(PlayScreen(view, i))
    }

    override fun layout(w: Int, h: Int) {
        this.w = w
        this.h = h
        refreshLabels()
        val top = view.insets.top.toFloat()
        val avail = h - top - view.insets.bottom
        cell = min(w / 9f, d(54f)).let { floor(it) }
        val bh = d(54f)
        val gap = d(14f)
        val content = d(64f) + d(26f) + d(30f) + cell * 3 + d(40f) + bh * 4 + gap * 3
        var y = top + ((avail - content) / 2).coerceAtLeast(d(16f)) - d(16f)
        titleY = y + d(32f)
        y += d(64f) + d(26f) + d(30f)
        sceneX = floor((w - cell * 7) / 2)
        sceneY = floor(y)
        y += cell * 3 + d(40f)
        val bw = min(w - d(64f), d(320f))
        for (b in buttons.list) {
            b.r.set((w - bw) / 2, y, (w + bw) / 2, y + bh)
            y += bh + gap
        }
        tiles.setSize(cell)
    }

    override fun draw(c: Canvas, now: Long) {
        // 标题
        ui.text.setShadowLayer(d(6f), 0f, d(3f), 0x80000000.toInt())
        ui.text(c, "推箱子", w / 2f, titleY, d(52f), Pal.text)
        ui.text.clearShadowLayer()
        ui.text.letterSpacing = 0.45f
        ui.text(c, "SOKOBAN", w / 2f, titleY + d(48f), d(13f), Pal.accent)
        ui.text.letterSpacing = 0f

        drawScene(c)
        buttons.tick(now)
        buttons.draw(c, ui, now)

        val p = view.progress
        val total = LevelPack.size
        val footer = "已通关 ${p.doneCount(total)} / $total     ★ ${p.starCount(total)} / ${total * 3}"
        ui.text(c, footer, w / 2f, h - view.insets.bottom - d(28f), d(13f), Pal.textDim, bold = false)
    }

    /** 小人把箱子推上目标点的循环小动画 */
    private fun drawScene(c: Canvas) {
        val t = ((System.nanoTime() - start) / 1_000_000L % 5600L).toFloat()
        val alpha = when {
            t < 400 -> t / 400f
            t > 5100 -> (5600 - t) / 500f
            else -> 1f
        }
        c.saveLayerAlpha(sceneX - cell, sceneY - cell, sceneX + cell * 8, sceneY + cell * 4, (255 * alpha).toInt())
        for (gx in 0 until 7) {
            for (gy in 0 until 3) {
                val x = sceneX + gx * cell
                val y = sceneY + gy * cell
                val isWall = gy != 1 || gx == 0 || gx == 6
                if (isWall) {
                    fun wallAt(a: Int, b: Int) = a in 0..6 && b in 0..2 && (b != 1 || a == 0 || a == 6)
                    tiles.wall(c, x, y, wallAt(gx, gy - 1), wallAt(gx, gy + 1), wallAt(gx - 1, gy), wallAt(gx + 1, gy), gx * 7 + gy)
                } else {
                    tiles.floor(c, x, y, (gx + gy) % 2 == 1, true, gx == 1)
                }
            }
        }
        // 推三格：700ms 开始，每格 420ms
        val k = ((t - 700) / 420f).coerceIn(0f, 3f)
        val cellK = floor(k).coerceAtMost(2f)
        val frac = if (k >= 3f) 1f else k - floor(k)
        val pos = cellK + Ui.easeOut(frac).let { if (k >= 3f) 1f else it }
        val boxX = sceneX + (2 + pos) * cell
        val playerX = sceneX + (1 + pos) * cell + cell / 2
        val y = sceneY + cell
        val done = k >= 3f
        if (!done) tiles.goal(c, sceneX + 5.5f * cell, y + cell / 2, 0.5f)
        tiles.box(c, boxX, y, if (done) TileRenderer.BOX_GOAL else TileRenderer.BOX_NORMAL)
        val blink = done && (t % 1700) < 120
        tiles.player(c, playerX, y + cell / 2, Dir.RIGHT, if (k in 0.01f..2.99f) frac else 0f, !done && k > 0f, blink)
        c.restore()
    }

    override fun animating(now: Long) = true

    override fun onTouch(e: MotionEvent) {
        buttons.onTouch(e)
    }

    override fun onBack() = false
}

/** 通用确认框 */
class ConfirmDialog(
    view: GameView,
    private val title: String,
    private val message: String,
    okLabel: String,
    danger: Boolean,
    private val onOk: () -> Unit,
) : Dialog(view) {
    private val cancel = buttons.add(Button("取消") { dismiss() })
    private val ok = buttons.add(Button(okLabel, kind = if (danger) Button.DANGER else Button.PRIMARY) {
        dismiss()
        onOk()
    })
    private var lines = emptyList<String>()

    override fun layout(w: Int, h: Int) {
        super.layout(w, h)
        val pw = min(w - d(48f), d(340f))
        lines = ui.wrap(message, d(15f), pw - d(48f))
        val ph = d(70f) + lines.size * d(23f) + d(88f)
        panel.set((w - pw) / 2, (h - ph) / 2, (w + pw) / 2, (h + ph) / 2)
        layoutButtonRow(listOf(cancel, ok), panel.bottom - d(20f))
    }

    override fun drawContent(c: Canvas, now: Long) {
        ui.text(c, title, panel.centerX(), panel.top + d(38f), d(20f), Pal.text)
        for ((i, l) in lines.withIndex()) {
            ui.text(c, l, panel.centerX(), panel.top + d(78f) + i * d(23f), d(15f), Pal.textDim, Paint.Align.CENTER, bold = false)
        }
    }
}
