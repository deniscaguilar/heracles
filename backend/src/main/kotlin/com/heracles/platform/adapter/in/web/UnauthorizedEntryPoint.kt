package com.heracles.platform.adapter.`in`.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/** Standard 401 (Problem Details, stable `code`). */
@Component
class UnauthorizedEntryPoint : AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        respond(response)
    }

    fun respond(response: HttpServletResponse) {
        response.setHeader("WWW-Authenticate", "Bearer")
        ProblemResponse.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", ErrorCodes.UNAUTHENTICATED)
    }
}
