package com.heracles.platform.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment

class RuntimeRoleIT {

    @Nested
    @SpringBootTest(properties = ["spring.profiles.active=api"])
    inner class ApiProfile(@Autowired private val context: ApplicationContext) {
        @Test
        fun `api profile starts the api and not the worker`() {
            assertThat(context.getBeansOfType(ApiRuntime::class.java)).hasSize(1)
            assertThat(context.getBeansOfType(WorkerRuntime::class.java)).isEmpty()
        }
    }

    @Nested
    @SpringBootTest(properties = ["spring.profiles.active=worker"])
    inner class WorkerProfile(@Autowired private val context: ApplicationContext) {
        @Test
        fun `worker profile starts the worker and not the api`() {
            assertThat(context.getBeansOfType(WorkerRuntime::class.java)).hasSize(1)
            assertThat(context.getBeansOfType(ApiRuntime::class.java)).isEmpty()
        }
    }

    @Nested
    @SpringBootTest
    inner class DefaultProfile(
        @Autowired private val context: ApplicationContext,
        @Autowired private val environment: Environment,
    ) {
        @Test
        fun `default profile is all and starts both`() {
            assertThat(environment.activeProfiles).isEmpty()
            assertThat(environment.defaultProfiles).containsExactly("all")
            assertThat(context.getBeansOfType(ApiRuntime::class.java)).hasSize(1)
            assertThat(context.getBeansOfType(WorkerRuntime::class.java)).hasSize(1)
        }
    }

    @Nested
    @SpringBootTest(properties = ["spring.profiles.active=all"])
    inner class ExplicitAllProfile(@Autowired private val context: ApplicationContext) {
        @Test
        fun `all profile starts both`() {
            assertThat(context.getBeansOfType(ApiRuntime::class.java)).hasSize(1)
            assertThat(context.getBeansOfType(WorkerRuntime::class.java)).hasSize(1)
        }
    }
}
