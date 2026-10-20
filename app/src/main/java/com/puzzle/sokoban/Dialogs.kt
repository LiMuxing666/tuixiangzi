package com.puzzle.sokoban

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import kotlin.math.min

class HelpDialog(view: GameView) : Dialog(view) {
    private val items = listOf(
        "目标" to "把所有箱子推到红色目标点上，箱子就位后会变成金色。",
        "规则" to "箱子只能推、不能拉，一次只能推动一个。",
        "滑动" to "朝滑动方向走一步；手指不松开继续拖动可以连续行走。",
        "点空地" to "小人会自动走过去。",
        "点箱子" to "标出它能被推到的位置，再点目标格就会自动推过去。",
        "撤销" to "按住「撤销」可以连续后退，「重做」可以前进。",
        "提示" to "自动走出下一推。用过提示的关卡最高 2 星。",
        "红箱子" to "箱子被推进了死角，再也到不了目标，需要撤销。",
        "星级" to "步数不超过最优解的 110% 得 3 星，150% 以内得 2 星。",
    )
    private val ok = buttons.add(Button("知道了", kind = Button.PRIMARY) { dismiss() })
    private var wrapped = emptyList<Pair<String, List<String>>>()
    private var textSize = 0f
    private var lineH = 0f
    private var labelW = 0f

    override fun layout(w: Int, h: Int) {
        super.layout(w, h)
        val pw = min(w - d(32f), d(400f))
        val maxH = h - view.insets.top - view.insets.bottom - d(32f)
        textSize = d(14f)
        var ph: Float
        while (true) {
            lineH = textSize * 1.5f
            labelW = ui.measure("红箱子", textSize) + d(14f)
            wrapped = items.map { (k, v) -> k to ui.wrap(v, textSize, pw - d(44f) - labelW) }
            val lines = wrapped.sumOf { it.second.size }
            ph = d(70f) + lines * lineH + items.size * d(6f) + d(84f)
            if (ph <= maxH || textSize < d(11f)) break
            textSize -= d(0.5f)
        }
        panel.set((w - pw) / 2, (h - ph) / 2, (w + pw) / 2, (h + ph) / 2)
        layoutButtonRow(listOf(ok), panel.bottom - d(20f))
    }

    override fun drawContent(c: Canvas, now: Long) {
        ui.text(c, "玩法说明", panel.centerX(), panel.top + d(38f), d(20f), Pal.text)
        var y = panel.top + d(70f)
        val x = panel.left + d(22f)
        for ((label, lines) in wrapped) {
            ui.text(c, label, x, y + lineH / 2, textSize, Pal.accent, Paint.Align.LEFT)
            for (line in lines) {
                ui.text(c, line, x + labelW, y + lineH / 2, textSize, Pal.text, Paint.Align.LEFT, bold = false)
                y += lineH
            }
            y += d(6f)
        }
    }
}

class SettingsDialog(view: GameView) : Dialog(view) {
    private class Row(val label: String, val get: () -> Boolean, val set: (Boolean) -> Unit) {
        var anim = -1f
        var top = 0f
    }

    private val p = view.progress
    private val rows = listOf(
        Row("音效", { p.sound }, { p.sound = it }),
        Row("振动反馈", { p.haptic }, { p.haptic = it }),
        Row("屏幕方向键", { p.dpad }, { p.dpad = it }),
        Row("死角提醒（箱子变红）", { p.deadHint }, { p.deadHint = it }),
        Row("解锁全部关卡", { p.unlockAll }, { p.unlockAll = it }),
    )
    private val speedLabels = arrayOf("慢", "中", "快")
    private val speedBtns = Array(3) { k ->
        buttons.add(Button(speedLabels[k]) {
            p.speed = k
            refreshSpeed()
        })
    }
    private val resetBtn = buttons.add(Button("重置进度", kind = Button.NORMAL) { askReset() })
    private val doneBtn = buttons.add(Button("完成", kind = Button.PRIMARY) { dismiss() })
    private val rowH get() = d(50f)
    private var speedY = 0f
    private var pressedRow = -1

    init {
        refreshSpeed()
    }

    private fun refreshSpeed() {
        for ((k, b) in speedBtns.withIndex()) b.kind = if (k == p.speed) Button.PRIMARY else Button.NORMAL
    }

    override fun layout(w: Int, h: Int) {
        super.layout(w, h)
        val pw = min(w - d(40f), d(360f))
        val ph = d(66f) + rows.size * rowH + d(64f) + d(76f)
        panel.set((w - pw) / 2, (h - ph) / 2, (w + pw) / 2, (h + ph) / 2)
        var y = panel.top + d(62f)
        for (r in rows) {
            r.top = y
            y += rowH
        }
        speedY = y + d(28f)
        val sw = d(52f)
        val sh = d(36f)
        for ((k, b) in speedBtns.withIndex()) {
            val x = panel.right - d(20f) - (3 - k) * (sw + d(6f)) + d(6f)
            b.r.set(x, speedY - sh / 2, x + sw, speedY + sh / 2)
        }
        layoutButtonRow(listOf(resetBtn, doneBtn), panel.bottom - d(20f))
    }

