package com.puzzle.sokoban

object Dir {
    const val UP = 0
    const val DOWN = 1
    const val LEFT = 2
    const val RIGHT = 3
    val DX = intArrayOf(0, 0, -1, 1)
    val DY = intArrayOf(-1, 1, 0, 0)
    const val CHARS = "udlr"
}

class Step(val dir: Int, val push: Boolean)

/**
 * 推箱子的规则与状态。地图四周补了一圈空白，所以任何格子的上下左右邻居都不会越界。
 * 撤销以“操作组”为单位：一次滑动、一次点击寻路、一次推箱寻路各算一组。
 */
class Game(val level: LevelData) {
    val w = level.width + 2
    val h = level.height + 2
    val size = w * h
    val off = intArrayOf(-w, w, -1, 1)

    val wall = BooleanArray(size)
    val goal = BooleanArray(size)
    /** 玩家能到达的区域（地板） */
    val inside = BooleanArray(size)
    /** 静态死格：箱子进去就再也推不到任何目标 */
    val dead = BooleanArray(size)
    val boxAt = IntArray(size) { -1 }
    val boxPos: IntArray

    var player = 0
        private set
    var facing = Dir.DOWN
    var moves = 0
        private set
    var pushes = 0
        private set

    private val undoStack = ArrayList<ArrayList<Step>>()
    private val redoStack = ArrayList<ArrayList<Step>>()
    private var groupOpen = false

    init {
        val boxes = ArrayList<Int>()
        for (y in 0 until level.height) {
            val row = level.rows[y]
            for (x in 0 until level.width) {
                val i = (y + 1) * w + (x + 1)
                when (row[x]) {
                    '#' -> wall[i] = true
                    '.' -> goal[i] = true
                    '$' -> boxes.add(i)
                    '*' -> { goal[i] = true; boxes.add(i) }
                    '@' -> player = i
                    '+' -> { goal[i] = true; player = i }
                }
            }
        }
        boxPos = boxes.toIntArray()
        for (k in boxPos.indices) boxAt[boxPos[k]] = k

        val stack = IntArray(size)
        var sp = 0
        stack[sp++] = player
        inside[player] = true
        while (sp > 0) {
            val c = stack[--sp]
            for (d in off) {
                val n = c + d
                if (n in 0 until size && !wall[n] && !inside[n]) {
                    inside[n] = true
                    stack[sp++] = n
                }
            }
        }

        // 从目标出发反向“拉”箱子，拉得到的格子都是活格
        val live = BooleanArray(size)
        sp = 0
        for (i in 0 until size) if (goal[i]) { live[i] = true; stack[sp++] = i }
        while (sp > 0) {
            val b = stack[--sp]
            for (d in off) {
                val c = b - d
                if (ins(c) && ins(c - d) && !live[c]) {
                    live[c] = true
                    stack[sp++] = c
                }
            }
        }
        for (i in 0 until size) dead[i] = inside[i] && !live[i]
    }

    fun ins(i: Int) = i in 0 until size && inside[i]
    fun isFree(i: Int) = ins(i) && boxAt[i] < 0
    fun cellX(i: Int) = i % w - 1
    fun cellY(i: Int) = i / w - 1
    fun index(x: Int, y: Int) = (y + 1) * w + (x + 1)

    val boxCount get() = boxPos.size
    fun boxesOnGoal() = boxPos.count { goal[it] }
    fun isSolved(): Boolean {
        for (b in boxPos) if (!goal[b]) return false
        return true
    }

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    /** 结束当前操作组，下一步会开启新组。 */
    fun closeGroup() {
        groupOpen = false
    }

    /** 尝试朝 dir 走一步（必要时推箱子），成功则记入撤销栈。 */
    fun tryMove(dir: Int): Step? {
        facing = dir
        val n = player + off[dir]
        if (!ins(n)) return null
        val b = boxAt[n]
        if (b >= 0 && !isFree(n + off[dir])) return null
        val s = Step(dir, b >= 0)
        apply(s)
        if (!groupOpen) {
            undoStack.add(ArrayList())
            groupOpen = true
        }
        undoStack[undoStack.size - 1].add(s)
        redoStack.clear()
        return s
    }

    /** 执行一步，不记录（重做时用）。 */
    fun apply(s: Step) {
        val n = player + off[s.dir]
        if (s.push) {
            val b = boxAt[n]
            val t = n + off[s.dir]
            boxAt[n] = -1
            boxAt[t] = b
            boxPos[b] = t
            pushes++
        }
        player = n
        moves++
        facing = s.dir
    }

