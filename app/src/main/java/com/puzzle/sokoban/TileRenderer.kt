package com.puzzle.sokoban

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** 用纯 Canvas 绘制所有格子：地板、墙、目标点、木箱和小人。 */
class TileRenderer {
    var s = 0f
        private set
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var shadowTop: Shader? = null
    private var shadowLeft: Shader? = null
    private var bodyShader: Shader? = null
    private var glowShader: Shader? = null

    companion object {
        const val BOX_NORMAL = 0
        const val BOX_GOAL = 1
        const val BOX_DEAD = 2

        // 深 / 中 / 浅 / 钉子
        private val WOOD = intArrayOf(0xFF7A4A22.toInt(), 0xFFA0672F.toInt(), 0xFFDAA463.toInt(), 0xFF573316.toInt())
        private val GOLD = intArrayOf(0xFF9A6A10.toInt(), 0xFFC8901E.toInt(), 0xFFF7D25E.toInt(), 0xFF6A4508.toInt())
        private val DEAD = intArrayOf(0xFF7C2C24.toInt(), 0xFFA23E33.toInt(), 0xFFE07F70.toInt(), 0xFF561812.toInt())
    }

    fun setSize(size: Float) {
        if (size == s) return
        s = size
        shadowTop = LinearGradient(0f, 0f, 0f, s * 0.3f, 0x44000000, 0, Shader.TileMode.CLAMP)
        shadowLeft = LinearGradient(0f, 0f, s * 0.18f, 0f, 0x2A000000, 0, Shader.TileMode.CLAMP)
        val rad = s * 0.34f
        bodyShader = RadialGradient(
            -rad * 0.35f, -rad * 0.45f, rad * 1.5f,
            intArrayOf(Pal.playerLight, Pal.player, Pal.playerDark), floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        glowShader = RadialGradient(0f, 0f, s * 0.7f, intArrayOf(0x70FFD45A, 0x00FFD45A), null, Shader.TileMode.CLAMP)
    }

    fun floor(c: Canvas, x: Float, y: Float, alt: Boolean, wallUp: Boolean, wallLeft: Boolean) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = if (alt) Pal.floorB else Pal.floorA
        c.drawRect(x, y, x + s, y + s, p)
        val line = max(1f, s * 0.02f)
        p.color = Pal.floorLine
        c.drawRect(x, y, x + s, y + line, p)
        c.drawRect(x, y, x + line, y + s, p)
        if (wallUp || wallLeft) {
            c.save()
            c.translate(x, y)
            if (wallUp) {
                p.shader = shadowTop
                c.drawRect(0f, 0f, s, s * 0.3f, p)
            }
            if (wallLeft) {
                p.shader = shadowLeft
                c.drawRect(0f, 0f, s * 0.18f, s, p)
            }
            p.shader = null
            c.restore()
        }
    }

    /** 立体石墙：相邻的墙会连成一体，露在外面的角是圆的，下方露出侧面。 */
    fun wall(c: Canvas, x: Float, y: Float, up: Boolean, down: Boolean, left: Boolean, right: Boolean, seed: Int) {
        val rad = s * 0.16f
        val face = s * 0.22f
        p.shader = null
        p.style = Paint.Style.FILL
        c.save()
        c.translate(x, y)
        p.color = Pal.wallFace
        blob(c, s, s, rad, up, down, left, right)
        p.color = Pal.wallTop
        val bottom = if (down) s else s - face
        blob(c, s, bottom, rad, up, down, left, right)
        if (!up) {
            p.color = Pal.wallHi
            val l = if (left) 0f else rad * 0.7f
            val rr = if (right) s else s - rad * 0.7f
            c.drawRect(l, s * 0.035f, rr, s * 0.035f + max(1.5f, s * 0.045f), p)
        }
        // 石头纹理小斑点
        p.color = Pal.wallSpeck
        var h = seed * 1103515245 + 12345
        for (k in 0 until 3) {
            h = h * 1103515245 + 12345
            val fx = ((h ushr 8) and 0xFF) / 255f
            val fy = ((h ushr 16) and 0xFF) / 255f
            c.drawCircle(s * (0.18f + 0.64f * fx), bottom * (0.22f + 0.56f * fy), s * (0.03f + 0.02f * k), p)
        }
        c.restore()
    }

