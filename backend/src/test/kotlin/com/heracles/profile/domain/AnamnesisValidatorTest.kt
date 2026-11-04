package com.heracles.profile.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** E2-S6.2 (CA11): what is refused, and that the refusal names fields and never repeats a value. */
class AnamnesisValidatorTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val catalog = Catalog(
        mapOf(
            CatalogKind.OBJECTIVE to setOf("GENERAL_HEALTH", "MUSCLE_GAIN"),
            CatalogKind.TRAINING_LEVEL to setOf("BEGINNER"),
            CatalogKind.ALLERGY to setOf("PEANUT"),
            CatalogKind.EQUIPMENT to setOf("DUMBBELLS"),
            CatalogKind.BODY_REGION to setOf("KNEE"),
        ),
    )
    private val validator = AnamnesisValidator(catalog)

    private val allNo = ScreeningQuestion.entries.associateWith { ScreeningAnswer.NO }

    private fun input(screening: Map<ScreeningQuestion, ScreeningAnswer> = allNo, birth: LocalDate = LocalDate.of(1990, 5, 1), items: List<ItemInput> = emptyList(), objective: String = "GENERAL_HEALTH") =
        AnamnesisInput(objective, "BEGINNER", birth, BiologicalSex.FEMALE, items, screening)

    private fun invalidFields(input: AnamnesisInput): List<String> =
        assertThatThrownBy { validator.validate(input, today) }.isInstanceOf(AnamnesisInvalidException::class.java)
            .let { (it.actual() as AnamnesisInvalidException).fields }

    @Test
    fun `a complete anamnesis is accepted, with trimmed texts`() {
        val ok = validator.validate(
            input(items = listOf(ItemInput(ItemKind.ALLERGY, "PEANUT"), ItemInput(ItemKind.DISLIKED_FOOD, otherText = "  beterraba "), ItemInput(ItemKind.INJURY, bodyRegion = "KNEE", note = "dor ao agachar"))),
            today,
        )
        assertThat(ok.items[1].otherText).isEqualTo("beterraba")
    }

    @Test
    fun `a value outside the catalog is refused by field name, without the value`() {
        val fields = invalidFields(input(objective = "SECRET_VALUE_42", items = listOf(ItemInput(ItemKind.ALLERGY, "UNKNOWN_CODE"))))
        assertThat(fields).containsExactlyInAnyOrder("objective", "items[0].catalogCode")
        assertThat(fields.joinToString()).doesNotContain("SECRET_VALUE_42", "UNKNOWN_CODE")
    }

    @Test
    fun `free text above the limit, blank or on the wrong kind is refused`() {
        assertThat(invalidFields(input(items = listOf(ItemInput(ItemKind.DISLIKED_FOOD, otherText = "x".repeat(81)))))).containsExactly("items[0].otherText")
        assertThat(invalidFields(input(items = listOf(ItemInput(ItemKind.DISLIKED_FOOD, otherText = "   "))))).containsExactly("items[0].otherText")
        assertThat(invalidFields(input(items = listOf(ItemInput(ItemKind.INJURY, bodyRegion = "KNEE", note = "n".repeat(201)))))).containsExactly("items[0].note")
        // a catalog code and free text together, a region on a food item, an injury without region
        assertThat(invalidFields(input(items = listOf(ItemInput(ItemKind.ALLERGY, "PEANUT", otherText = "kiwi"))))).containsExactly("items[0]")
        assertThat(invalidFields(input(items = listOf(ItemInput(ItemKind.ALLERGY, "PEANUT", bodyRegion = "KNEE"))))).containsExactly("items[0].bodyRegion")
        assertThat(invalidFields(input(items = listOf(ItemInput(ItemKind.INJURY))))).containsExactly("items[0].bodyRegion")
    }

    @Test
    fun `a birth date in the future or implausibly old is refused`() {
        assertThat(invalidFields(input(birth = today.plusDays(1)))).containsExactly("birthDate")
        assertThat(invalidFields(input(birth = today.minusYears(121)))).containsExactly("birthDate")
        validator.validate(input(birth = today), today) // born today: not in the future (triage deals with age)
    }

    @Test
    fun `all four screening answers are required`() {
        assertThat(invalidFields(input(screening = allNo - ScreeningQuestion.PREGNANT_OR_NURSING))).containsExactly("screening")
        assertThat(invalidFields(input(screening = emptyMap()))).containsExactly("screening")
    }

    @Test
    fun `too many items are refused`() {
        val many = List(101) { ItemInput(ItemKind.EQUIPMENT, "DUMBBELLS") }
        assertThat(invalidFields(input(items = many))).containsExactly("items")
    }

    @Test
    fun `the exception message carries field names only`() {
        val e = AnamnesisInvalidException(listOf("objective"))
        assertThat(e.message).isEqualTo("anamnesis_invalid: objective")
    }
}
