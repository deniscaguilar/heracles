package com.heracles.platform.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class BackoffTest {
    @Test
    fun `the wait doubles with each failed attempt`() {
        assertThat((1..5).map { Backoff.delay(it) }).containsExactly(
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(20), Duration.ofSeconds(40), Duration.ofSeconds(80),
        )
    }

    @Test
    fun `the wait stops growing at the ceiling, however many attempts`() {
        assertThat(Backoff.delay(30)).isEqualTo(Backoff.MAX)
        assertThat(Backoff.delay(Int.MAX_VALUE)).isEqualTo(Backoff.MAX)
        assertThat(Backoff.MAX).isEqualTo(Duration.ofMinutes(15))
    }

    @Test
    fun `fewer than one attempt waits the base`() {
        assertThat(Backoff.delay(0)).isEqualTo(Duration.ofSeconds(5))
    }
}
