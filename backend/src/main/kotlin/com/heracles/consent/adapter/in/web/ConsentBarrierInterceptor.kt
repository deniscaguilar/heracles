package com.heracles.consent.adapter.`in`.web

import com.heracles.consent.api.ConsentChecker
import com.heracles.consent.api.RequiresConsent
import com.heracles.platform.api.ApiProblemException
import com.heracles.platform.api.CurrentUserProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * The barrier before health data (spec RF5): a route marked [RequiresConsent] answers `403 consent_required` before the
 * controller runs, so no health query is made. Fail closed: no authenticated user, or any doubt, is a refusal.
 */
@Component
class ConsentBarrierInterceptor(private val checker: ConsentChecker, private val currentUser: CurrentUserProvider) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true
        val required = AnnotatedElementUtils.findMergedAnnotation(handler.method, RequiresConsent::class.java)
            ?: AnnotatedElementUtils.findMergedAnnotation(handler.beanType, RequiresConsent::class.java)
            ?: return true
        val user = currentUser.current() ?: throw ApiProblemException(HttpStatus.FORBIDDEN.value(), ConsentErrorCodes.CONSENT_REQUIRED)
        if (!checker.hasConsented(user.id, required.type)) {
            throw ApiProblemException(HttpStatus.FORBIDDEN.value(), ConsentErrorCodes.CONSENT_REQUIRED)
        }
        return true
    }
}
