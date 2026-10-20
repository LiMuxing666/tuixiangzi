package com.puzzle.sokoban

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

class PlayScreen(view: GameView, private var index: Int) : Screen(view) {
    private lateinit var level: LevelData
    private lateinit var game: Game
    private val tiles = TileRenderer()
    private val buttons = ButtonSet(view)

    private val backBtn = buttons.add(Button(icon = Icon.BACK, kind = Button.ICON) { goBack() })
    private val gearBtn = buttons.add(Button(icon = Icon.GEAR, kind = Button.ICON) { view.showDialog(SettingsDialog(view)) })
    private val prevBtn = buttons.add(Button(icon = Icon.LEFT, kind = Button.ICON) { switchLevel(index - 1) })
    private val nextBtn = buttons.add(Button(icon = Icon.RIGHT, kind = Button.ICON) { switchLevel(index + 1) })
    private val undoBtn = buttons.add(Button("撤销", Icon.UNDO, Button.TOOL) { undo() }.apply { repeat = true })
    private val redoBtn = buttons.add(Button("重做", Icon.REDO, Button.TOOL) { redo() }.apply { repeat = true })
    private val restartBtn = buttons.add(Button("重玩", Icon.RESTART, Button.TOOL) { askRestart() })
    private val hintBtn = buttons.add(Button("提示", Icon.HINT, Button.TOOL) { hint() })
    private val dpad = Array(4) { d ->
        val icon = intArrayOf(Icon.UP, Icon.DOWN, Icon.LEFT, Icon.RIGHT)[d]
        buttons.add(Button(icon = icon, kind = Button.DPAD) { dpadPress(d) }.apply { repeat = true })
    }

    // ---------------------------------------------------------------- 布局
    private var w = 0
    private var h = 0
    private var cell = 0
    private var ox = 0f
    private var oy = 0f
    private val area = RectF()
    private val gestureArea = RectF()
    private var titleY = 0f
    private var statsY = 0f
    private var layer: Bitmap? = null
    private var goals = IntArray(0)

    // ---------------------------------------------------------------- 动画与操作队列
    private class Anim(
        val pFrom: Int, val pTo: Int,
        val box: Int, val bFrom: Int, val bTo: Int,
        val start: Long, val dur: Long,
        val forward: Boolean, val bumpDir: Int = -1,
    ) {
        val end get() = start + dur
    }

    private class QItem(val kind: Int, val dir: Int, val step: Step?, val dur: Long)

    private var anim: Anim? = null
    private var lastAnimEnd = 0L
    private val queue = ArrayDeque<QItem>()
    /** 有未存档的操作 */
    private var dirty = false
    private var boxPopAt = LongArray(0)

    private var loadedAt = 0L
    private var won = false
    private var wonAt = 0L
    private var winShown = false
    private var winInfo: WinDialog.Info? = null

    private var selBox = -1
    private var reach: Game.BoxReach? = null
    private var selAt = 0L

    // 提示 / 求解器
    private var thinking = false
    private var solverCancel: AtomicBoolean? = null
    private var generation = 0
    private var plan: ArrayList<IntArray>? = null
    private var planKey: String? = null
    private var hintAwaitKey = false
    private var hintsUsed = 0

    private var deadWarned = false
    private var nextBlink = 0L

    // 触摸
    private var boardTouch = false
    private var downX = 0f
    private var downY = 0f
    private var anchorX = 0f
    private var anchorY = 0f
    private var swiped = false

