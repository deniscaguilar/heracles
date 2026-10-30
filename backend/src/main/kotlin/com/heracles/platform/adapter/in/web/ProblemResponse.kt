package com.heracles.platform.adapter.`in`.web

import jakarta.servlet.http.HttpServletResponse

/** Problem Details body with a stable `code`. The traceId joins when the global error handler arrives (E1-S5.1). */
internal object ProblemResponse {
    fun write(response: HttpServletResponse, status: Int, title: String, code: String) {
        response.status = status
        response.contentType = "application/problem+json"
        response.characterEncoding = "UTF-8"
        response.writer.write("""{"type":"about:blank","title":"$title","status":$status,"code":"$code"}""")
    }
}
