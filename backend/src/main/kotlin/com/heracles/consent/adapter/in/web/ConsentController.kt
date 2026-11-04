package com.heracles.consent.adapter.`in`.web

import com.heracles.consent.api.ConsentType
import com.heracles.consent.application.AcceptRequiredConsents
import com.heracles.consent.application.GetCurrentPolicy
import com.heracles.consent.application.ListMyConsents
import com.heracles.consent.application.SetOptionalConsent
import com.heracles.consent.domain.Block
import com.heracles.consent.domain.Client
import com.heracles.consent.domain.ConsentStatus
import com.heracles.consent.domain.Inline
import com.heracles.consent.domain.NotOptionalConsentException
import com.heracles.consent.domain.PolicyBlocks
import com.heracles.consent.domain.PolicyNotFoundException
import com.heracles.consent.domain.PolicyVersion
import com.heracles.consent.domain.PolicyVersionOutdatedException
import com.heracles.platform.api.ApiProblemException
import com.heracles.platform.api.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

object ConsentErrorCodes {
    const val POLICY_NOT_FOUND = "policy_not_found"
    const val POLICY_VERSION_OUTDATED = "policy_version_outdated"
    const val CONSENT_NOT_OPTIONAL = "consent_not_optional"
    const val CONSENT_REQUIRED = "consent_required"
}

/** A piece of text for a client to draw. [type] is one of `text`, `strong`, `emphasis`, `link`, `break`. */
data class InlineResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val type: String,
    val text: String? = null,
    val href: String? = null,
    val children: List<InlineResponse>? = null,
)

/** [type] is one of `paragraph`, `heading`, `list`. A paragraph or heading has [children]; a list has [items]. */
data class BlockResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val type: String,
    val level: Int? = null,
    val ordered: Boolean? = null,
    val children: List<InlineResponse>? = null,
    val items: List<List<InlineResponse>>? = null,
)

data class PolicyResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val versionId: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val type: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val version: Int,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val title: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val blocks: List<BlockResponse>,
)

data class ConsentStatusResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val type: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val required: Boolean,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val granted: Boolean,
    /** The person must accept a published text now (a required consent that is missing or in an old version). */
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val pending: Boolean,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) val currentVersionId: String?,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) val currentVersion: Int?,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) val acceptedVersionId: String?,
)

data class MyConsentsResponse(@field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val items: List<ConsentStatusResponse>)

data class AcceptRequiredRequest(
    @field:NotEmpty @field:Size(max = 10) val versionIds: List<UUID>?,
    @field:NotNull val client: Client?,
)

data class SetOptionalRequest(
    @field:NotNull val granted: Boolean?,
    @field:NotNull val versionId: UUID?,
    @field:NotNull val client: Client?,
)

@RestController
class ConsentController(
    private val listMyConsents: ListMyConsents,
    private val getCurrentPolicy: GetCurrentPolicy,
    private val acceptRequired: AcceptRequiredConsents,
    private val setOptional: SetOptionalConsent,
) {
    @Operation(operationId = "listMyConsents", summary = "The seven consents of the caller and where each stands")
    @GetMapping("/api/v1/me/consents", produces = ["application/json"])
    fun mine(user: CurrentUser) = MyConsentsResponse(listMyConsents(user.id).map { it.toResponse() })

    @Operation(operationId = "getCurrentPolicy", summary = "The current published text of a policy, as blocks to draw")
    @GetMapping("/api/v1/policies/{type}/current", produces = ["application/json"])
    fun current(@PathVariable type: ConsentType): PolicyResponse =
        try {
            getCurrentPolicy(type).toResponse()
        } catch (e: PolicyNotFoundException) {
            throw ApiProblemException(HttpStatus.NOT_FOUND.value(), ConsentErrorCodes.POLICY_NOT_FOUND)
        }

    @Operation(operationId = "acceptRequiredConsents", summary = "Accept the three required consents, in the versions the caller saw")
    @PostMapping("/api/v1/me/required-consents", produces = ["application/json"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun acceptRequired(user: CurrentUser, @Valid @RequestBody body: AcceptRequiredRequest) {
        try {
            acceptRequired(user.id, body.versionIds!!.toSet(), body.client!!)
        } catch (e: PolicyVersionOutdatedException) {
            throw ApiProblemException(HttpStatus.CONFLICT.value(), ConsentErrorCodes.POLICY_VERSION_OUTDATED)
        }
    }

    @Operation(operationId = "setOptionalConsent", summary = "Turn an optional consent on or off, in the current version")
    @PutMapping("/api/v1/me/consents/{type}", produces = ["application/json"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setOptional(user: CurrentUser, @PathVariable type: ConsentType, @Valid @RequestBody body: SetOptionalRequest) {
        try {
            setOptional(user.id, type, body.granted!!, body.versionId!!, body.client!!)
        } catch (e: PolicyNotFoundException) {
            throw ApiProblemException(HttpStatus.NOT_FOUND.value(), ConsentErrorCodes.POLICY_NOT_FOUND)
        } catch (e: PolicyVersionOutdatedException) {
            throw ApiProblemException(HttpStatus.CONFLICT.value(), ConsentErrorCodes.POLICY_VERSION_OUTDATED)
        } catch (e: NotOptionalConsentException) {
            throw ApiProblemException(HttpStatus.BAD_REQUEST.value(), ConsentErrorCodes.CONSENT_NOT_OPTIONAL)
        }
    }
}

internal fun ConsentStatus.toResponse() = ConsentStatusResponse(
    type = type.name, required = required, granted = granted, pending = pending,
    currentVersionId = currentVersion?.id?.toString(), currentVersion = currentVersion?.version,
    acceptedVersionId = acceptedVersionId?.toString(),
)

internal fun PolicyVersion.toResponse() = PolicyResponse(id.toString(), type.name, version, title, PolicyBlocks.parse(markdown).map { it.toResponse() })

internal fun Block.toResponse(): BlockResponse = when (this) {
    is Block.Paragraph -> BlockResponse("paragraph", children = children.map { it.toResponse() })
    is Block.Heading -> BlockResponse("heading", level = level, children = children.map { it.toResponse() })
    is Block.ListBlock -> BlockResponse("list", ordered = ordered, items = items.map { item -> item.map { it.toResponse() } })
}

internal fun Inline.toResponse(): InlineResponse = when (this) {
    is Inline.Plain -> InlineResponse("text", text = text)
    is Inline.Strong -> InlineResponse("strong", children = children.map { it.toResponse() })
    is Inline.Emphasis -> InlineResponse("emphasis", children = children.map { it.toResponse() })
    is Inline.Link -> InlineResponse("link", href = href, children = children.map { it.toResponse() })
    Inline.LineBreak -> InlineResponse("break")
}
