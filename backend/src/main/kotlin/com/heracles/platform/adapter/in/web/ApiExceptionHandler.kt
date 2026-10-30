package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.domain.DeadLetterNotFoundException
import com.heracles.platform.domain.InvalidCursorException
import com.heracles.platform.domain.TaskNotFoundException
import com.heracles.platform.domain.UserNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.context.support.DefaultMessageSourceResolvable
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Every error leaves as Problem Details with a stable `code` and the `traceId`. The text never repeats what the
 * caller sent: Spring's own `detail` is dropped, because some of them quote the rejected value.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    override fun handleExceptionInternal(
        ex: Exception, body: Any?, headers: HttpHeaders, statusCode: HttpStatusCode, request: WebRequest,
    ): ResponseEntity<Any>? {
        // A hand-made body, not ProblemDetail: Spring fills `instance` of a ProblemDetail with the requested URL,
        // and `detail` with texts that may quote the rejected value. Same shape as ProblemResponse (security filters).
        val problem = LinkedHashMap<String, Any>()
        problem["type"] = "about:blank"
        problem["title"] = HttpStatus.resolve(statusCode.value())?.reasonPhrase ?: "Error"
        problem["status"] = statusCode.value()
        problem["code"] = codeOf(ex)
        TraceIds.current()?.let { problem["traceId"] = it }
        fieldNamesOf(ex)?.let { problem["fields"] = it }
        return ResponseEntity.status(statusCode).headers(headers).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem)
    }

    @ExceptionHandler(InvalidCursorException::class)
    fun invalidCursor(ex: InvalidCursorException, request: WebRequest): ResponseEntity<Any>? =
        handleExceptionInternal(ex, null, HttpHeaders(), HttpStatus.BAD_REQUEST, request)

    @ExceptionHandler(DeadLetterNotFoundException::class)
    fun deadLetterNotFound(ex: DeadLetterNotFoundException, request: WebRequest): ResponseEntity<Any>? =
        handleExceptionInternal(ex, null, HttpHeaders(), HttpStatus.NOT_FOUND, request)

    @ExceptionHandler(TaskNotFoundException::class)
    fun taskNotFound(ex: TaskNotFoundException, request: WebRequest): ResponseEntity<Any>? =
        handleExceptionInternal(ex, null, HttpHeaders(), HttpStatus.NOT_FOUND, request)

    @ExceptionHandler(UserNotFoundException::class)
    fun userNotFound(ex: UserNotFoundException, request: WebRequest): ResponseEntity<Any>? =
        handleExceptionInternal(ex, null, HttpHeaders(), HttpStatus.NOT_FOUND, request)

    @ExceptionHandler(Exception::class)
    fun unexpected(ex: Exception, request: WebRequest): ResponseEntity<Any>? {
        log.error("unexpected failure", ex)
        return handleExceptionInternal(ex, null, HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request)
    }

    private fun codeOf(ex: Exception): String = when (ex) {
        is MethodArgumentNotValidException, is HandlerMethodValidationException, is BindException -> ErrorCodes.VALIDATION_FAILED
        is HttpMessageNotReadableException -> ErrorCodes.MALFORMED_REQUEST
        is TypeMismatchException, is MissingServletRequestParameterException -> ErrorCodes.INVALID_PARAMETER
        is InvalidCursorException -> ErrorCodes.INVALID_CURSOR
        is NoResourceFoundException, is UserNotFoundException, is TaskNotFoundException, is DeadLetterNotFoundException -> ErrorCodes.NOT_FOUND
        is HttpRequestMethodNotSupportedException -> ErrorCodes.METHOD_NOT_ALLOWED
        is HttpMediaTypeNotSupportedException -> ErrorCodes.UNSUPPORTED_MEDIA_TYPE
        else -> if (ex is org.springframework.web.ErrorResponse && ex.statusCode.is4xxClientError) ErrorCodes.BAD_REQUEST else ErrorCodes.INTERNAL_ERROR
    }

    /** Names of the offending fields, never their values. */
    private fun fieldNamesOf(ex: Exception): List<String>? = when (ex) {
        is MethodArgumentNotValidException -> ex.bindingResult.fieldErrors.map { it.field }.distinct()
        is BindException -> ex.fieldErrors.map { it.field }.distinct()
        is HandlerMethodValidationException -> ex.parameterValidationResults
            .flatMap { it.resolvableErrors }.mapNotNull { (it as? DefaultMessageSourceResolvable)?.codes?.firstOrNull() }.distinct()
        else -> null
    }
}
