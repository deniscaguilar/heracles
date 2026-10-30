package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.GetMe
import com.heracles.platform.application.ProvisionUser
import com.heracles.platform.domain.Identity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class MeResponse(val id: String, val role: String)

@RestController
class MeController(private val provisionUser: ProvisionUser, private val getMe: GetMe) {

    @GetMapping("/api/v1/me")
    fun me(authentication: Authentication): MeResponse {
        val user = provisionUser(authentication.principal as Identity)
        val me = getMe(user.id)
        return MeResponse(me.id.toString(), me.role.name)
    }
}
