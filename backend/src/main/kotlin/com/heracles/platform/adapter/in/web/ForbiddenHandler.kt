package com.heracles.platform.adapter.`in`.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

/** Standard 403 (Problem Details, stable `code`). */
@Component
class ForbiddenHandler : AccessDeniedHandler {
    override fun handle(request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) {
        ProblemResponse.write(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden", ErrorCodes.FORBIDDEN)
    }
}
