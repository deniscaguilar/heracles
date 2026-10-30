package com.heracles.platform.config

import com.heracles.platform.adapter.`in`.web.RouteLogInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(private val routeLogInterceptor: RouteLogInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(routeLogInterceptor)
    }
}
