package com.heracles.profile.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** E2-S7.1 (CA12): the provisional, conservative screening rules. */
class TriageTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val adult = LocalDate.of(1990, 5, 1)
    private val rules = TriageRules(version = 7, minimumAgeYears = 18, blockingAnswers = setOf(ScreeningAnswer.YES, ScreeningAnswer.PREFER_NOT_TO_SAY), questions = ScreeningQuestion.entries.toSet())

    private fun answers(vararg overrides: Pair<ScreeningQuestion, ScreeningAnswer>): Map<ScreeningQuestion, ScreeningAnswer> =
        ScreeningQuestion.entries.associateWith { ScreeningAnswer.NO } + overrides

    @Test
    fun `an adult who answers no to everything is eligible, and the rules version comes back`() {
        val result = rules.evaluate(adult, answers(), today)
        assertThat(result.eligible).isTrue()
        assertThat(result.reasons).isEmpty()
        assertThat(result.rulesVersion).isEqualTo(7)
    }

    @Test
    fun `each reason alone makes the profile ineligible, with its own code`() {
        ScreeningQuestion.entries.forEach { question ->
            val result = rules.evaluate(adult, answers(question to ScreeningAnswer.YES), today)
            assertThat(result.eligible).describedAs(question.name).isFalse()
            assertThat(result.reasons).containsExactly(question.name)
        }
        val minor = rules.evaluate(LocalDate.of(2010, 1, 1), answers(), today)
        assertThat(minor.eligible).isFalse()
        assertThat(minor.reasons).containsExactly("UNDER_MINIMUM_AGE")
    }

    @Test
    fun `reasons combine`() {
        val result = rules.evaluate(
            LocalDate.of(2012, 1, 1),
            answers(ScreeningQuestion.EATING_DISORDER_HISTORY to ScreeningAnswer.YES, ScreeningQuestion.PREGNANT_OR_NURSING to ScreeningAnswer.YES), today,
        )
        assertThat(result.reasons).containsExactlyInAnyOrder("UNDER_MINIMUM_AGE", "EATING_DISORDER_HISTORY", "PREGNANT_OR_NURSING")
    }

    @Test
    fun `when in doubt the profile is out of the automatic plan`() {
        // "prefer not to say" and an unanswered question both block
        assertThat(rules.evaluate(adult, answers(ScreeningQuestion.MEDICATION_AFFECTING_PLAN to ScreeningAnswer.PREFER_NOT_TO_SAY), today).eligible).isFalse()
        val missing = rules.evaluate(adult, answers() - ScreeningQuestion.CONDITION_REQUIRING_CARE, today)
        assertThat(missing.eligible).isFalse()
        assertThat(missing.reasons).containsExactly("CONDITION_REQUIRING_CARE")
    }

    @Test
    fun `the 18th birthday is the first eligible day`() {
        assertThat(rules.evaluate(today.minusYears(18), answers(), today).eligible).isTrue()
        assertThat(rules.evaluate(today.minusYears(18).plusDays(1), answers(), today).eligible).isFalse()
    }

    @Test
    fun `a question the rules do not list is not evaluated`() {
        val lenient = rules.copy(questions = setOf(ScreeningQuestion.PREGNANT_OR_NURSING))
        assertThat(lenient.evaluate(adult, answers(ScreeningQuestion.EATING_DISORDER_HISTORY to ScreeningAnswer.YES), today).eligible).isTrue()
    }
}
