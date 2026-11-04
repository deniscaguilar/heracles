package com.heracles.profile.domain

import java.time.LocalDate

/** Spec RF8 and CA11: closed values from the catalogs, short texts, a believable birth date. */
class AnamnesisValidator(private val catalog: Catalog) {
    /** Returns the input with its texts trimmed, or throws [AnamnesisInvalidException] with the field names. */
    fun validate(input: AnamnesisInput, today: LocalDate): AnamnesisInput {
        val invalid = mutableListOf<String>()
        if (!catalog.contains(CatalogKind.OBJECTIVE, input.objective)) invalid += "objective"
        if (!catalog.contains(CatalogKind.TRAINING_LEVEL, input.trainingLevel)) invalid += "trainingLevel"
        if (input.birthDate.isAfter(today) || input.birthDate.isBefore(today.minusYears(MAX_AGE_YEARS))) invalid += "birthDate"
        if (input.items.size > MAX_ITEMS) invalid += "items"
        if (!input.screening.keys.containsAll(ScreeningQuestion.entries)) invalid += "screening"

        val items = input.items.take(MAX_ITEMS).mapIndexed { i, item ->
            val trimmed = item.copy(otherText = item.otherText?.trim(), note = item.note?.trim())
            invalid += validateItem(trimmed).map { if (it.isEmpty()) "items[$i]" else "items[$i].$it" }
            trimmed
        }
        if (invalid.isNotEmpty()) throw AnamnesisInvalidException(invalid)
        return input.copy(items = items)
    }

    /** Field names of one item that are wrong; an empty name means the item as a whole. */
    private fun validateItem(item: ItemInput): List<String> {
        val invalid = mutableListOf<String>()
        fun shortText(name: String, value: String?, max: Int) {
            if (value != null && (value.isEmpty() || value.length > max)) invalid += name
        }
        shortText("otherText", item.otherText, MAX_TEXT)
        shortText("note", item.note, MAX_NOTE)

        when (item.kind) {
            ItemKind.INJURY -> {
                if (!catalog.contains(CatalogKind.BODY_REGION, item.bodyRegion)) invalid += "bodyRegion"
                if (item.catalogCode != null) invalid += "catalogCode"
                if (item.otherText != null) invalid += "otherText"
            }
            ItemKind.DISLIKED_FOOD -> {
                if (item.otherText == null) invalid += "otherText"
                if (item.catalogCode != null) invalid += "catalogCode"
                if (item.bodyRegion != null) invalid += "bodyRegion"
                if (item.note != null) invalid += "note"
            }
            else -> {
                if (item.catalogCode != null && item.otherText != null) invalid += ""
                else if (item.catalogCode == null && item.otherText == null) invalid += ""
                else if (item.catalogCode != null && !catalog.contains(item.kind.catalog!!, item.catalogCode)) invalid += "catalogCode"
                if (item.bodyRegion != null) invalid += "bodyRegion"
                if (item.note != null) invalid += "note"
            }
        }
        return invalid.distinct()
    }

    companion object {
        const val MAX_ITEMS = 100
        const val MAX_TEXT = 80
        const val MAX_NOTE = 200
        const val MAX_AGE_YEARS = 120L
    }
}
