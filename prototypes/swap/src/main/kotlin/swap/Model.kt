package swap

import java.math.BigDecimal

data class Food(
    val id: String,
    val name: String,
    val group: String,
    val kcalPer100g: BigDecimal,
    val proteinPer100g: BigDecimal,
    val minPortionG: BigDecimal,
    val maxPortionG: BigDecimal,
    val stepG: BigDecimal,
    val tags: Set<String>,
)

data class PlanItem(val foodId: String, val grams: BigDecimal)

data class DayTargets(
    val kcal: BigDecimal,
    val proteinFloorG: BigDecimal,
    val kcalFloor: BigDecimal,
)

data class SwapRequest(
    val plan: List<PlanItem>,
    val index: Int,
    val substituteId: String,
    val targets: DayTargets,
    val excludedTags: Set<String>,
)

/** Motivos que rejeitam a troca. */
enum class Rejection {
    SAME_FOOD,
    EXCLUDED_TAG,
    DIFFERENT_GROUP,
    ABOVE_TOLERANCE,
    BELOW_TOLERANCE,
    BELOW_KCAL_FLOOR,
    BELOW_PROTEIN_FLOOR,
}

/** Ajustes informativos; não rejeitam a troca. */
enum class Note { PORTION_CAPPED, PORTION_RAISED_TO_MIN }

data class SwapResult(
    val accepted: Boolean,
    val grams: BigDecimal?,
    val dayKcal: BigDecimal?,
    val dayProteinG: BigDecimal?,
    val rejections: List<Rejection>,
    val notes: List<Note>,
)
