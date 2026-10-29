package swap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.file.Path

class SwapEngineTest {
    private val catalog = DataLoader.catalog(Path.of("data/catalog.tsv"))
    private val plan = DataLoader.plan(Path.of("data/plan.tsv"))
    private val targets = DayTargets(BigDecimal(1900), BigDecimal(120), BigDecimal(1500))

    private fun swap(index: Int, substitute: String, excluded: Set<String> = emptySet()) =
        SwapEngine.swap(catalog, SwapRequest(plan, index, substitute, targets, excluded))

    @Test
    fun `rounds portion to the food step with half up`() {
        // 192 kcal / 0,91 = 210,989 g -> 210 g (passo de 5 g)
        assertThat(swap(4, "mashed_potato").grams).isEqualByComparingTo("210")
    }

    @Test
    fun `is deterministic`() {
        assertThat(swap(4, "sweet_potato")).isEqualTo(swap(4, "sweet_potato"))
    }

    @Test
    fun `never suggests an excluded tag even when the numbers fit`() {
        val result = swap(4, "pasta", setOf("GLUTEN"))
        assertThat(result.accepted).isFalse()
        assertThat(result.rejections).containsExactly(Rejection.EXCLUDED_TAG)
    }

    @Test
    fun `rejects when the day falls below the safe calorie floor`() {
        val strictFloor = DayTargets(BigDecimal(1900), BigDecimal(120), BigDecimal(1950))
        val result = SwapEngine.swap(catalog, SwapRequest(plan, 4, "sweet_potato", strictFloor, emptySet()))
        assertThat(result.rejections).containsExactly(Rejection.BELOW_KCAL_FLOOR)
    }

    @Test
    fun `tolerance limits are inclusive`() {
        // dia após a troca = 1907,1 kcal; alvo 2119 x 0,90 = 1907,1 exato: no limite, ainda é aceita
        val atLowerLimit = DayTargets(BigDecimal(2119), BigDecimal(120), BigDecimal(1500))
        val result = SwapEngine.swap(catalog, SwapRequest(plan, 4, "sweet_potato", atLowerLimit, emptySet()))
        assertThat(result.dayKcal).isEqualByComparingTo("1907.1")
        assertThat(result.accepted).isTrue()
    }
}
