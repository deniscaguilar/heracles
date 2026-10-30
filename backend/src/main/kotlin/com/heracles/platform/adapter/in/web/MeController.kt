package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.GetMe
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class MeResponse(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val id: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val role: String,
)

@RestController
class MeController(private val getMe: GetMe) {

    @Operation(operationId = "getMe", summary = "Who am I: identifier and role of the caller")
    @GetMapping("/api/v1/me", produces = ["application/json"])
    fun me(authentication: Authentication): MeResponse {
        val user = (authentication as IdentityAuthentication).user
        val me = getMe(user.id)
        return MeResponse(me.id.toString(), me.role.name)
    }
}
