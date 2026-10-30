package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.application.GetMe
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class MeResponse(val id: String, val role: String)

@RestController
class MeController(private val getMe: GetMe) {

    @GetMapping("/api/v1/me")
    fun me(authentication: Authentication): MeResponse {
        val user = (authentication as IdentityAuthentication).user
        val me = getMe(user.id)
        return MeResponse(me.id.toString(), me.role.name)
    }
}
