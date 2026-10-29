package swap

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Regra de troca de alimento (protótipo da P2). Função pura: sem relógio, banco ou rede.
 *
 * Arredondamento (todas as contas em BigDecimal, nunca Double):
 *  1. Energia do item original = gramas x kcal/100 (exata, escala 6).
 *  2. Gramas brutas do substituto = energia original x 100 / kcal/100 g do substituto (escala 6, HALF_UP).
 *  3. Gramas = bruta arredondada ao múltiplo do passo do alimento (HALF_UP), depois limitada a [min, max].
 *  4. Totais do dia são somas exatas; só a saída é arredondada a 1 casa (HALF_UP).
 *  5. Tolerância de calorias: |dia| deve ficar entre alvo x 0,90 e alvo x 1,10 (limites inclusivos).
 */
object SwapEngine {
    private val HUNDRED = BigDecimal(100)
    private val LOWER = BigDecimal("0.90")
    private val UPPER = BigDecimal("1.10")
    private const val SCALE = 6

    fun swap(catalog: Map<String, Food>, request: SwapRequest): SwapResult {
        val original = request.plan[request.index]
        val originalFood = catalog.getValue(original.foodId)
        val substitute = catalog.getValue(request.substituteId)

        val early = mutableListOf<Rejection>()
        if (substitute.id == originalFood.id) early += Rejection.SAME_FOOD
        if (substitute.tags.any { it in request.excludedTags }) early += Rejection.EXCLUDED_TAG
        if (substitute.group != originalFood.group) early += Rejection.DIFFERENT_GROUP
        if (early.isNotEmpty()) return SwapResult(false, null, null, null, early, emptyList())

        val originalKcal = energy(original.grams, originalFood.kcalPer100g)
        val raw = originalKcal.multiply(HUNDRED).divide(substitute.kcalPer100g, SCALE, RoundingMode.HALF_UP)
        var grams = raw.divide(substitute.stepG, 0, RoundingMode.HALF_UP).multiply(substitute.stepG)

        val notes = mutableListOf<Note>()
        if (grams > substitute.maxPortionG) {
            grams = substitute.maxPortionG
            notes += Note.PORTION_CAPPED
        }
        if (grams < substitute.minPortionG) {
            grams = substitute.minPortionG
            notes += Note.PORTION_RAISED_TO_MIN
        }

        var dayKcal = BigDecimal.ZERO
        var dayProtein = BigDecimal.ZERO
        request.plan.forEachIndexed { i, item ->
            val food: Food
            val g: BigDecimal
            if (i == request.index) {
                food = substitute
                g = grams
            } else {
                food = catalog.getValue(item.foodId)
                g = item.grams
            }
            dayKcal = dayKcal.add(energy(g, food.kcalPer100g))
            dayProtein = dayProtein.add(energy(g, food.proteinPer100g))
        }

        val rejections = mutableListOf<Rejection>()
        if (dayKcal > request.targets.kcal.multiply(UPPER)) rejections += Rejection.ABOVE_TOLERANCE
        if (dayKcal < request.targets.kcal.multiply(LOWER)) rejections += Rejection.BELOW_TOLERANCE
        if (dayKcal < request.targets.kcalFloor) rejections += Rejection.BELOW_KCAL_FLOOR
        if (dayProtein < request.targets.proteinFloorG) rejections += Rejection.BELOW_PROTEIN_FLOOR

        return SwapResult(
            accepted = rejections.isEmpty(),
            grams = grams,
            dayKcal = dayKcal.setScale(1, RoundingMode.HALF_UP),
            dayProteinG = dayProtein.setScale(1, RoundingMode.HALF_UP),
            rejections = rejections,
            notes = notes,
        )
    }

    private fun energy(grams: BigDecimal, per100g: BigDecimal): BigDecimal =
        grams.multiply(per100g).divide(HUNDRED, SCALE, RoundingMode.UNNECESSARY)
}
