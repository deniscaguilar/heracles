package com.heracles.profile.adapter.`in`.web

import com.heracles.consent.api.RequiresConsent
import com.heracles.platform.api.ApiProblemException
import com.heracles.platform.api.CurrentUser
import com.heracles.profile.application.GetCatalog
import com.heracles.profile.application.GetMyAnamnesis
import com.heracles.profile.application.GetRevision
import com.heracles.profile.application.ListRevisions
import com.heracles.profile.application.SaveAnamnesis
import com.heracles.profile.domain.AnamnesisInput
import com.heracles.profile.domain.AnamnesisInvalidException
import com.heracles.profile.domain.AnamnesisNotFoundException
import com.heracles.profile.domain.AnamnesisRevision
import com.heracles.profile.domain.BiologicalSex
import com.heracles.profile.domain.CatalogKind
import com.heracles.profile.domain.ItemInput
import com.heracles.profile.domain.ItemKind
import com.heracles.profile.domain.ScreeningAnswer
import com.heracles.profile.domain.ScreeningQuestion
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

object ProfileErrorCodes {
    const val ANAMNESIS_NOT_FOUND = "anamnesis_not_found"
    const val ANAMNESIS_INVALID = "anamnesis_invalid"
}

/** Health data. Never put a value of these classes in a log line, a trace, a metric or a message. */
data class AnamnesisItemBody(
    @field:NotNull val kind: ItemKind?,
    val catalogCode: String? = null,
    val otherText: String? = null,
    val bodyRegion: String? = null,
    val note: String? = null,
)

data class AnamnesisBody(
    @field:NotNull val objective: String?,
    @field:NotNull val trainingLevel: String?,
    @field:NotNull val birthDate: LocalDate?,
    val biologicalSex: BiologicalSex? = null,
    @field:Valid @field:Size(max = 100) val items: List<AnamnesisItemBody> = emptyList(),
    /** Answers to the screening questions; the keys are the question codes. */
    @field:NotNull val screening: Map<ScreeningQuestion, ScreeningAnswer>?,
)

data class AnamnesisItemResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val kind: ItemKind,
    @field:Schema(nullable = true) val catalogCode: String?,
    @field:Schema(nullable = true) val otherText: String?,
    @field:Schema(nullable = true) val bodyRegion: String?,
    @field:Schema(nullable = true) val note: String?,
)

data class AnamnesisResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val revision: Int,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val createdAt: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val objective: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val trainingLevel: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val birthDate: LocalDate,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) val biologicalSex: BiologicalSex?,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val items: List<AnamnesisItemResponse>,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val screening: Map<ScreeningQuestion, ScreeningAnswer>,
    /** False: the profile is outside the automatic plan and the client shows "procure um profissional" (spec RF10). */
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val eligible: Boolean,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val ineligibilityReasons: List<String>,
)

data class RevisionSummaryResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val revision: Int,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val createdAt: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val eligible: Boolean,
)

data class RevisionListResponse(@field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val items: List<RevisionSummaryResponse>)

data class CatalogResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val kind: CatalogKind,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val codes: List<String>,
)

/** Every route here reads or writes health data, so the whole controller is behind the consent barrier. */
@RestController
@RequiresConsent
class AnamnesisController(
    private val getMyAnamnesis: GetMyAnamnesis,
    private val saveAnamnesis: SaveAnamnesis,
    private val listRevisions: ListRevisions,
    private val getRevision: GetRevision,
) {
    @Operation(operationId = "getMyAnamnesis", summary = "The caller's current anamnesis (the last revision)")
    @GetMapping("/api/v1/me/anamnesis", produces = ["application/json"])
    fun mine(user: CurrentUser): AnamnesisResponse = notFoundAs { getMyAnamnesis(user.id).toResponse() }

    @Operation(operationId = "saveMyAnamnesis", summary = "Saves a new revision of the anamnesis; earlier ones stay readable")
    @PutMapping("/api/v1/me/anamnesis", produces = ["application/json"])
    fun save(user: CurrentUser, @Valid @RequestBody body: AnamnesisBody): AnamnesisResponse =
        try {
            saveAnamnesis(user.id, body.toInput()).toResponse()
        } catch (e: AnamnesisInvalidException) {
            throw ApiProblemException(HttpStatus.BAD_REQUEST.value(), ProfileErrorCodes.ANAMNESIS_INVALID, e.fields)
        }

    @Operation(operationId = "listAnamnesisRevisions", summary = "The caller's revisions, oldest first")
    @GetMapping("/api/v1/me/anamnesis/revisions", produces = ["application/json"])
    fun revisions(user: CurrentUser) =
        RevisionListResponse(listRevisions(user.id).map { RevisionSummaryResponse(it.revision, it.createdAt.toString(), it.eligible) })

    @Operation(operationId = "getAnamnesisRevision", summary = "One revision of the caller's anamnesis")
    @GetMapping("/api/v1/me/anamnesis/revisions/{revision}", produces = ["application/json"])
    fun revision(user: CurrentUser, @PathVariable revision: Int): AnamnesisResponse = notFoundAs { getRevision(user.id, revision).toResponse() }

    private fun <T> notFoundAs(block: () -> T): T =
        try {
            block()
        } catch (e: AnamnesisNotFoundException) {
            throw ApiProblemException(HttpStatus.NOT_FOUND.value(), ProfileErrorCodes.ANAMNESIS_NOT_FOUND)
        }
}

/** The catalogs are not health data; a person needs them to fill in the anamnesis, so there is no consent barrier. */
@RestController
class CatalogController(private val getCatalog: GetCatalog) {
    @Operation(operationId = "getCatalog", summary = "The codes of one anamnesis catalog, in display order")
    @GetMapping("/api/v1/catalogs/{kind}", produces = ["application/json"])
    fun catalog(@PathVariable kind: CatalogKind) = CatalogResponse(kind, getCatalog(kind))
}

internal fun AnamnesisBody.toInput() = AnamnesisInput(
    objective!!, trainingLevel!!, birthDate!!, biologicalSex,
    items.map { ItemInput(it.kind!!, it.catalogCode, it.otherText, it.bodyRegion, it.note) }, screening!!,
)

internal fun AnamnesisRevision.toResponse() = AnamnesisResponse(
    revision, createdAt.toString(), input.objective, input.trainingLevel, input.birthDate, input.biologicalSex,
    input.items.map { AnamnesisItemResponse(it.kind, it.catalogCode, it.otherText, it.bodyRegion, it.note) }, input.screening,
    eligible, ineligibilityReasons,
)
