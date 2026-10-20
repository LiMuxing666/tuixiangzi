package com.puzzle.sokoban

import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 提示用的求解器：以“推”为单位做加权 A* 搜索，配合静态死格和 2x2 冻结死锁剪枝。
 * 不追求最优，只要尽快找到一条能完成的路线。
 */
class Solver(game: Game) {
    sealed class Result {
        /** 每一项为 [推之前箱子所在格, 方向] */
        class Solved(val pushes: List<IntArray>) : Result()
        object Unsolvable : Result()
        object TooHard : Result()
        object Cancelled : Result()
    }

    private val size = game.size
    private val off = game.off.copyOf()
    private val inside = game.inside.copyOf()
    private val goal = game.goal.copyOf()
    private val dead = game.dead.copyOf()
    private val startBoxes = game.boxPos.copyOf().also { it.sort() }
    private val startPlayer = game.player
    private val goalDist = IntArray(size) { INF }
    private val goals = (0 until size).filter { goal[it] }.toIntArray()
    /** perGoal[k][c]：箱子从 c 推到第 k 个目标的最少推数（忽略其他箱子） */
    private val perGoal = Array(goals.size) { IntArray(size) { INF } }
    private val goalUsed = BooleanArray(goals.size)
    private val boxUsed = BooleanArray(64)

    private class Node(
        val boxes: IntArray,
        val player: Int,
        val parent: Node?,
        val pushFrom: Int,
        val pushDir: Int,
        val g: Int,
        val h: Int,
    ) {
        val f = g + 2 * h
    }

    init {
        // 从每个目标反向“拉”箱子做 BFS，得到各格到该目标的推数
        val q = IntArray(size)
        for ((k, g) in goals.withIndex()) {
            val dist = perGoal[k]
            var qh = 0
            var qt = 0
            dist[g] = 0
            q[qt++] = g
            while (qh < qt) {
                val b = q[qh++]
                for (d in 0..3) {
                    val c = b - off[d]
                    val p = c - off[d]
                    if (ins(c) && ins(p) && dist[c] == INF) {
                        dist[c] = dist[b] + 1
                        q[qt++] = c
                    }
                }
            }
            for (i in 0 until size) if (dist[i] < goalDist[i]) goalDist[i] = dist[i]
        }
    }

    private fun ins(i: Int) = i in 0 until size && inside[i]

    /**
     * 启发值：贪心地把箱子和目标一一配对（每次取剩下里最近的一对），推数求和。
     * 比“每个箱子找最近目标”更准——多个箱子挤向同一个目标时不会被低估。
     */
    private fun nearestSum(boxes: IntArray): Int {
        var s = 0
        for (b in boxes) s += goalDist[b]
        return s
    }

    private fun heuristic(boxes: IntArray): Int {
        val n = boxes.size
        if (n > boxUsed.size || goals.size != n) return nearestSum(boxes)
        goalUsed.fill(false)
        boxUsed.fill(false)
        var total = 0
        repeat(n) {
            var best = INF
            var bi = -1
            var gi = -1
            for (i in 0 until n) {
                if (boxUsed[i]) continue
                val b = boxes[i]
                for (k in goals.indices) {
                    if (goalUsed[k]) continue
                    val v = perGoal[k][b]
                    if (v < best) { best = v; bi = i; gi = k }
                }
            }
            // 贪心配对走进了死路（换一种配对也许可行），退回到简单估计，不据此剪枝
            if (bi < 0) return nearestSum(boxes)
            boxUsed[bi] = true
            goalUsed[gi] = true
            total += best
        }
        return total
    }

    private fun solved(boxes: IntArray): Boolean {
        for (b in boxes) if (!goal[b]) return false
        return true
    }

    private fun frozen(t: Int, boxSet: BooleanArray, w: Int): Boolean {
        val corners = intArrayOf(0, -1, -w, -w - 1)
        for (o in corners) {
            val tl = t + o
            val cells = intArrayOf(tl, tl + 1, tl + w, tl + w + 1)
            var blocked = true
            var stuck = false
            for (c in cells) {
                val isBox = boxSet[c]
                if (!isBox && ins(c)) { blocked = false; break }
                if (isBox && !goal[c]) stuck = true
            }
            if (blocked && stuck) return true
        }
        return false
    }

    fun solve(cancel: AtomicBoolean, maxNodes: Int = 300_000, timeMs: Long = 8000): Result {
        if (solved(startBoxes)) return Result.Solved(emptyList())
        if (startBoxes.any { dead[it] }) return Result.Unsolvable
        val w = off[1]
        val deadline = System.currentTimeMillis() + timeMs
        val open = PriorityQueue<Node>(compareBy<Node>({ it.f }, { it.h }))
        val closed = HashSet<String>()
        val boxSet = BooleanArray(size)
        val reach = IntArray(size)
        var stamp = 0
        val stack = IntArray(size)
        open.add(Node(startBoxes, startPlayer, null, -1, -1, 0, heuristic(startBoxes)))
        var expanded = 0
        while (true) {
            val node = open.poll() ?: return Result.Unsolvable
            if (cancel.get()) return Result.Cancelled
            if (++expanded > maxNodes || (expanded and 1023 == 0 && System.currentTimeMillis() > deadline)) {
                return Result.TooHard
            }
            val boxes = node.boxes
            for (b in boxes) boxSet[b] = true

            // 玩家可达区域，并取最小格子编号作为规范化位置
            stamp++
            var sp = 0
            stack[sp++] = node.player
            reach[node.player] = stamp
            var norm = node.player
            while (sp > 0) {
                val c = stack[--sp]
                if (c < norm) norm = c
                for (d in off) {
                    val n = c + d
                    if (reach[n] != stamp && ins(n) && !boxSet[n]) {
                        reach[n] = stamp
                        stack[sp++] = n
                    }
                }
            }
            val key = keyOf(norm, boxes)
            if (!closed.add(key)) {
                for (b in boxes) boxSet[b] = false
                continue
            }

            for (bi in boxes.indices) {
                val b = boxes[bi]
                for (d in 0..3) {
                    val p = b - off[d]
                    val t = b + off[d]
                    if (reach[p] != stamp || !ins(t) || boxSet[t] || dead[t]) continue
                    boxSet[b] = false
                    boxSet[t] = true
                    val isFrozen = !goal[t] && frozen(t, boxSet, w)
                    boxSet[t] = false
                    boxSet[b] = true
                    if (isFrozen) continue
                    val nb = boxes.copyOf()
                    nb[bi] = t
                    nb.sort()
                    val child = Node(nb, b, node, b, d, node.g + 1, heuristic(nb))
                    if (child.h == 0 && solved(nb)) {
                        for (x in boxes) boxSet[x] = false
                        return Result.Solved(path(child))
                    }
                    open.add(child)
                }
            }
            for (b in boxes) boxSet[b] = false
        }
    }

    private fun keyOf(player: Int, boxes: IntArray): String {
        val chars = CharArray(boxes.size + 1)
        chars[0] = player.toChar()
        for (i in boxes.indices) chars[i + 1] = boxes[i].toChar()
        return String(chars)
    }

    private fun path(end: Node): List<IntArray> {
        val out = ArrayList<IntArray>()
        var n: Node? = end
        while (n?.parent != null) {
            out.add(intArrayOf(n.pushFrom, n.pushDir))
            n = n.parent
        }
        out.reverse()
        return out
    }

    companion object {
        private const val INF = Int.MAX_VALUE / 4
    }
}
