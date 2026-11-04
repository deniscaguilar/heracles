package com.heracles.consent.adapter.`in`.web

import com.heracles.consent.api.RequiresConsent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * E2-S3.4 (CA1): every route of a module that holds health data carries the consent barrier. A new route in `profile`
 * (and later `exams`, `training`, `nutrition`) without `@RequiresConsent` fails here.
 */
@SpringBootTest
class HealthRoutesBarrierIT(@Autowired @Qualifier("requestMappingHandlerMapping") private val mapping: RequestMappingHandlerMapping) {
    /** Modules whose routes are health data; each route must be behind the barrier unless listed in [OPEN]. */
    private val healthPackages = listOf("com.heracles.profile.")

    /** Routes a person needs to reach the consent itself, or that carry no health data. Keep this list short and reasoned. */
    private val open = setOf("com.heracles.profile.adapter.in.web.CatalogController")

    private fun unprotected(handlers: Collection<HandlerMethod>, packages: List<String> = healthPackages): List<String> = handlers
        .filter { h -> packages.any { h.beanType.name.startsWith(it) } && h.beanType.name !in open }
        .filter { h ->
            AnnotatedElementUtils.findMergedAnnotation(h.method, RequiresConsent::class.java) == null &&
                AnnotatedElementUtils.findMergedAnnotation(h.beanType, RequiresConsent::class.java) == null
        }
        .map { "${it.beanType.simpleName}.${it.method.name}" }

    @Test
    fun `every health route is behind the consent barrier`() {
        val handlers = mapping.handlerMethods.values
        assertThat(handlers.count { it.beanType.name.startsWith("com.heracles.profile.") }).describedAs("profile routes found").isGreaterThan(3)
        assertThat(unprotected(handlers)).isEmpty()
    }

    @Test
    fun `the check fails for a health route without the annotation`() {
        @RestController
        class Unprotected {
            @GetMapping("/x") fun leak() = "x"
        }
        // the same rule, pointed at this test's package, where the class above lives
        val handler = HandlerMethod(Unprotected(), Unprotected::class.java.getMethod("leak"))
        val failing = unprotected(listOf(handler), listOf(HealthRoutesBarrierIT::class.java.packageName + "."))
        assertThat(failing).containsExactly("Unprotected.leak")
    }
}
