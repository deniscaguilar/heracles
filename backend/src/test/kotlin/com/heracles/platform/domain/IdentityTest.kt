package com.heracles.platform.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class IdentityTest {
    @Test
    fun `identity carries the subject of the token`() {
        assertThat(Identity("abc-123").subject).isEqualTo("abc-123")
    }

    @Test
    fun `blank subject is refused`() {
        assertThatThrownBy { Identity(" ") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Identity("") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
