package com.puzzle.sokoban

import android.content.Context

/** 一个关卡的原始数据（XSB 格式），par 为求解器算出的最少步数 / 推数。 */
class LevelData(
    val number: Int,
    val width: Int,
    val height: Int,
    val rows: List<String>,
    val parMoves: Int,
    val parPushes: Int,
    val solution: String,
)

object LevelPack {
    const val CHAPTER_SIZE = 20
    val CHAPTER_NAMES = arrayOf("新手入门", "初露锋芒", "渐入佳境", "烧脑挑战", "大师之路", "极限挑战", "传说之境")

    var levels: List<LevelData> = emptyList()
        private set

    fun load(context: Context) {
        if (levels.isNotEmpty()) return
        val text = context.assets.open("levels.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        levels = parse(text)
    }

    val size get() = levels.size

    fun chapterCount() = (levels.size + CHAPTER_SIZE - 1) / CHAPTER_SIZE

    fun chapterName(chapter: Int) =
        CHAPTER_NAMES.getOrElse(chapter) { "第 ${chapter + 1} 章" }

    fun chapterOf(index: Int) = index / CHAPTER_SIZE

    internal fun parse(text: String): List<LevelData> {
        val out = ArrayList<LevelData>()
        var rows = ArrayList<String>()
        var par = 0
        var parPushes = 0
        var sol = ""
        fun flush() {
            if (rows.isEmpty()) return
            val w = rows.maxOf { it.length }
            out.add(LevelData(out.size + 1, w, rows.size, rows.map { it.padEnd(w) }, par, parPushes, sol))
            rows = ArrayList()
            par = 0
            parPushes = 0
            sol = ""
        }
        for (raw in text.lines()) {
            val line = raw.trimEnd('\r')
            when {
                line.startsWith(";") -> flush()
                line.startsWith("par ") -> {
                    val p = line.substring(4).trim().split(" ")
                    par = p[0].toInt()
                    parPushes = p.getOrNull(1)?.toIntOrNull() ?: 0
                }
                line.startsWith("sol ") -> sol = line.substring(4).trim()
                line.isBlank() -> flush()
                else -> rows.add(line.trimEnd())
            }
        }
        flush()
        return out
    }
}
