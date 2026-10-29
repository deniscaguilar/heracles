package swap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Casos de aceitação da troca (P2): lidos de data/cases.tsv. Mudar a regra exige atualizar os casos. */
class AcceptanceCasesTest {
    private val data = Path.of("data")
    private val catalog = DataLoader.catalog(data.resolve("catalog.tsv"))
    private val plan = DataLoader.plan(data.resolve("plan.tsv"))
    private val cases = DataLoader.cases(data.resolve("cases.tsv"), plan)

    @Test
    fun `has exactly 20 cases`() {
        assertThat(cases).hasSize(20)
    }

    @Test
    fun `every case produces the expected outcome`() {
        cases.forEach { c ->
            val result = SwapEngine.swap(catalog, c.request)
            val label = "${c.id}: ${c.description}"
            assertThat(result.accepted).describedAs(label).isEqualTo(c.accepted)
            assertThat(result.rejections).describedAs(label).isEqualTo(c.rejections)
            assertThat(result.notes).describedAs(label).isEqualTo(c.notes)
            assertThat(result.grams).describedAs(label).isEqualTo(c.grams)
            assertThat(result.dayKcal).describedAs(label).isEqualTo(c.dayKcal)
            assertThat(result.dayProteinG).describedAs(label).isEqualTo(c.dayProteinG)
        }
    }
}