    private val particles = ArrayList<Particle>()
    private var lastFrame = 0L

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val max: Float, val color: Int, val size: Float,
        var rot: Float, val vr: Float, val ring: Boolean, val gravity: Float,
    )

    companion object {
        private const val MOVE = 0
        private const val REVERT = 1
        private const val APPLY = 2
        private val CONFETTI = intArrayOf(
            0xFFF4AE3D.toInt(), 0xFFE8574D.toInt(), 0xFF5DC47D.toInt(),
            0xFF3F86F0.toInt(), 0xFFB57BFF.toInt(), 0xFFFFE066.toInt(),
        )
        private val TIPS = arrayOf(
            "滑动屏幕或点击空地来移动\n把箱子推到红色目标点上",
            "箱子只能推，不能拉\n推之前先想好路线",
            "点击箱子会显示它能去的位置\n推错了就点「撤销」",
        )
    }

    init {
        load(index, resume = true)
    }

    private val speedK get() = floatArrayOf(1.35f, 1f, 0.7f)[view.progress.speed.coerceIn(0, 2)]
    private val durSingle get() = (125 * speedK).toLong()
    private val durPath get() = (78 * speedK).toLong()
    private val durRevert get() = (60 * speedK).toLong()

    // ---------------------------------------------------------------- 关卡加载

    private fun load(i: Int, resume: Boolean) {
        cancelSolver()
        index = i
        level = LevelPack.levels[i]
        game = Game(level)
        anim = null
        queue.clear()
        won = false
        winShown = false
        selBox = -1
        reach = null
        plan = null
        planKey = null
        hintAwaitKey = false
        hintsUsed = 0
        deadWarned = false
        particles.clear()
        generation++
        boxPopAt = LongArray(game.boxCount)
        if (resume) {
            // 重玩后还没走就离开时只存了提示次数，也要读回来，否则重新进关就能拿 3 星
            hintsUsed = view.progress.savedHints(i)
            val saved = view.progress.savedGame(i)
            if (!saved.isNullOrEmpty()) {
                game.restore(saved)
                if (game.isSolved()) game = Game(level)
                else if (game.moves > 0) view.toast("已恢复上次进度，可撤销")
            }
        }
        view.progress.lastLevel = i
        goals = (0 until game.size).filter { game.goal[it] }.toIntArray()
        loadedAt = SystemClock.uptimeMillis()
        if (w > 0) layoutBoard()
    }

    private fun switchLevel(i: Int) {
        if (i < 0 || i >= LevelPack.size) return
        if (!view.progress.isUnlocked(i)) {
            view.toast("完成前面的关卡即可解锁")
            return
        }
        save()
        load(i, resume = true)
    }

    private fun save() {
        if (!won) view.progress.saveGame(index, game.historyString(), hintsUsed)
    }

    override fun onEnter() {
        if (!view.progress.seenHelp) {
            view.progress.seenHelp = true
            view.showDialog(HelpDialog(view))
        }
    }

    override fun onLeave() {
        // 切换界面的淡出期间本界面还会继续绘制：先收尾当前动作、丢掉排队的移动，
        // 否则淡出时小人还在走，并且会在“重置进度”之后又把存档写回去
        settle()
        finishAnim()
        dirty = false
        save()
        cancelSolver()
    }

    override fun onPause() = save()

    private fun goBack() {
        view.show(LevelSelectScreen(view))
    }

    override fun onBack(): Boolean {
        if (selBox >= 0) {
            clearSelection()
            return true
        }
        goBack()
        return true
    }

    // ---------------------------------------------------------------- 布局

    override fun layout(w: Int, h: Int) {
        this.w = w
        this.h = h
        val ins = view.insets
        val top = ins.top.toFloat()
        val l = ins.left.toFloat()
        val r = (w - ins.right).toFloat()
        val cx = w / 2f
        backBtn.r.set(l + d(12f), top + d(8f), l + d(56f), top + d(52f))
        gearBtn.r.set(r - d(56f), top + d(8f), r - d(12f), top + d(52f))
        prevBtn.r.set(cx - d(112f), top + d(12f), cx - d(76f), top + d(48f))
        nextBtn.r.set(cx + d(76f), top + d(12f), cx + d(112f), top + d(48f))
        titleY = top + d(24f)
        statsY = top + d(88f)

        val toolH = d(70f)
        val toolTop = h - ins.bottom - toolH - d(6f)
        val tw = min(r - l - d(24f), d(440f))
        val tools = listOf(undoBtn, redoBtn, restartBtn, hintBtn)
        val bw = tw / tools.size
        for ((k, b) in tools.withIndex()) {
            val x = cx - tw / 2 + k * bw
            b.r.set(x + d(4f), toolTop, x + bw - d(4f), toolTop + toolH)
        }

        var bottom = toolTop - d(6f)
        val showPad = view.progress.dpad
        for (b in dpad) b.visible = showPad
        if (showPad) {
            val s = d(56f)
            val g = d(4f)
            val pcx = cx
            val pcy = bottom - s * 1.5f - g
            fun place(b: Button, dx: Int, dy: Int) {
                val bx = pcx + dx * (s + g)
                val by = pcy + dy * (s + g)
                b.r.set(bx - s / 2, by - s / 2, bx + s / 2, by + s / 2)
            }
            place(dpad[Dir.UP], 0, -1)
            place(dpad[Dir.DOWN], 0, 1)
            place(dpad[Dir.LEFT], -1, 0)
            place(dpad[Dir.RIGHT], 1, 0)
            bottom = pcy - s * 1.5f - g - d(8f)
        }
        area.set(l + d(12f), statsY + d(36f), r - d(12f), bottom)
        gestureArea.set(0f, statsY + d(28f), w.toFloat(), if (showPad) bottom + d(8f) else toolTop)
        layoutBoard()
    }

    private fun layoutBoard() {
        val cols = level.width
        val rows = level.height
        val tipSpace = if (index < TIPS.size) d(60f) else 0f
        val ah = area.height() - tipSpace
        cell = floor(min(min(area.width() / cols, ah / rows), d(76f))).toInt().coerceAtLeast(8)
        ox = floor(area.centerX() - cols * cell / 2f)
        oy = floor(area.top + ah / 2 - rows * cell / 2f)
        tiles.setSize(cell.toFloat())
        buildLayer()
    }

    /** 地板和墙是静态的，预先画进一张位图 */
    private fun buildLayer() {
        val bw = level.width * cell
        val bh = level.height * cell
        val old = layer
        val bmp = if (old != null && old.width == bw && old.height == bh) {
            old.eraseColor(0)
            old
        } else {
            old?.recycle()
            Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        }
        val c = Canvas(bmp)
        val g = game
        val s = cell.toFloat()
        for (y in 0 until level.height) for (x in 0 until level.width) {
            val i = g.index(x, y)
            if (g.inside[i]) tiles.floor(c, x * s, y * s, (x + y) % 2 == 1, g.wall[i - g.w], g.wall[i - 1])
        }
        for (y in 0 until level.height) for (x in 0 until level.width) {
            val i = g.index(x, y)
            if (g.wall[i]) tiles.wall(c, x * s, y * s, g.wall[i - g.w], g.wall[i + g.w], g.wall[i - 1], g.wall[i + 1], i * 31 + level.number)
        }
        layer = bmp
    }

    private fun px(i: Int) = ox + game.cellX(i) * cell
    private fun py(i: Int) = oy + game.cellY(i) * cell

    private fun cellAt(x: Float, y: Float): Int {
        val gx = floor((x - ox) / cell).toInt()
        val gy = floor((y - oy) / cell).toInt()
        if (gx < 0 || gy < 0 || gx >= level.width || gy >= level.height) return -1
        return game.index(gx, gy)
    }

    // ---------------------------------------------------------------- 绘制

    override fun draw(c: Canvas, now: Long) {
        val dt = ((now - lastFrame).coerceIn(0, 50)) / 1000f
        lastFrame = now
        buttons.tick(now)
        advance(now)
        if (won && !winShown && now - wonAt > 1100) {
            winShown = true
            winInfo?.let { info -> view.showDialog(WinDialog(view, info, ::onWinAction)) }
        }

        drawBoard(c, now)
        updateParticles(c, dt)
        drawHud(c, now)
    }

    private fun drawBoard(c: Canvas, now: Long) {
        val k = ((now - loadedAt) / 320f).coerceIn(0f, 1f)
        val bw = level.width * cell
        val bh = level.height * cell
        c.save()
        if (k < 1f) {
            val sc = 0.94f + 0.06f * Ui.easeOut(k)
            c.scale(sc, sc, ox + bw / 2f, oy + bh / 2f)
            c.saveLayerAlpha(ox - cell, oy - cell, ox + bw + cell, oy + bh + cell, (255 * Ui.easeOut(k)).toInt())
        }
        layer?.let { c.drawBitmap(it, ox, oy, null) }

        val a = anim
        val t = if (a == null) 1f else ((now - a.start).toFloat() / a.dur).coerceIn(0f, 1f)
        val pulse = (sin(now / 420.0) * 0.5 + 0.5).toFloat()
        val half = cell / 2f
        for (g in goals) {
            // 箱子正在滑入的目标点要继续显示，直到箱子到位
            val covered = game.boxAt[g] >= 0 && !(a != null && a.box >= 0 && a.bTo == g && t < 1f)
            if (!covered) tiles.goal(c, px(g) + half, py(g) + half, pulse)
        }

        // 选中箱子后的可达位置
        val rc = reach
        if (selBox >= 0 && rc != null) {
            val sk = Ui.easeOutBack(((now - selAt) / 260f).coerceIn(0f, 1f))
            for (i in 0 until game.size) {
                if (rc.canReach(i)) {
                    // 目标点绿色，死角灰色（不用红色，免得和目标点混淆）
                    val col = if (game.goal[i]) Pal.green else if (game.dead[i] && view.progress.deadHint) 0xFF8A8F98.toInt() else Pal.accent
                    tiles.marker(c, px(i) + half, py(i) + half, col, sk)
                }
            }
        }

        // 箱子
        for (id in 0 until game.boxCount) {
            val pos = game.boxPos[id]
            var x = px(pos)
            var y = py(pos)
            var cellForState = pos
            if (a != null && a.box == id && t < 1f) {
                val e = t
                x = px(a.bFrom) + (px(a.bTo) - px(a.bFrom)) * e
                y = py(a.bFrom) + (py(a.bTo) - py(a.bFrom)) * e
                cellForState = a.bFrom
            }
            val state = when {
                game.goal[cellForState] -> TileRenderer.BOX_GOAL
                view.progress.deadHint && game.dead[cellForState] -> TileRenderer.BOX_DEAD
                else -> TileRenderer.BOX_NORMAL
            }
            var sc = 1f
            val pk = (now - boxPopAt[id]) / 320f
            if (boxPopAt[id] > 0 && pk in 0f..1f) sc = 1f + 0.14f * sin(pk * PI).toFloat() * (1f - pk * 0.5f)
            tiles.box(c, x, y, state, sc, id == selBox)
        }

        // 小人
        var pxp = px(game.player) + half
        var pyp = py(game.player) + half
        var phase = 0f
        var pushing = false
        if (a != null && t < 1f) {
            if (a.bumpDir >= 0) {
                val o = sin(t * PI).toFloat() * cell * 0.12f
                pxp = px(a.pFrom) + half + Dir.DX[a.bumpDir] * o
                pyp = py(a.pFrom) + half + Dir.DY[a.bumpDir] * o
            } else {
                pxp = px(a.pFrom) + half + (px(a.pTo) - px(a.pFrom)) * t
                pyp = py(a.pFrom) + half + (py(a.pTo) - py(a.pFrom)) * t
                phase = t
                pushing = a.box >= 0
            }
        }
        if (now > nextBlink + 140) nextBlink = now + 1800 + Random.nextLong(2600)
        val blink = now in nextBlink..(nextBlink + 130)
        if (won) {
            // 通关后开心地跳
            val j = abs(sin((now - wonAt) / 180.0)).toFloat()
            pyp -= j * cell * 0.12f
        }
        tiles.player(c, pxp, pyp, game.facing, phase, pushing, blink)

        if (k < 1f) c.restore()
        c.restore()

        if (index < TIPS.size && !won) {
            val lines = TIPS[index].split('\n')
            val ty = oy + bh + d(34f)
            for ((li, line) in lines.withIndex()) {
                ui.text(c, line, w / 2f, ty + li * d(22f), d(14f), Pal.textDim, bold = false)
            }
        }
    }

    private fun drawHud(c: Canvas, now: Long) {
        val top = view.insets.top.toFloat()
        val n = LevelPack.size
        prevBtn.enabled = index > 0
        nextBtn.enabled = index + 1 < n && view.progress.isUnlocked(index + 1)
        undoBtn.enabled = game.canUndo && !won && !thinking
        redoBtn.enabled = game.canRedo && !won && !thinking
        restartBtn.enabled = !won && !thinking
        hintBtn.enabled = !won
        hintBtn.busy = thinking
        for (b in dpad) b.enabled = !won && !thinking

        ui.text(c, "第 ${index + 1} 关", w / 2f, titleY, d(20f), Pal.text)
        val best = view.progress.best(index)
        val sub = LevelPack.chapterName(LevelPack.chapterOf(index)) + if (best > 0) " · 最佳 $best 步" else ""
        ui.text(c, sub, w / 2f, titleY + d(23f), d(12f), Pal.textDim, bold = false)

        // 数据栏
        val l = view.insets.left + d(16f)
        val r = w - view.insets.right - d(16f)
        val sw = min(r - l, d(440f))
        val sl = w / 2f - sw / 2
        ui.roundRect(c, sl, statsY - d(24f), sl + sw, statsY + d(24f), d(16f), 0x26000000)
        val target = if (level.parMoves > 0) ceil(level.parMoves * 1.1).toInt() else 0
        val labels = arrayOf("步数", "推动", "就位", "三星目标")
        val values = arrayOf(
            "${game.moves}", "${game.pushes}", "${game.boxesOnGoal()} / ${game.boxCount}",
            if (target > 0) "≤ $target" else "—",
        )
        val cw = sw / 4
        for (k in 0 until 4) {
            val cx = sl + cw * (k + 0.5f)
            ui.text(c, labels[k], cx, statsY - d(10f), d(11f), Pal.textDim, bold = false)
            val col = when {
                k == 2 && game.boxesOnGoal() == game.boxCount -> Pal.green
                k == 3 && target > 0 && game.moves > target -> 0xFF8A97A8.toInt()
                k == 3 -> Pal.star
                else -> Pal.text
            }
            ui.text(c, values[k], cx, statsY + d(10f), d(17f), col)
        }
        buttons.draw(c, ui, now)
    }

    private fun updateParticles(c: Canvas, dt: Float) {
        if (particles.isEmpty()) return
        val iter = particles.iterator()
        val p = ui.fill
        p.shader = null
        while (iter.hasNext()) {
            val q = iter.next()
            q.life -= dt
            if (q.life <= 0f) {
                iter.remove()
                continue
            }
            q.vy += q.gravity * dt
            q.vx *= (1f - 0.8f * dt)
            q.x += q.vx * dt
            q.y += q.vy * dt
            q.rot += q.vr * dt
            val a = (q.life / q.max).coerceIn(0f, 1f)
            if (q.ring) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = q.size * 0.18f * a
                p.color = Ui.withAlpha(q.color, a)
                c.drawCircle(q.x, q.y, q.size * (1.6f - a), p)
                p.style = Paint.Style.FILL
            } else {
                p.color = Ui.withAlpha(q.color, min(1f, a * 2f))
                c.save()
                c.translate(q.x, q.y)
                c.rotate(q.rot)
                c.drawRect(-q.size, -q.size * 0.45f, q.size, q.size * 0.45f, p)
                c.restore()
            }
        }
    }

    private fun sparkle(cell: Int) {
        val cx = px(cell) + this.cell / 2f
        val cy = py(cell) + this.cell / 2f
        particles.add(Particle(cx, cy, 0f, 0f, 0.45f, 0.45f, Pal.star, this.cell * 0.45f, 0f, 0f, true, 0f))
        for (k in 0 until 8) {
            val ang = k * PI / 4 + Random.nextDouble(0.3)
            val sp = d(120f) + Random.nextFloat() * d(80f)
            particles.add(
                Particle(
                    cx, cy, (cos(ang) * sp).toFloat(), (sin(ang) * sp).toFloat(), 0.5f, 0.5f,
                    if (k % 2 == 0) Pal.star else 0xFFFFFFFF.toInt(), d(2.5f), 0f, 0f, false, d(200f),
                )
            )
        }
    }

    private fun confetti() {
        val bx = ox + level.width * cell / 2f
        val by = oy + level.height * cell / 2f
        for (k in 0 until 120) {
            val ang = -PI / 2 + (Random.nextDouble() - 0.5) * 1.9
            val sp = d(380f) + Random.nextFloat() * d(520f)
            particles.add(
                Particle(
                    bx + (Random.nextFloat() - 0.5f) * d(40f), by,
                    (cos(ang) * sp).toFloat(), (sin(ang) * sp).toFloat(),
                    1.6f + Random.nextFloat(), 2.6f, CONFETTI[k % CONFETTI.size], d(4f) + Random.nextFloat() * d(3f),
                    Random.nextFloat() * 360f, (Random.nextFloat() - 0.5f) * 720f, false, d(900f),
                )
            )
        }
    }

    /** 目标点一直在呼吸闪烁，所以游戏界面持续刷新 */
    override fun animating(now: Long) = true

    // ---------------------------------------------------------------- 操作队列

    /** 推进动画：结束的动画收尾，空闲时取下一条指令执行。 */
    private fun advance(now: Long) {
        var guard = 0
        while (guard++ < 200) {
            val a = anim
            if (a != null) {
                if (now < a.end) return
                anim = null
                lastAnimEnd = a.end
                onAnimEnd(a)
                if (won) return
            }
            if (queue.isEmpty()) {
                if (hintAwaitKey) {
                    hintAwaitKey = false
                    planKey = game.stateKey()
                }
                // 每个操作结束就存档，进程被系统直接杀掉也不会丢进度
                if (dirty) {
                    dirty = false
                    save()
                }
                return
            }
            val start = if (now - lastAnimEnd < 40) lastAnimEnd else now
            startNext(start)
        }
    }

    private fun startNext(start: Long) {
        val q = queue.removeFirst()
        dirty = true
        if (selBox >= 0) clearSelection()
        val g = game
        when (q.kind) {
            MOVE -> {
                val from = g.player
                val n = from + g.off[q.dir]
                val box = if (g.ins(n)) g.boxAt[n] else -1
                val s = g.tryMove(q.dir)
                if (s == null) {
                    queue.clear()
                    hintAwaitKey = false
                    anim = Anim(from, from, -1, 0, 0, start, 150, false, q.dir)
                    view.sound.play(Sound.BUMP, 0.5f)
                    return
                }
                if (s.push) {
                    anim = Anim(from, g.player, box, n, g.boxPos[box], start, q.dur, true)
                    view.sound.play(Sound.PUSH, 0.8f)
                } else {
                    anim = Anim(from, g.player, -1, 0, 0, start, q.dur, true)
                    view.sound.play(Sound.STEP, 0.6f)
                }
            }
            REVERT -> {
                val s = q.step!!
                val from = g.player
                val bc = from + g.off[s.dir]
                val box = if (s.push) g.boxAt[bc] else -1
                g.revert(s)
                anim = Anim(from, g.player, box, bc, from, start, q.dur, false)
            }
            APPLY -> {
                val s = q.step!!
                val from = g.player
                val n = from + g.off[s.dir]
                val box = if (s.push) g.boxAt[n] else -1
                g.apply(s)
                anim = if (s.push) Anim(from, g.player, box, n, g.boxPos[box], start, q.dur, true)
                else Anim(from, g.player, -1, 0, 0, start, q.dur, true)
            }
        }
    }

    private fun onAnimEnd(a: Anim) {
        if (!a.forward || a.bumpDir >= 0) return
        if (a.box >= 0) {
            val t = a.bTo
            if (game.goal[t]) {
                boxPopAt[a.box] = a.end
                view.sound.play(Sound.GOAL, 0.8f)
                view.haptic()
                sparkle(t)
            } else if (game.dead[t] && view.progress.deadHint && !deadWarned) {
                deadWarned = true
                view.toast("这个箱子进了死角，再也推不到目标了\n点「撤销」退回去吧", long = true)
            }
        }
        if (!won && game.isSolved()) onWin(a.end)
    }

    /** 新的一次操作开始：丢弃尚未执行的移动，完成未播完的撤销 / 重做，并开启新的撤销组。 */
    private fun beginAction() {
        settle()
        game.closeGroup()
        hintAwaitKey = false
        if (selBox >= 0) clearSelection()
    }

    /** 立即结束正在播放的动画（触发到位、过关等结算）。 */
    private fun finishAnim() {
        val a = anim ?: return
        anim = null
        lastAnimEnd = a.end
        onAnimEnd(a)
    }

    private fun settle() {
        var changed = false
        while (queue.isNotEmpty()) {
            val q = queue.removeFirst()
            when (q.kind) {
                REVERT -> { game.revert(q.step!!); changed = true }
                APPLY -> { game.apply(q.step!!); changed = true }
            }
        }
        if (changed) anim = null
    }

    private fun enqueue(dirs: IntArray, dur: Long) {
        for (d in dirs) queue.addLast(QItem(MOVE, d, null, dur))
    }

    // ---------------------------------------------------------------- 输入

    override fun onTouch(e: MotionEvent) {
        if (buttons.onTouch(e)) return
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                boardTouch = !won && gestureArea.contains(e.x, e.y)
                downX = e.x
                downY = e.y
                anchorX = e.x
                anchorY = e.y
                swiped = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!boardTouch || thinking) return
                val th = max(d(20f), min(cell * 0.55f, d(44f)))
                val dx = e.x - anchorX
                val dy = e.y - anchorY
                if (abs(dx) > th || abs(dy) > th) {
                    val horizontal = abs(dx) > abs(dy)
                    val dir = if (horizontal) {
                        if (dx > 0) Dir.RIGHT else Dir.LEFT
                    } else {
                        if (dy > 0) Dir.DOWN else Dir.UP
                    }
                    if (!swiped) {
                        beginAction()
                        swiped = true
                    }
                    if (queue.size < 2) queue.addLast(QItem(MOVE, dir, null, durSingle))
                    if (horizontal) {
                        anchorX += sign(dx) * th
                        anchorY = e.y
                    } else {
                        anchorY += sign(dy) * th
                        anchorX = e.x
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!boardTouch) return
                boardTouch = false
                if (!swiped && hypot(e.x - downX, e.y - downY) < d(14f)) onTap(e.x, e.y)
            }
            MotionEvent.ACTION_CANCEL -> boardTouch = false
        }
    }

    private fun onTap(x: Float, y: Float) {
        if (thinking) {
            view.toast("正在思考提示…")
            return
        }
        val cellIdx = cellAt(x, y)
        if (cellIdx < 0 || !game.inside[cellIdx]) {
            if (selBox >= 0) clearSelection()
            return
        }
        // 已选中箱子：点可达位置就自动推过去
        val rc = reach
        if (selBox >= 0 && rc != null) {
            if (rc.canReach(cellIdx)) {
                val path = rc.pathTo(cellIdx)
                beginAction()
                enqueue(path, durPath)
                return
            }
            val prev = selBox
            clearSelection()
            // 再点一次同一个箱子 = 取消选中；点别处则按普通点击处理
            if (game.boxAt[cellIdx] == prev) return
        }
        val b = game.boxAt[cellIdx]
        if (b >= 0) {
            settle()
            selectBox(b)
            return
        }
        val path = game.walkPath(cellIdx)
        if (path == null) {
            view.sound.play(Sound.BUMP, 0.5f)
            view.toast("走不到那里")
            return
        }
        if (path.isEmpty()) return
        beginAction()
        enqueue(path, durPath)
    }

    private fun selectBox(b: Int) {
        if (anim != null || queue.isNotEmpty()) {
            // 等当前动作结束再选，避免状态错位
            beginAction()
            finishAnim()
            if (won) return
        }
        val rc = game.BoxReach(b)
        if (rc.count == 0) {
            view.sound.play(Sound.BUMP, 0.5f)
            view.toast("这个箱子现在推不动")
            return
        }
        selBox = b
        reach = rc
        selAt = SystemClock.uptimeMillis()
        view.sound.play(Sound.CLICK, 0.5f)
    }

    private fun clearSelection() {
        selBox = -1
        reach = null
    }

    private fun dpadPress(d: Int) {
        if (won || thinking) return
        val btn = dpad[d]
        if (!btn.repeating) beginAction()
        if (queue.size < 1) queue.addLast(QItem(MOVE, d, null, durSingle))
    }

    private fun undo() {
        if (won || thinking) return
        settle()
        clearSelection()
        val g = game.popUndo()
        if (g == null) {
            if (!undoBtn.repeating) view.toast("已经回到起点了")
            return
        }
        anim = null
        val dur = if (g.size <= 6) durRevert else max(14L, 420L / g.size)
        for (s in g.asReversed()) queue.addLast(QItem(REVERT, s.dir, s, dur))
        view.sound.play(Sound.UNDO, 0.7f)
    }

    private fun redo() {
        if (won || thinking) return
        settle()
        clearSelection()
        val g = game.popRedo() ?: return
        anim = null
        val dur = if (g.size <= 6) durRevert else max(14L, 420L / g.size)
        for (s in g) queue.addLast(QItem(APPLY, s.dir, s, dur))
        view.sound.play(Sound.UNDO, 0.7f, 1.25f)
    }

    private fun askRestart() {
        if (game.moves == 0) {
            view.toast("已经在起点了")
            return
        }
        view.showDialog(ConfirmDialog(view, "重新开始", "当前进度会被清空，确定从头开始本关吗？", "重来", true) {
            restart()
        })
    }

    private fun restart() {
        val hints = hintsUsed
        // 重来不清除提示记录，同时立刻写盘，避免进程被杀后丢失
        view.progress.saveGame(index, "", hints)
        load(index, resume = false)
        hintsUsed = hints
        view.sound.play(Sound.UNDO)
    }

    // ---------------------------------------------------------------- 提示

    private fun hint() {
        if (won) return
        if (thinking) {
            view.toast("正在思考提示…")
            return
        }
        beginAction()
        finishAnim()
        if (won) return
        // 1) 走法仍是内置最优解的前缀：直接给出下一推
        val hist = game.flatHistory()
        val sol = level.solution
        if (sol.length > hist.length && sol.startsWith(hist)) {
            var end = hist.length
            while (end < sol.length && !sol[end].isUpperCase()) end++
            val dirs = sol.substring(hist.length, min(end + 1, sol.length))
                .map { Dir.CHARS.indexOf(it.lowercaseChar()) }.toIntArray()
            runHint(dirs)
            return
        }
        // 2) 上次求解得到的计划仍然有效
        val key = game.stateKey()
        val p = plan
        if (p != null && p.isNotEmpty() && planKey == key) {
            executePush(p.removeAt(0))
            return
        }
        // 3) 后台求解
        thinking = true
        val gen = generation
        val cancel = AtomicBoolean(false)
        solverCancel = cancel
        val solver = Solver(game)
        thread(name = "sokoban-solver", isDaemon = true) {
            val r = solver.solve(cancel)
            view.post {
                if (gen != generation || cancel.get()) return@post
                thinking = false
                onSolved(r, key)
                view.invalidate()
            }
        }
    }

    private fun onSolved(r: Solver.Result, key: String) {
        when (r) {
            is Solver.Result.Solved -> {
                if (game.stateKey() != key || r.pushes.isEmpty()) return
                val p = ArrayList(r.pushes)
                plan = p
                executePush(p.removeAt(0))
            }
            Solver.Result.Unsolvable -> {
                view.sound.play(Sound.BUMP)
                view.toast("当前局面已经无解了\n撤销几步再试试吧", long = true)
            }
            Solver.Result.TooHard -> view.toast("局面太复杂，暂时算不出提示\n撤销几步再试试吧", long = true)
            Solver.Result.Cancelled -> {}
        }
    }

    private fun executePush(push: IntArray) {
        val stand = push[0] - game.off[push[1]]
        val path = game.walkPath(stand) ?: return
        runHint(path + push[1])
    }

    private fun runHint(dirs: IntArray) {
        beginAction()
        if (hintsUsed == 0) view.toast("使用提示后，本关最高可得 2 星")
        hintsUsed++
        enqueue(dirs, (durPath * 1.4f).toLong())
        hintAwaitKey = true
    }

    private fun cancelSolver() {
        solverCancel?.set(true)
        solverCancel = null
        thinking = false
    }

    // ---------------------------------------------------------------- 通关

    private fun onWin(at: Long) {
        won = true
        wonAt = at
        queue.clear()
        clearSelection()
        cancelSolver()
        val moves = game.moves
        val stars = Progress.starsFor(moves, level.parMoves, hintsUsed)
        val prevBest = view.progress.best(index)
        val newBest = view.progress.recordWin(index, moves, stars)
        winInfo = WinDialog.Info(
            index, moves, game.pushes, stars, level.parMoves, newBest && prevBest > 0, prevBest, hintsUsed,
        )
        view.sound.play(Sound.WIN)
        view.haptic(strong = true)
        confetti()
        for (id in 0 until game.boxCount) boxPopAt[id] = at + 150 + id * 90L
    }

    private fun onWinAction(action: Int) {
        when (action) {
            WinDialog.NEXT -> if (index + 1 < LevelPack.size) load(index + 1, resume = true)
            WinDialog.REPLAY -> load(index, resume = false)
            WinDialog.LEVELS -> view.show(LevelSelectScreen(view))
        }
    }
}
