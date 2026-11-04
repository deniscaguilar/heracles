package com.heracles.consent.adapter.`in`.web

import com.heracles.consent.api.ConsentType
import com.heracles.consent.application.CreateDraft
import com.heracles.consent.application.DiscardDraft
import com.heracles.consent.application.ListPolicies
import com.heracles.consent.application.PreviewVersion
import com.heracles.consent.application.PublishVersion
import com.heracles.consent.application.UpdateDraft
import com.heracles.consent.domain.DraftAlreadyExistsException
import com.heracles.consent.domain.DraftNotFoundException
import com.heracles.consent.domain.InvalidPolicyTextException
import com.heracles.consent.domain.PolicyVersionSummary
import com.heracles.platform.api.ApiProblemException
import com.heracles.platform.api.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class DraftRequest(
    @field:NotBlank @field:Size(max = 200) val title: String?,
    @field:NotBlank @field:Size(max = 60_000) val markdown: String?,
)

data class DraftCreatedResponse(@field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String)

data class PublishedResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val version: Int,
)

data class PolicyVersionSummaryResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val type: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) val version: Int?,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val title: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val status: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val createdAt: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) val publishedAt: String?,
    /** How many people hold this published version as their current acceptance; no identity. */
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val accepted: Long,
)

data class PoliciesResponse(@field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val items: List<PolicyVersionSummaryResponse>)

data class PolicyPreviewResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val summary: PolicyVersionSummaryResponse,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val markdown: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val blocks: List<BlockResponse>,
)

/** Backoffice of the policies. Everything under `/api/v1/admin` is for ADMIN only (AdminAuthorizationManager). */
@RestController
class PolicyAdminController(
    private val createDraft: CreateDraft,
    private val updateDraft: UpdateDraft,
    private val discardDraft: DiscardDraft,
    private val publishVersion: PublishVersion,
    private val previewVersion: PreviewVersion,
    private val listPolicies: ListPolicies,
) {
    @Operation(operationId = "listPolicies", summary = "Every policy version, drafts included, with how many people hold each (ADMIN only)")
    @GetMapping("/api/v1/admin/policies", produces = ["application/json"])
    fun list() = PoliciesResponse(listPolicies().map { it.toResponse() })

    @Operation(operationId = "createPolicyDraft", summary = "Start the draft of a new version of a policy (ADMIN only)")
    @PostMapping("/api/v1/admin/policies/{type}/draft", produces = ["application/json"])
    @ResponseStatus(HttpStatus.CREATED)
    fun create(user: CurrentUser, @PathVariable type: ConsentType, @Valid @RequestBody body: DraftRequest): DraftCreatedResponse =
        invalidTextAsProblem {
            try {
                DraftCreatedResponse(createDraft(user.id, type, body.title!!, body.markdown!!).toString())
            } catch (e: DraftAlreadyExistsException) {
                throw ApiProblemException(HttpStatus.CONFLICT.value(), "draft_already_exists")
            }
        }

    @Operation(operationId = "updatePolicyDraft", summary = "Change a draft (ADMIN only)")
    @PutMapping("/api/v1/admin/policy-drafts/{id}", produces = ["application/json"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun update(user: CurrentUser, @PathVariable id: UUID, @Valid @RequestBody body: DraftRequest) =
        invalidTextAsProblem { draftOrNotFound { updateDraft(user.id, id, body.title!!, body.markdown!!) } }

    @Operation(operationId = "discardPolicyDraft", summary = "Throw a draft away (ADMIN only)")
    @DeleteMapping("/api/v1/admin/policy-drafts/{id}", produces = ["application/json"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun discard(user: CurrentUser, @PathVariable id: UUID) = draftOrNotFound { discardDraft(user.id, id) }

    @Operation(operationId = "publishPolicyDraft", summary = "Publish a draft as the next version; it becomes the current one (ADMIN only)")
    @PostMapping("/api/v1/admin/policy-drafts/{id}/publish", produces = ["application/json"])
    fun publish(user: CurrentUser, @PathVariable id: UUID) = draftOrNotFound { PublishedResponse(id.toString(), publishVersion(user.id, id)) }

    @Operation(operationId = "previewPolicyVersion", summary = "A draft or published version as people will see it (ADMIN only)")
    @GetMapping("/api/v1/admin/policy-versions/{id}/preview", produces = ["application/json"])
    fun preview(@PathVariable id: UUID): PolicyPreviewResponse = draftOrNotFound {
        val preview = previewVersion(id)
        PolicyPreviewResponse(preview.document.summary.toResponse(), preview.document.markdown, preview.blocks.map { it.toResponse() })
    }

    private fun <T> draftOrNotFound(block: () -> T): T = try {
        block()
    } catch (e: DraftNotFoundException) {
        throw ApiProblemException(HttpStatus.NOT_FOUND.value(), ConsentErrorCodes.POLICY_NOT_FOUND)
    }

    private fun <T> invalidTextAsProblem(block: () -> T): T = try {
        block()
    } catch (e: InvalidPolicyTextException) {
        throw ApiProblemException(HttpStatus.BAD_REQUEST.value(), "policy_text_invalid", listOf("markdown"))
    }
}

private fun PolicyVersionSummary.toResponse() = PolicyVersionSummaryResponse(
    id = id.toString(), type = type.name, version = version, title = title,
    status = if (publishedAt == null) "DRAFT" else "PUBLISHED",
    createdAt = createdAt.toString(), publishedAt = publishedAt?.toString(), accepted = accepted,
)
