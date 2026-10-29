package swap

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

/** Lê os arquivos de dados do protótipo (TSV com ';'; linhas com '#' são comentários). */
object DataLoader {
    private fun rows(path: Path): List<List<String>> =
        Files.readAllLines(path, Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split(';') }

    private fun tags(raw: String): Set<String> = raw.split(',').filter { it.isNotBlank() }.toSet()

    fun catalog(path: Path): Map<String, Food> =
        rows(path).associate { r ->
            r[0] to Food(r[0], r[1], r[2], BigDecimal(r[3]), BigDecimal(r[4]), BigDecimal(r[5]),
                BigDecimal(r[6]), BigDecimal(r[7]), tags(r[8]))
        }

    fun plan(path: Path): List<PlanItem> =
        rows(path).sortedBy { it[0].toInt() }.map { PlanItem(it[1], BigDecimal(it[2])) }

    data class Case(val id: String, val description: String, val request: SwapRequest,
                    val accepted: Boolean, val grams: BigDecimal?, val dayKcal: BigDecimal?,
                    val dayProteinG: BigDecimal?, val rejections: List<Rejection>, val notes: List<Note>)

    fun cases(path: Path, plan: List<PlanItem>): List<Case> =
        rows(path).map { r ->
            val request = SwapRequest(plan, r[6].toInt(), r[7],
                DayTargets(BigDecimal(r[2]), BigDecimal(r[3]), BigDecimal(r[4])), tags(r[5]))
            Case(r[0], r[1], request, r[8] == "ACEITA",
                r[9].takeIf { it.isNotBlank() }?.let(::BigDecimal),
                r[10].takeIf { it.isNotBlank() }?.let(::BigDecimal),
                r[11].takeIf { it.isNotBlank() }?.let(::BigDecimal),
                r[12].split(',').filter { it.isNotBlank() }.map(Rejection::valueOf),
                r[13].split(',').filter { it.isNotBlank() }.map(Note::valueOf))
        }
}
