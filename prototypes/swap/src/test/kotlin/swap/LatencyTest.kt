package swap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.file.Path

/**
 * Mede só o cálculo da regra (sem banco, rede ou serialização). Imprime p50, p95 e p99.
 * Dois cenários: uma troca única e a geração de opções varrendo um catálogo grande em memória.
 */
class LatencyTest {
    private val base = DataLoader.catalog(Path.of("data/catalog.tsv"))
    private val plan = DataLoader.plan(Path.of("data/plan.tsv"))
    private val targets = DayTargets(BigDecimal(1900), BigDecimal(120), BigDecimal(1500))

    private fun percentiles(samplesNs: LongArray): Triple<Double, Double, Double> {
        val sorted = samplesNs.sortedArray()
        fun p(q: Double) = sorted[((sorted.size - 1) * q).toInt()] / 1_000_000.0
        return Triple(p(0.50), p(0.95), p(0.99))
    }

    @Test
    fun `single swap latency`() {
        val request = SwapRequest(plan, 4, "mashed_potato", targets, emptySet())
        repeat(20_000) { SwapEngine.swap(base, request) } // aquecimento do JIT
        val samples = LongArray(100_000) {
            val t0 = System.nanoTime()
            SwapEngine.swap(base, request)
            System.nanoTime() - t0
        }
        val (p50, p95, p99) = percentiles(samples)
        println("LATENCIA troca unica (ms): p50=%.4f p95=%.4f p99=%.4f".format(p50, p95, p99))
        assertThat(p99).isLessThan(50.0)
    }

    @Test
    fun `option generation over a large catalog`() {
        // catálogo sintético de 5.000 alimentos: cada um é uma variação de um alimento real do mesmo grupo
        val templates = base.values.toList()
        val big = HashMap<String, Food>(6_000)
        big.putAll(base)
        for (i in 0 until 5_000) {
            val t = templates[i % templates.size]
            val kcal = t.kcalPer100g.add(BigDecimal(i % 7))
            big["synthetic_$i"] = t.copy(id = "synthetic_$i", kcalPer100g = kcal)
        }
        val originalGroup = base.getValue(plan[4].foodId).group
        val candidates = big.values.filter { it.group == originalGroup }
        fun generate(): Int {
            var accepted = 0
            for (c in candidates) {
                if (SwapEngine.swap(big, SwapRequest(plan, 4, c.id, targets, emptySet())).accepted) accepted++
            }
            return accepted
        }
        repeat(30) { generate() } // aquecimento
        val samples = LongArray(500) {
            val t0 = System.nanoTime()
            generate()
            System.nanoTime() - t0
        }
        val (p50, p95, p99) = percentiles(samples)
        println("LATENCIA opcoes sobre ${candidates.size} candidatos (ms): p50=%.3f p95=%.3f p99=%.3f".format(p50, p95, p99))
        assertThat(p99).isLessThan(300.0)
    }
}
