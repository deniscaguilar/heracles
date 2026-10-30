package com.heracles.platform.adapter.`in`.web

import jakarta.servlet.http.HttpServletResponse

/** Problem Details body written by hand where Spring MVC is not in the way (security filters). Same shape as ApiExceptionHandler. */
internal object ProblemResponse {
    fun write(response: HttpServletResponse, status: Int, title: String, code: String) {
        response.status = status
        response.contentType = "application/problem+json"
        response.characterEncoding = "UTF-8"
        val trace = TraceIds.current()?.let { ""","traceId":"$it"""" }.orEmpty()
        response.writer.write("""{"type":"about:blank","title":"$title","status":$status,"code":"$code"$trace}""")
    }
}
