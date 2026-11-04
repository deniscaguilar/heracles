package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.api.ApiProblemException
import com.heracles.platform.api.CurrentUser
import com.heracles.platform.application.ExportExpiredException
import com.heracles.platform.application.ExportNotReadyException
import com.heracles.platform.application.GetExport
import com.heracles.platform.application.RequestExport
import com.heracles.platform.domain.TaskNotFoundException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class ExportRequestedResponse(@field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val taskId: String)

data class ExportReadyResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val url: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val fileExpiresAt: String,
)

/** Not behind the health data consent: the person must be able to take their data out even after revoking it. */
@RestController
class ExportController(private val requestExport: RequestExport, private val getExport: GetExport) {
    @Operation(operationId = "requestExport", summary = "Asks for a file with all the data the product keeps about the caller")
    @PostMapping("/api/v1/me/exports", produces = ["application/json"])
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun request(user: CurrentUser) = ExportRequestedResponse(requestExport(user.id).toString())

    @Operation(operationId = "getExport", summary = "A 15-minute link to the export file, once it is ready")
    @GetMapping("/api/v1/me/exports/{taskId}", produces = ["application/json"])
    fun get(user: CurrentUser, @PathVariable taskId: UUID): ExportReadyResponse =
        try {
            getExport(user.id, taskId).let { ExportReadyResponse(it.url, it.expiresAt.toString()) }
        } catch (e: TaskNotFoundException) {
            throw ApiProblemException(HttpStatus.NOT_FOUND.value(), "task_not_found")
        } catch (e: ExportNotReadyException) {
            throw ApiProblemException(HttpStatus.CONFLICT.value(), "export_not_ready")
        } catch (e: ExportExpiredException) {
            throw ApiProblemException(HttpStatus.GONE.value(), "export_expired")
        }
}