    override fun drawContent(c: Canvas, now: Long) {
        ui.text(c, "设置", panel.centerX(), panel.top + d(36f), d(20f), Pal.text)
        val l = panel.left + d(22f)
        val r = panel.right - d(22f)
        for ((i, row) in rows.withIndex()) {
            val on = row.get()
            val target = if (on) 1f else 0f
            row.anim = if (row.anim < 0f) target else row.anim + (target - row.anim) * 0.3f
            if (i == pressedRow) ui.roundRect(c, l - d(10f), row.top + d(2f), r + d(10f), row.top + rowH - d(2f), d(12f), 0x14FFFFFF)
            ui.text(c, row.label, l, row.top + rowH / 2, d(15f), Pal.text, Paint.Align.LEFT, bold = false)
            ui.toggle(c, r - d(23f), row.top + rowH / 2, row.anim)
        }
        ui.text(c, "移动速度", l, speedY, d(15f), Pal.text, Paint.Align.LEFT, bold = false)
    }

    override fun animating(now: Long) = true

    override fun onTouch(e: MotionEvent) {
        if (buttons.onTouch(e)) return
        val idx = rows.indexOfFirst { e.y >= it.top && e.y < it.top + rowH && e.x >= panel.left && e.x <= panel.right }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressedRow = idx
            MotionEvent.ACTION_MOVE -> if (idx != pressedRow) pressedRow = -1
            MotionEvent.ACTION_UP -> {
                if (idx >= 0 && idx == pressedRow) {
                    val row = rows[idx]
                    row.set(!row.get())
                    view.sound.enabled = p.sound
                    view.sound.play(Sound.CLICK, 0.6f)
                    view.haptic()
                    view.applySettings()
                } else if (pressedRow < 0 && idx < 0 && !panel.contains(e.x, e.y)) {
                    dismiss()
                }
                pressedRow = -1
            }
            MotionEvent.ACTION_CANCEL -> pressedRow = -1
        }
    }

    private fun askReset() {
        view.showDialog(ConfirmDialog(view, "重置进度", "所有关卡的通关记录、星级和存档都会被清除，此操作无法撤销。", "重置", true) {
            dismiss()
            // 先离开当前界面（游戏界面离开时会存档），再清空数据
            view.show(MenuScreen(view))
            p.resetProgress()
            view.applySettings()
            view.toast("进度已重置")
        })
    }
}

class WinDialog(view: GameView, private val info: Info, private val onAction: (Int) -> Unit) : Dialog(view) {
    class Info(
        val index: Int, val moves: Int, val pushes: Int, val stars: Int, val par: Int,
        val newRecord: Boolean, val prevBest: Int, val hints: Int,
    )

    companion object {
        const val NEXT = 0
        const val REPLAY = 1
        const val LEVELS = 2
    }

    override val cancelable = false
    private val isLast = info.index + 1 >= LevelPack.size
    private val replay = buttons.add(Button("重玩", Icon.RESTART) { act(REPLAY) })
    private val levels = buttons.add(Button("选关", Icon.GRID) { act(LEVELS) })
    private val next = buttons.add(Button("下一关", Icon.NEXT, Button.PRIMARY) { act(NEXT) })
    private var starsPlayed = 0

    private fun act(a: Int) {
        dismiss()
        onAction(a)
    }

    override fun layout(w: Int, h: Int) {
        super.layout(w, h)
        val pw = min(w - d(40f), d(350f))
        val ph = d(330f)
        panel.set((w - pw) / 2, (h - ph) / 2, (w + pw) / 2, (h + ph) / 2)
        next.visible = !isLast
        layoutButtonRow(if (isLast) listOf(replay, levels) else listOf(replay, levels, next), panel.bottom - d(20f))
    }

    override fun drawContent(c: Canvas, now: Long) {
        val cx = panel.centerX()
        val title = if (isLast) "全部通关！" else "过关！"
        ui.text(c, title, cx, panel.top + d(40f), d(24f), Pal.text)
        ui.text(c, "第 ${info.index + 1} 关", cx, panel.top + d(68f), d(13f), Pal.textDim, bold = false)

        // 星星依次弹出
        val sy = panel.top + d(118f)
        for (k in 0 until 3) {
            val appear = openedAt + 280 + k * 240L
            val sx = cx + (k - 1) * d(66f)
            val r = if (k == 1) d(30f) else d(25f)
            val yy = if (k == 1) sy - d(8f) else sy
            Icon.star(c, sx, yy, r, Pal.starEmpty, ui.fill)
            if (k < info.stars && now >= appear) {
                if (starsPlayed <= k) {
                    starsPlayed = k + 1
                    view.sound.play(Sound.STAR, 0.7f, 1f + k * 0.12f)
                }
                val t = ((now - appear) / 300f).coerceIn(0f, 1f)
                val s = Ui.easeOutBack(t)
                Icon.star(c, sx, yy, r * s, Pal.star, ui.fill)
            }
        }

        var y = panel.top + d(178f)
        ui.text(c, "步数 ${info.moves}     推动 ${info.pushes}", cx, y, d(17f), Pal.text)
        y += d(30f)
        val badge = when {
            info.par > 0 && info.moves <= info.par -> "完美！达到了最优解"
            info.newRecord -> "刷新纪录！之前最好 ${info.prevBest} 步"
            else -> null
        }
        if (badge != null) {
            val bw = ui.measure(badge, d(13f)) + d(24f)
            ui.roundRect(c, cx - bw / 2, y - d(13f), cx + bw / 2, y + d(13f), d(13f), 0x335DC47D)
            ui.text(c, badge, cx, y, d(13f), Pal.green)
            y += d(30f)
        }
        val sub = buildString {
            if (info.par > 0) append("最优解 ${info.par} 步")
            if (info.hints > 0) append("   ·   使用提示 ${info.hints} 次")
        }
        ui.text(c, sub, cx, y, d(13f), Pal.textDim, bold = false)
    }

    override fun animating(now: Long) = now - openedAt < 1400 || buttons.tracking

    override fun onBack() = act(LEVELS)
}
