package com.heracles.support

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Test-only protected route, so authentication can be exercised before the real endpoints exist. */
@RestController
class ProbeController {
    @GetMapping("/api/v1/_probe")
    fun probe(authentication: Authentication): Map<String, String> = mapOf("subject" to authentication.name)
}
