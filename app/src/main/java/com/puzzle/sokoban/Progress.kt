package com.puzzle.sokoban

import android.content.Context
import kotlin.math.ceil

/** 关卡进度、存档与设置，全部保存在 SharedPreferences。关卡编号从 0 开始。 */
class Progress(context: Context) {
    private val sp = context.getSharedPreferences("sokoban", Context.MODE_PRIVATE)

    fun best(i: Int) = sp.getInt("best_$i", 0)
    fun stars(i: Int) = sp.getInt("stars_$i", 0)
    fun isDone(i: Int) = best(i) > 0

    fun isUnlocked(i: Int): Boolean {
        if (unlockAll || i < 3) return true
        // 允许最多跳过两关
        return isDone(i - 1) || isDone(i - 2) || isDone(i - 3)
    }

    /** 记录通关，返回是否刷新了最佳步数。 */
    fun recordWin(i: Int, moves: Int, stars: Int): Boolean {
        val old = best(i)
        val newBest = old == 0 || moves < old
        sp.edit().apply {
            if (newBest) putInt("best_$i", moves)
            if (stars > stars(i)) putInt("stars_$i", stars)
            remove("save_$i")
            remove("hints_$i")
        }.apply()
        return newBest
    }

    fun doneCount(total: Int) = (0 until total).count { isDone(it) }
    fun starCount(total: Int) = (0 until total).sumOf { stars(it) }

    /** 第一个还没通关的已解锁关卡，用于“继续游戏”。 */
    fun continueLevel(total: Int): Int {
        val last = lastLevel
        if (last in 0 until total && !isDone(last)) return last
        for (i in 0 until total) if (!isDone(i) && isUnlocked(i)) return i
        return last.coerceIn(0, total - 1)
    }

    var lastLevel: Int
        get() = sp.getInt("last", 0)
        set(v) = sp.edit().putInt("last", v).apply()

    fun savedGame(i: Int): String? = sp.getString("save_$i", null)
    fun savedHints(i: Int) = sp.getInt("hints_$i", 0)
    fun saveGame(i: Int, hist: String, hints: Int) {
        sp.edit().apply {
            if (hist.isEmpty()) remove("save_$i") else putString("save_$i", hist)
            if (hints == 0) remove("hints_$i") else putInt("hints_$i", hints)
        }.apply()
    }

    var sound: Boolean
        get() = sp.getBoolean("opt_sound", true)
        set(v) = sp.edit().putBoolean("opt_sound", v).apply()
    var haptic: Boolean
        get() = sp.getBoolean("opt_haptic", true)
        set(v) = sp.edit().putBoolean("opt_haptic", v).apply()
    var dpad: Boolean
        get() = sp.getBoolean("opt_dpad", false)
        set(v) = sp.edit().putBoolean("opt_dpad", v).apply()
    var deadHint: Boolean
        get() = sp.getBoolean("opt_dead", true)
        set(v) = sp.edit().putBoolean("opt_dead", v).apply()
    var unlockAll: Boolean
        get() = sp.getBoolean("opt_unlock", false)
        set(v) = sp.edit().putBoolean("opt_unlock", v).apply()
    /** 0 慢 1 中 2 快 */
    var speed: Int
        get() = sp.getInt("opt_speed", 1)
        set(v) = sp.edit().putInt("opt_speed", v).apply()

    var seenHelp: Boolean
        get() = sp.getBoolean("seen_help", false)
        set(v) = sp.edit().putBoolean("seen_help", v).apply()

    fun resetProgress() {
        val keep = sp.all.keys.filter { it.startsWith("opt_") || it == "seen_help" }.toSet()
        sp.edit().apply {
            for (k in sp.all.keys) if (k !in keep) remove(k)
        }.apply()
    }

    companion object {
        /** 3 星：不超过最优步数的 110%；2 星：不超过 150%；用过提示最多 2 星。 */
        fun starsFor(moves: Int, par: Int, hintsUsed: Int): Int {
            val s = when {
                par <= 0 -> 3
                moves <= ceil(par * 1.1).toInt() -> 3
                moves <= ceil(par * 1.5).toInt() -> 2
                else -> 1
            }
            return if (hintsUsed > 0) minOf(s, 2) else s
        }
    }
}
