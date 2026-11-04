package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.api.ApiProblemException
import com.heracles.platform.api.CurrentUser
import com.heracles.platform.application.CancelErasure
import com.heracles.platform.application.ErasureNotFoundException
import com.heracles.platform.application.GetErasure
import com.heracles.platform.application.RequestErasure
import com.heracles.platform.application.port.out.AccountErasure
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class ErasureResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val requestedAt: String,
    /** The account is erased from this moment on, unless the person cancels before. */
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val dueAt: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = ["PENDING", "RUNNING"]) val status: String,
)

/** Not behind the health data consent: erasing is a right, and it must work for a person who revoked everything. */
@RestController
class ErasureController(private val request: RequestErasure, private val get: GetErasure, private val cancel: CancelErasure) {
    @Operation(operationId = "requestErasure", summary = "Asks to erase the caller's account, with 7 days to change their mind")
    @PostMapping("/api/v1/me/erasure-requests", produces = ["application/json"])
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun request(user: CurrentUser) = request(user.id).toResponse()

    @Operation(operationId = "getErasureRequest", summary = "The caller's open erasure request")
    @GetMapping("/api/v1/me/erasure-requests/current", produces = ["application/json"])
    fun current(user: CurrentUser): ErasureResponse = notFoundAs { get(user.id).toResponse() }

    @Operation(operationId = "cancelErasureRequest", summary = "Cancels the caller's pending erasure request")
    @DeleteMapping("/api/v1/me/erasure-requests/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(user: CurrentUser) {
        notFoundAs { cancel(user.id) }
    }

    private fun <T> notFoundAs(block: () -> T): T =
        try {
            block()
        } catch (e: ErasureNotFoundException) {
            throw ApiProblemException(HttpStatus.NOT_FOUND.value(), "erasure_not_found")
        }

    private fun AccountErasure.toResponse() = ErasureResponse(requestedAt.toString(), dueAt.toString(), status.name)
}
