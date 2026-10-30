package com.heracles.support

import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
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

    data class ProbeBody(@field:NotBlank @field:Size(max = 3) val name: String?)

    @PostMapping("/api/v1/_validated")
    fun validated(@Valid @RequestBody body: ProbeBody): Map<String, String> = mapOf("ok" to "true")

    @GetMapping("/api/v1/_number")
    fun number(@RequestParam n: Int): Map<String, Int> = mapOf("n" to n)
}
