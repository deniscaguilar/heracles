package com.heracles.platform.adapter.`in`.web

import com.heracles.platform.api.CurrentUser
import com.heracles.platform.api.CurrentUserProvider
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/** Reads the caller from the security context, for the argument resolver and for interceptors of other modules. */
@Component
class SecurityCurrentUserProvider : CurrentUserProvider {
    override fun current(): CurrentUser? =
        (SecurityContextHolder.getContext().authentication as? IdentityAuthentication)?.user?.let { CurrentUser(it.id, it.role.name) }
}

/** Lets controllers of other modules ask for a [CurrentUser] without knowing how the caller was authenticated. */
@Component
class CurrentUserArgumentResolver(private val provider: CurrentUserProvider) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == CurrentUser::class.java

    override fun resolveArgument(
        parameter: MethodParameter, mavContainer: ModelAndViewContainer?, webRequest: NativeWebRequest, binderFactory: WebDataBinderFactory?,
    ): Any {
        return provider.current() ?: throw IllegalStateException("no authenticated user on a protected route")
    }
}
