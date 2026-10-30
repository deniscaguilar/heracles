package com.heracles.platform.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Marks beans that run only where the HTTP API runs (profiles `api` and `all`). */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Profile("api | all")
annotation class ApiOnly

/** Marks beans that run only where background processing runs (profiles `worker` and `all`). */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Profile("worker | all")
annotation class WorkerOnly

/** Present when this process serves the HTTP API. */
class ApiRuntime

/** Present when this process runs outbox publishing, queue consumers and scheduled tasks. */
class WorkerRuntime

@Configuration(proxyBeanMethods = false)
class RuntimeRoleConfig {
    @Bean
    @ApiOnly
    fun apiRuntime() = ApiRuntime()

    @Bean
    @WorkerOnly
    fun workerRuntime() = WorkerRuntime()
}