    private fun blob(c: Canvas, w: Float, b: Float, rad: Float, up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
        r.set(0f, 0f, w, b)
        c.drawRoundRect(r, rad, rad, p)
        val mx = w / 2
        val my = b / 2
        if (up) c.drawRect(0f, 0f, w, my, p)
        if (down) c.drawRect(0f, my, w, b, p)
        if (left) c.drawRect(0f, 0f, mx, b, p)
        if (right) c.drawRect(mx, 0f, w, b, p)
    }

    fun goal(c: Canvas, cx: Float, cy: Float, pulse: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = Ui.withAlpha(Pal.goal, 0.16f + 0.14f * pulse)
        c.drawCircle(cx, cy, s * (0.3f + 0.035f * pulse), p)
        p.color = Pal.goal
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.065f
        c.drawCircle(cx, cy, s * 0.2f, p)
        p.style = Paint.Style.FILL
        c.drawCircle(cx, cy, s * 0.075f, p)
    }

    /** 可推到位置的小提示点 */
    fun marker(c: Canvas, cx: Float, cy: Float, color: Int, scale: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = Ui.withAlpha(color, 0.35f)
        c.drawCircle(cx, cy, s * 0.2f * scale, p)
        p.color = color
        c.drawCircle(cx, cy, s * 0.09f * scale, p)
    }

    fun box(c: Canvas, x: Float, y: Float, state: Int, scale: Float = 1f, selected: Boolean = false) {
        val col = when (state) {
            BOX_GOAL -> GOLD
            BOX_DEAD -> DEAD
            else -> WOOD
        }
        p.shader = null
        p.style = Paint.Style.FILL
        c.save()
        c.translate(x + s / 2, y + s / 2)
        c.scale(scale, scale)
        if (state == BOX_GOAL) {
            p.shader = glowShader
            c.drawCircle(0f, 0f, s * 0.7f, p)
            p.shader = null
        }
        c.translate(-s / 2, -s / 2)
        val m = s * 0.07f
        // 投影
        p.color = 0x38000000
        r.set(m * 0.5f, s * 0.5f, s - m * 0.5f, s + s * 0.02f)
        c.drawRoundRect(r, s * 0.14f, s * 0.14f, p)
        if (selected) {
            p.color = Pal.accent
            r.set(m - s * 0.06f, m - s * 0.06f, s - m + s * 0.06f, s - m + s * 0.04f)
            c.drawRoundRect(r, s * 0.16f, s * 0.16f, p)
        }
        // 外框
        p.color = col[0]
        r.set(m, m, s - m, s - m - s * 0.02f)
        c.drawRoundRect(r, s * 0.1f, s * 0.1f, p)
        // 箱板
        val f = s * 0.13f
        val il = m + f
        val tp = m + f
        val ir = s - m - f
        val ib = s - m - f - s * 0.02f
        p.color = col[2]
        c.drawRect(il, tp, ir, ib, p)
        p.color = col[1]
        val pl = max(1f, s * 0.022f)
        val ih = ib - tp
        c.drawRect(il, tp + ih / 3 - pl / 2, ir, tp + ih / 3 + pl / 2, p)
        c.drawRect(il, tp + ih * 2 / 3 - pl / 2, ir, tp + ih * 2 / 3 + pl / 2, p)
        // 斜撑
        c.save()
        c.clipRect(il, tp, ir, ib)
        p.style = Paint.Style.STROKE
        p.strokeWidth = f * 0.95f
        p.strokeCap = Paint.Cap.BUTT
        c.drawLine(il - f, ib + f, ir + f, tp - f, p)
        p.style = Paint.Style.FILL
        c.restore()
        // 内框阴影
        p.color = 0x22000000
        c.drawRect(il, tp, ir, tp + max(1f, s * 0.03f), p)
        // 顶部高光
        p.color = 0x40FFFFFF
        r.set(m + s * 0.06f, m + s * 0.022f, s - m - s * 0.06f, m + s * 0.05f)
        c.drawRoundRect(r, s * 0.02f, s * 0.02f, p)
        // 钉子
        p.color = col[3]
        val nr = s * 0.026f
        val n0 = m + f / 2
        val n1 = s - m - f / 2
        val n1y = s - m - f / 2 - s * 0.02f
        c.drawCircle(n0, n0, nr, p)
        c.drawCircle(n1, n0, nr, p)
        c.drawCircle(n0, n1y, nr, p)
        c.drawCircle(n1, n1y, nr, p)
        c.restore()
    }

