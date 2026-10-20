package com.puzzle.sokoban

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class LevelsTest {
    private val levels by lazy {
        LevelPack.parse(File("src/main/assets/levels.txt").readText(Charsets.UTF_8))
    }

    @Test
    fun allLevelsParse() {
        assertEquals(100, levels.size)
        for (lv in levels) {
            val g = Game(lv)
            val goals = (0 until g.size).count { g.goal[it] }
            assertEquals("第 ${lv.number} 关箱子数与目标数不一致", goals, g.boxCount)
            assertTrue(lv.parMoves > 0)
        }
    }

    /** 内置最优解在 Kotlin 规则下也必须能走通，且步数等于 par。 */
    @Test
    fun builtInSolutionsWork() {
        for (lv in levels) {
            val g = Game(lv)
            for (ch in lv.solution) {
                val d = Dir.CHARS.indexOf(ch.lowercaseChar())
                val s = g.tryMove(d)
                assertNotNull("第 ${lv.number} 关的解在 '$ch' 处走不通", s)
                assertEquals("第 ${lv.number} 关推箱标记不一致", ch.isUpperCase(), s!!.push)
            }
            assertTrue("第 ${lv.number} 关走完解后没有完成", g.isSolved())
            assertEquals(lv.parMoves, g.moves)
            assertEquals(lv.parPushes, g.pushes)
        }
    }

    /** 提示求解器要能从每关起点找到解，并且按它的推法真的能过关。 */
    @Test
    fun solverSolvesEveryLevel() {
        var slowest = 0L
        var slowestLevel = 0
        for (lv in levels) {
            val g = Game(lv)
            val t0 = System.currentTimeMillis()
            // 节点上限与游戏内一致；时间放宽，因为测试机和手机速度不同
            val r = Solver(g).solve(AtomicBoolean(false), timeMs = 60_000)
            val dt = System.currentTimeMillis() - t0
            if (dt > slowest) {
                slowest = dt
                slowestLevel = lv.number
            }
            assertTrue("第 ${lv.number} 关求解失败: $r", r is Solver.Result.Solved)
            for (push in (r as Solver.Result.Solved).pushes) {
                val path = g.walkPath(push[0] - g.off[push[1]])
                assertNotNull("第 ${lv.number} 关提示路线走不到推箱位置", path)
                for (d in path!!) assertNotNull(g.tryMove(d))
                assertTrue(g.tryMove(push[1])!!.push)
            }
            assertTrue("第 ${lv.number} 关按提示推完没有完成", g.isSolved())
        }
        println("solver slowest level: #$slowestLevel ${slowest}ms")
    }

    @Test
    fun undoRedoRestoresState() {
        val lv = levels[10]
        val g = Game(lv)
        val start = g.stateKey()
        for (ch in lv.solution.take(12)) g.tryMove(Dir.CHARS.indexOf(ch.lowercaseChar()))
        val mid = g.stateKey()
        val hist = g.historyString()
        while (true) {
            val grp = g.popUndo() ?: break
            for (s in grp.asReversed()) g.revert(s)
        }
        assertEquals(start, g.stateKey())
        assertEquals(0, g.moves)
        while (true) {
            val grp = g.popRedo() ?: break
            for (s in grp) g.apply(s)
        }
        assertEquals(mid, g.stateKey())
        val g2 = Game(lv)
        g2.restore(hist)
        assertEquals(mid, g2.stateKey())
    }

    @Test
    fun boxReachPathsAreValid() {
        for (lv in levels.take(30)) {
            val g = Game(lv)
            for (id in 0 until g.boxCount) {
                val reach = g.BoxReach(id)
                for (cell in 0 until g.size) {
                    if (!reach.canReach(cell)) continue
                    val copy = Game(lv)
                    for (d in reach.pathTo(cell)) assertNotNull(copy.tryMove(d))
                    assertEquals(cell, copy.boxPos[id])
                }
            }
        }
    }
}
