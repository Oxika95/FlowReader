package com.personal.flowreader.tts

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live Edge latency probe — run manually:
 * `./gradlew :app:testDebugUnitTest --tests "*.EdgeSynthLengthBench" -Dflow.bench.edge=true`
 *
 * Opt-in via `-Dflow.bench.edge=true` so normal CI/unit runs skip it.
 */
class EdgeSynthLengthBench {
    @Test
    fun measureSynthLatencyByLength() = runBlocking {
        assumeTrue(
            "Set FLOW_BENCH_EDGE=true (or -Dflow.bench.edge=true) to run live Edge bench",
            System.getenv("FLOW_BENCH_EDGE") == "true" ||
                System.getProperty("flow.bench.edge") == "true",
        )

        val outFile = java.io.File("build/edge-synth-length-bench.txt").apply {
            parentFile?.mkdirs()
        }
        fun log(line: String) {
            println(line)
            outFile.appendText(line + "\n")
        }
        outFile.writeText("")

        val client = EdgeTtsClient()
        val lengths = listOf(20, 40, 60, 80, 100, 120, 160, 200, 280, 400, 600, 800)
        val runsPerLength = 4
        val voice = "en-US-AndrewNeural"

        log("Warmup…")
        runCatching {
            client.synthesize(text = sampleText(60), voice = voice)
        }.onFailure { log("Warmup failed: ${it.message}") }

        log("")
        log("=== Edge synth length bench (voice=$voice, runs=$runsPerLength) ===")
        log(
            "%6s %6s %8s %8s %8s %8s %8s %8s".format(
                "chars", "words", "ms_med", "ms_avg", "ms_min", "ms_max",
                "aud_ms", "syn/aud",
            ),
        )

        val rows = ArrayList<Row>()
        for (chars in lengths) {
            val text = sampleText(chars)
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
            val samples = ArrayList<Sample>()
            repeat(runsPerLength) { attempt ->
                val t0 = System.nanoTime()
                val audio = try {
                    client.synthesize(text = text, voice = voice)
                } catch (t: Throwable) {
                    log("FAIL chars=$chars attempt=$attempt: ${t.message}")
                    return@repeat
                }
                val synthMs = (System.nanoTime() - t0) / 1_000_000.0
                val audMs = audioDurationMs(audio)
                samples += Sample(synthMs, audMs, audio.mp3.size)
                Thread.sleep(200)
            }
            if (samples.isEmpty()) continue
            val synthSorted = samples.map { it.synthMs }.sorted()
            val med = percentile(synthSorted, 0.5)
            val avg = samples.map { it.synthMs }.average()
            val audMed = percentile(samples.map { it.audMs }.sorted(), 0.5)
            val ratio = if (audMed > 0) med / audMed else Double.NaN
            val row = Row(
                chars = text.length,
                words = words,
                medMs = med,
                avgMs = avg,
                minMs = synthSorted.first(),
                maxMs = synthSorted.last(),
                audMs = audMed,
                ratio = ratio,
            )
            rows += row
            log(
                "%6d %6d %8.0f %8.0f %8.0f %8.0f %8.0f %8.2f".format(
                    row.chars, row.words, row.medMs, row.avgMs, row.minMs, row.maxMs,
                    row.audMs, row.ratio,
                ),
            )
        }

        log("")
        log("--- Analysis ---")
        if (rows.size >= 3) {
            val intercept = estimateFixedCostMs(rows)
            log("Approx fixed cost (extrapolate synthMs→0 chars): ${"%.0f".format(intercept)} ms")
            val bestRatio = rows.minByOrNull { it.ratio }
            val safe = rows.filter { it.ratio.isFinite() && it.ratio <= 0.55 }
            log(
                "Lowest synth/audio ratio: chars=${bestRatio?.chars} " +
                    "ratio=${"%.2f".format(bestRatio?.ratio)} medMs=${"%.0f".format(bestRatio?.medMs ?: 0.0)}",
            )
            if (safe.isNotEmpty()) {
                val lo = safe.minOf { it.chars }
                val hi = safe.maxOf { it.chars }
                val mid = safe.minByOrNull { kotlin.math.abs(it.ratio - 0.35) }?.chars
                log("Band with ratio<=0.55: ${lo}..${hi} chars (suggest TARGET≈$mid)")
            } else {
                log("No length hit ratio<=0.55; prefer shortest median among mid sizes.")
            }
            log("Fixed-overhead tax (assume ${"%.0f".format(intercept)}ms fixed) per minute audio:")
            for (r in rows) {
                if (r.audMs <= 0) continue
                val clipsPerMin = 60_000.0 / r.audMs
                val taxMs = clipsPerMin * intercept
                log(
                    "  chars=%4d  clips/min=%5.1f  fixed_tax_ms/min=%6.0f  med_synth=%4.0f".format(
                        r.chars, clipsPerMin, taxMs, r.medMs,
                    ),
                )
            }
        }
        log("=== end bench ===")
        log("Wrote ${outFile.absolutePath}")
    }