    /**
     * 小人：圆滚滚的身体、头顶小芽、朝着行走方向看的眼睛。
     * phase 为行走相位（0..1 一步），squash 为推箱时的挤压程度。
     */
    fun player(c: Canvas, cx: Float, cy: Float, facing: Int, phase: Float, pushing: Boolean, blink: Boolean) {
        val rad = s * 0.34f
        p.shader = null
        p.style = Paint.Style.FILL
        c.save()
        c.translate(cx, cy)
        // 影子
        p.color = 0x40000000
        r.set(-rad * 0.85f, rad * 0.72f, rad * 0.85f, rad * 1.08f)
        c.drawOval(r, p)
        val step = sin(phase * PI).toFloat()
        val bob = -abs(step) * s * 0.05f
        // 脚
        p.color = Pal.playerDark
        val lift = rad * 0.16f * step
        r.set(-rad * 0.62f, rad * 0.58f - lift, -rad * 0.12f, rad * 0.98f - lift)
        c.drawOval(r, p)
        r.set(rad * 0.12f, rad * 0.58f + lift * 0.3f, rad * 0.62f, rad * 0.98f + lift * 0.3f)
        c.drawOval(r, p)

        c.translate(0f, bob)
        if (pushing) {
            // 推箱时朝推的方向微微前倾
            c.translate(Dir.DX[facing] * s * 0.04f, Dir.DY[facing] * s * 0.04f)
            if (Dir.DX[facing] != 0) c.scale(1.06f, 0.95f) else c.scale(0.95f, 1.06f)
        }
        // 头顶小芽
        p.color = Pal.leaf
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.035f
        p.strokeCap = Paint.Cap.ROUND
        c.drawLine(0f, -rad * 0.9f, rad * 0.05f, -rad * 1.2f, p)
        p.style = Paint.Style.FILL
        c.save()
        c.rotate(-30f, rad * 0.05f, -rad * 1.2f)
        r.set(rad * 0.05f, -rad * 1.32f, rad * 0.45f, -rad * 1.1f)
        c.drawOval(r, p)
        c.restore()
        // 身体
        p.shader = bodyShader
        c.drawCircle(0f, 0f, rad, p)
        p.shader = null

        if (facing != Dir.UP) {
            val ex = Dir.DX[facing] * rad * 0.22f
            val ey = Dir.DY[facing] * rad * 0.12f
            // 腮红
            p.color = 0x55FF7A8A
            c.drawCircle(-rad * 0.56f + ex * 0.6f, rad * 0.24f + ey, rad * 0.14f, p)
            c.drawCircle(rad * 0.56f + ex * 0.6f, rad * 0.24f + ey, rad * 0.14f, p)
            for (side in intArrayOf(-1, 1)) {
                val x0 = side * rad * 0.32f + ex
                val y0 = -rad * 0.1f + ey
                if (blink) {
                    p.color = 0xFF1E2533.toInt()
                    c.drawRect(x0 - rad * 0.16f, y0 - rad * 0.025f, x0 + rad * 0.16f, y0 + rad * 0.035f, p)
                } else {
                    p.color = 0xFFFFFFFF.toInt()
                    r.set(x0 - rad * 0.19f, y0 - rad * 0.23f, x0 + rad * 0.19f, y0 + rad * 0.23f)
                    c.drawOval(r, p)
                    val px = x0 + Dir.DX[facing] * rad * 0.07f
                    val py = y0 + Dir.DY[facing] * rad * 0.08f + rad * 0.02f
                    p.color = 0xFF1E2533.toInt()
                    c.drawCircle(px, py, rad * 0.11f, p)
                    p.color = 0xFFFFFFFF.toInt()
                    c.drawCircle(px + rad * 0.04f, py - rad * 0.05f, rad * 0.035f, p)
                }
            }
        } else {
            // 背对镜头：画一道背部高光
            p.color = 0x30FFFFFF
            r.set(-rad * 0.5f, -rad * 0.6f, rad * 0.5f, -rad * 0.2f)
            c.drawOval(r, p)
        }
        c.restore()
    }
}
