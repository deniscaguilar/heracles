package com.heracles.support

import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Test-only protected route, so authentication can be exercised before the real endpoints exist. */
@RestController
class ProbeController {
    private val log = LoggerFactory.getLogger(ProbeController::class.java)

    @GetMapping("/api/v1/_probe")
    fun probe(authentication: Authentication): Map<String, String> {
        log.info("probe handled")
        return mapOf("subject" to authentication.name)
    }

    @GetMapping("/api/v1/_boom")
    fun boom(): Nothing = throw IllegalStateException("boom")

    @GetMapping("/api/v1/admin/_probe")
    fun adminProbe(): Map<String, String> = mapOf("area" to "admin")
}