    private data class Sample(val synthMs: Double, val audMs: Double, val bytes: Int)
    private data class Row(
        val chars: Int,
        val words: Int,
        val medMs: Double,
        val avgMs: Double,
        val minMs: Double,
        val maxMs: Double,
        val audMs: Double,
        val ratio: Double,
    )

    private fun sampleText(targetChars: Int): String = Companion.sampleText(targetChars)
    private fun audioDurationMs(audio: EdgeAudio): Double = Companion.audioDurationMs(audio)
    private fun percentile(sorted: List<Double>, p: Double): Double = Companion.percentile(sorted, p)
    private fun estimateFixedCostMs(rows: List<Row>): Double = Companion.estimateFixedCostMs(rows)

    companion object {
        private val CORPUS =
            "The quick brown fox jumps over the lazy dog near the riverbank while " +
                "clouds gather above the distant hills and a soft wind moves through the pines. " +
                "Later, travelers pause beside the old stone bridge to rest, drink water, and " +
                "watch the lantern light flicker across the water. In the village square, " +
                "merchants call out prices for bread, fruit, and cloth as children weave " +
                "between carts and dogs chase each other under the oak. Far beyond the fields, " +
                "the road climbs toward the mountains where snow still clings to the peaks " +
                "even in late spring, and every mile brings a new story worth telling aloud. "

        private fun sampleText(targetChars: Int): String {
            if (targetChars <= 0) return "Hi."
            val buf = StringBuilder()
            while (buf.length < targetChars) buf.append(CORPUS)
            var cut = buf.substring(0, targetChars)
            val sp = cut.lastIndexOf(' ')
            if (sp in (targetChars * 3 / 4) until targetChars) {
                cut = cut.substring(0, sp).trimEnd()
            }
            return cut.ifBlank { "Hi." }
        }

        private fun audioDurationMs(audio: EdgeAudio): Double {
            val last = audio.boundaries.maxByOrNull { it.offset + it.duration }
            if (last != null) {
                return (last.offset + last.duration) / 10_000.0
            }
            return audio.mp3.size / 6.0
        }

        private fun percentile(sorted: List<Double>, p: Double): Double {
            if (sorted.isEmpty()) return Double.NaN
            if (sorted.size == 1) return sorted[0]
            val idx = ((sorted.size - 1) * p).coerceIn(0.0, (sorted.size - 1).toDouble())
            val lo = idx.toInt()
            val hi = (lo + 1).coerceAtMost(sorted.lastIndex)
            val frac = idx - lo
            return sorted[lo] * (1 - frac) + sorted[hi] * frac
        }

        private fun estimateFixedCostMs(rows: List<Row>): Double {
            val n = rows.size.toDouble()
            val sumX = rows.sumOf { it.chars.toDouble() }
            val sumY = rows.sumOf { it.medMs }
            val sumXX = rows.sumOf { it.chars.toDouble() * it.chars }
            val sumXY = rows.sumOf { it.chars.toDouble() * it.medMs }
            val denom = n * sumXX - sumX * sumX
            if (denom == 0.0) return sumY / n
            val b = (n * sumXY - sumX * sumY) / denom
            val a = (sumY - b * sumX) / n
            return a.coerceAtLeast(0.0)
        }
    }
}
