package com.heracles.platform.adapter.`in`.web

import org.slf4j.MDC

/** Stable error codes of the API. Clients switch on these, never on the text. */
object ErrorCodes {
    const val UNAUTHENTICATED = "unauthenticated"
    const val FORBIDDEN = "forbidden"
    const val VALIDATION_FAILED = "validation_failed"
    const val INVALID_PARAMETER = "invalid_parameter"
    const val MALFORMED_REQUEST = "malformed_request"
    const val INVALID_CURSOR = "invalid_cursor"
    const val NOT_FOUND = "not_found"
    const val METHOD_NOT_ALLOWED = "method_not_allowed"
    const val UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type"
    const val BAD_REQUEST = "bad_request"
    const val INTERNAL_ERROR = "internal_error"
}

/** The id of the trace of the current request, as put in the log context by the tracing bridge. */
internal object TraceIds {
    fun current(): String? = MDC.get("traceId")?.takeIf { it.isNotBlank() }
}