    /** 撤销一步，不改动撤销栈。 */
    fun revert(s: Step) {
        if (s.push) {
            val bc = player + off[s.dir]
            val b = boxAt[bc]
            boxAt[bc] = -1
            boxAt[player] = b
            boxPos[b] = player
            pushes--
        }
        player -= off[s.dir]
        moves--
        facing = s.dir
    }

    /** 弹出最近一组操作（移入重做栈），由调用方逐步 revert。 */
    fun popUndo(): List<Step>? {
        groupOpen = false
        if (undoStack.isEmpty()) return null
        val g = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(g)
        return g
    }

    fun popRedo(): List<Step>? {
        groupOpen = false
        if (redoStack.isEmpty()) return null
        val g = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(g)
        return g
    }

    /** 用于存档：每组之间以 | 分隔，大写字母表示推箱。 */
    fun historyString(): String {
        val sb = StringBuilder()
        for ((gi, g) in undoStack.withIndex()) {
            if (gi > 0) sb.append('|')
            for (s in g) {
                val ch = Dir.CHARS[s.dir]
                sb.append(if (s.push) ch.uppercaseChar() else ch)
            }
        }
        return sb.toString()
    }

    /** 不带分组的完整走法，用来和内置最优解比较。 */
    fun flatHistory(): String = historyString().replace("|", "")

    fun restore(hist: String) {
        for (ch in hist) {
            if (ch == '|') {
                closeGroup()
                continue
            }
            val d = Dir.CHARS.indexOf(ch.lowercaseChar())
            if (d < 0 || tryMove(d) == null) break
        }
        closeGroup()
    }

    fun stateKey(): String {
        val sorted = boxPos.copyOf()
        sorted.sort()
        val sb = StringBuilder()
        sb.append(player).append(':')
        for (b in sorted) sb.append(b).append(',')
        return sb.toString()
    }

    /** 玩家走到 target 的最短路径（方向序列），走不到返回 null。 */
    fun walkPath(target: Int): IntArray? {
        if (target == player) return IntArray(0)
        if (!isFree(target)) return null
        val from = IntArray(size) { -1 }
        val q = IntArray(size)
        var qh = 0
        var qt = 0
        q[qt++] = player
        from[player] = 4
        while (qh < qt) {
            val c = q[qh++]
            if (c == target) break
            for (d in 0..3) {
                val n = c + off[d]
                if (from[n] == -1 && isFree(n)) {
                    from[n] = d
                    q[qt++] = n
                }
            }
        }
        if (from[target] == -1) return null
        val out = ArrayList<Int>()
        var c = target
        while (c != player) {
            val d = from[c]
            out.add(d)
            c -= off[d]
        }
        out.reverse()
        return out.toIntArray()
    }

    /**
     * 选中一个箱子后，计算它能被推到的所有位置以及对应的最少步数走法。
     * 状态为 (箱子位置, 玩家位置)，其他箱子视为障碍。
     */
    inner class BoxReach(val boxId: Int) {
        private val start = boxPos[boxId]
        private val prevState = IntArray(size * size) { -2 }
        private val prevDir = ByteArray(size * size)
        private val firstState = IntArray(size) { -1 }
        var count = 0
            private set

        init {
            boxAt[start] = -1
            val q = IntArray(size * size)
            var qh = 0
            var qt = 0
            val s0 = start * size + player
            prevState[s0] = -1
            q[qt++] = s0
            firstState[start] = s0
            while (qh < qt) {
                val s = q[qh++]
                val b = s / size
                val p = s % size
                for (d in 0..3) {
                    val n = p + off[d]
                    val ns = if (n == b) {
                        val t = b + off[d]
                        if (!isFree(t)) continue
                        t * size + b
                    } else {
                        if (!isFree(n)) continue
                        b * size + n
                    }
                    if (prevState[ns] != -2) continue
                    prevState[ns] = s
                    prevDir[ns] = d.toByte()
                    q[qt++] = ns
                    val nb = ns / size
                    if (firstState[nb] < 0) {
                        firstState[nb] = ns
                        count++
                    }
                }
            }
            boxAt[start] = boxId
        }

        fun canReach(cell: Int) = cell != start && cell in 0 until size && firstState[cell] >= 0

        fun pathTo(cell: Int): IntArray {
            val out = ArrayList<Int>()
            var s = firstState[cell]
            while (prevState[s] >= 0) {
                out.add(prevDir[s].toInt())
                s = prevState[s]
            }
            out.reverse()
            return out.toIntArray()
        }
    }
}
