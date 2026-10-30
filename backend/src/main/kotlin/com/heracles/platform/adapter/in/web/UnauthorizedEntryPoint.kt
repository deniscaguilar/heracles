package com.heracles.platform.adapter.`in`.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/** Standard 401 (Problem Details, stable `code`). The traceId is added with the global error handler (E1-S5.1). */
@Component
class UnauthorizedEntryPoint : AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        respond(response)
    }

    fun respond(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.setHeader("WWW-Authenticate", "Bearer")
        response.contentType = "application/problem+json"
        response.characterEncoding = "UTF-8"
        response.writer.write("""{"type":"about:blank","title":"Unauthorized","status":401,"code":"unauthenticated"}""")
    }
}
