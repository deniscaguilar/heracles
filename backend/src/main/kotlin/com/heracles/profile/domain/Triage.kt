package com.heracles.profile.domain

import java.time.LocalDate
import java.time.Period

/** The outcome of the screening of one anamnesis. [reasons] are stable codes, never free text. */
data class Eligibility(val eligible: Boolean, val reasons: List<String>, val rulesVersion: Int)

/**
 * The provisional, conservative rules (spec RF10, question 5): only a plain "no" lets the profile through. They come from
 * `triage-rules.json`, so the nutritionist can correct the list without a code change; [version] is stored in each revision.
 */
data class TriageRules(
    val version: Int,
    val minimumAgeYears: Int,
    val blockingAnswers: Set<ScreeningAnswer>,
    val questions: Set<ScreeningQuestion>,
) {
    fun evaluate(birthDate: LocalDate, screening: Map<ScreeningQuestion, ScreeningAnswer>, today: LocalDate): Eligibility {
        val reasons = mutableListOf<String>()
        if (Period.between(birthDate, today).years < minimumAgeYears) reasons += UNDER_MINIMUM_AGE
        ScreeningQuestion.entries.filter { it in questions }.forEach { question ->
            val answer = screening[question]
            if (answer == null || answer in blockingAnswers) reasons += question.name
        }
        return Eligibility(reasons.isEmpty(), reasons, version)
    }

    companion object {
        const val UNDER_MINIMUM_AGE = "UNDER_MINIMUM_AGE"
    }
}
