package com.heracles.profile.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class CatalogKind { OBJECTIVE, TRAINING_LEVEL, ALLERGY, INTOLERANCE, RESTRICTION, FOOD_PREFERENCE, EQUIPMENT, BODY_REGION }

enum class BiologicalSex { FEMALE, MALE }

/** What an item of the anamnesis is. Its catalog (when it has one) is the kind of the same name. */
enum class ItemKind(val catalog: CatalogKind?) {
    ALLERGY(CatalogKind.ALLERGY),
    INTOLERANCE(CatalogKind.INTOLERANCE),
    RESTRICTION(CatalogKind.RESTRICTION),
    FOOD_PREFERENCE(CatalogKind.FOOD_PREFERENCE),
    EQUIPMENT(CatalogKind.EQUIPMENT),
    DISLIKED_FOOD(null),
    INJURY(null),
}

/** One entry of a list: a catalog code or a short text (`otherText`); an injury is a body region and an optional note. */
data class ItemInput(
    val kind: ItemKind,
    val catalogCode: String? = null,
    val otherText: String? = null,
    val bodyRegion: String? = null,
    val note: String? = null,
)

/** What the person fills in. Health data: never goes to a log, a trace, a metric or a queue message. */
data class AnamnesisInput(
    val objective: String,
    val trainingLevel: String,
    val birthDate: LocalDate,
    val biologicalSex: BiologicalSex?,
    val items: List<ItemInput>,
    /** The four screening answers (spec RF10); all are required, and only a plain "no" lets the profile through. */
    val screening: Map<ScreeningQuestion, ScreeningAnswer>,
)

/** A saved version of the anamnesis. The last one is the current one. */
data class AnamnesisRevision(
    val id: UUID,
    val revision: Int,
    val createdAt: Instant,
    val input: AnamnesisInput,
    val eligible: Boolean = true,
    val ineligibilityReasons: List<String> = emptyList(),
)

/** The allowed codes of each catalog. */
class Catalog(private val codes: Map<CatalogKind, Set<String>>) {
    fun contains(kind: CatalogKind, code: String?): Boolean = code != null && codes[kind]?.contains(code) == true
}

/** The anamnesis was refused. Carries the names of the offending fields, never their values (`anamnesis_invalid`). */
class AnamnesisInvalidException(val fields: List<String>) : RuntimeException("anamnesis_invalid: ${fields.joinToString()}")

class AnamnesisNotFoundException : RuntimeException("anamnesis_not_found")

/** The screening questions (spec RF10). The app only warns "procure um profissional"; it never diagnoses or advises. */
enum class ScreeningQuestion { PREGNANT_OR_NURSING, CONDITION_REQUIRING_CARE, MEDICATION_AFFECTING_PLAN, EATING_DISORDER_HISTORY }

enum class ScreeningAnswer { YES, NO, PREFER_NOT_TO_SAY }
