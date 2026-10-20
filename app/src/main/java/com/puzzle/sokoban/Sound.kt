package com.puzzle.sokoban

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** 音效全部用代码合成成 WAV 再交给 SoundPool 播放，不需要任何音频资源。 */
class Sound(context: Context) {
    companion object {
        const val STEP = 0
        const val PUSH = 1
        const val GOAL = 2
        const val WIN = 3
        const val UNDO = 4
        const val CLICK = 5
        const val BUMP = 6
        const val STAR = 7
        private const val COUNT = 8
        private const val SR = 22050
    }

    var enabled = true
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    @Volatile
    private var ids = IntArray(COUNT)

    init {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        thread(name = "sfx-synth") {
            val result = IntArray(COUNT)
            for (k in 0 until COUNT) {
                val f = File(dir, "s$k.wav")
                writeWav(f, synth(k))
                result[k] = pool.load(f.path, 1)
            }
            ids = result
        }
    }

    fun play(id: Int, volume: Float = 1f, rate: Float = 1f) {
        if (!enabled) return
        val sid = ids[id]
        if (sid != 0) pool.play(sid, volume, volume, 1, 0, rate)
    }

    fun release() = pool.release()

    private fun synth(k: Int): ShortArray = when (k) {
        STEP -> tone(45) { t, ph -> sin(ph(380.0 + 260 * exp(-t * 90))) * exp(-t * 80) * 0.22 }
        PUSH -> {
            val rnd = Random(7)
            var lp = 0.0
            tone(120) { t, ph ->
                lp += (rnd.nextDouble(-1.0, 1.0) - lp) * 0.18
                val env = (t / 0.006).coerceAtMost(1.0) * exp(-t * 26)
                (lp * 0.9 + sin(ph(95.0)) * 0.6) * env * 0.55
            }
        }
        GOAL -> tone(520) { t, _ ->
            val env = (t / 0.004).coerceAtMost(1.0) * exp(-t * 7)
            (sin(2 * PI * 1046.5 * t) + 0.45 * sin(2 * PI * 2093.0 * t) + 0.2 * sin(2 * PI * 3139.5 * t)) * env * 0.32
        }
        WIN -> {
            val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5, 1318.5)
            tone(1500) { t, _ ->
                var v = 0.0
                for ((n, f) in notes.withIndex()) {
                    val st = n * 0.1
                    if (t < st) continue
                    val lt = t - st
                    val decay = if (n == notes.size - 1) 3.0 else 7.0
                    v += (sin(2 * PI * f * lt) + 0.35 * sin(4 * PI * f * lt)) *
                        (lt / 0.005).coerceAtMost(1.0) * exp(-lt * decay)
                }
                v * 0.2
            }
        }
        UNDO -> tone(80) { t, ph -> sin(ph(720.0 - 3200 * t)) * exp(-t * 45) * 0.22 }
        CLICK -> tone(40) { t, ph -> sin(ph(1500.0)) * exp(-t * 140) * 0.2 }
        BUMP -> tone(90) { t, ph -> sin(ph(130.0 - 300 * t)) * exp(-t * 38) * 0.45 }
        else -> tone(320) { t, _ ->
            val env = (t / 0.003).coerceAtMost(1.0) * exp(-t * 11)
            (sin(2 * PI * 1567.98 * t) + 0.4 * sin(2 * PI * 2349.3 * t)) * env * 0.28
        }
    }

    /** fn(t, phase) — phase(freq) 返回按瞬时频率累积的相位，便于做滑音。 */
    private inline fun tone(ms: Int, fn: (Double, (Double) -> Double) -> Double): ShortArray {
        val n = SR * ms / 1000
        val out = ShortArray(n)
        var phase = 0.0
        val ph: (Double) -> Double = { f ->
            phase += 2 * PI * f / SR
            phase
        }
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val fade = ((n - i).toDouble() / (SR * 0.004)).coerceAtMost(1.0)
            val v = (fn(t, ph) * fade).coerceIn(-1.0, 1.0)
            out[i] = (v * 32767).toInt().toShort()
        }
        return out
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        val dataLen = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(SR).putInt(SR * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(dataLen)
        for (s in pcm) buf.putShort(s)
        FileOutputStream(f).use { it.write(buf.array()) }
    }
}
