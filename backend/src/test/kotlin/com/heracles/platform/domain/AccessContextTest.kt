package com.heracles.platform.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class AccessContextTest {

    @Test
    fun `user context carries the user id and the user scope`() {
        val id = UUID.randomUUID()
        val context = AccessContext.forUser(id)
        assertThat(context.userId).isEqualTo(id)
        assertThat(context.scope).isEqualTo(AccessScope.USER)
    }

    @Test
    fun `admin and system contexts carry no user id`() {
        assertThat(AccessContext.admin()).isEqualTo(AccessContext(null, AccessScope.ADMIN))
        assertThat(AccessContext.system()).isEqualTo(AccessContext(null, AccessScope.SYSTEM))
    }

    @Test
    fun `user scope without a user id is refused`() {
        assertThatThrownBy { AccessContext(null, AccessScope.USER) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `admin and system scopes with a user id are refused`() {
        val id = UUID.randomUUID()
        assertThatThrownBy { AccessContext(id, AccessScope.ADMIN) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { AccessContext(id, AccessScope.SYSTEM) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `scopes map to the database values of ADR-0003`() {
        assertThat(AccessScope.entries.map { it.databaseValue }).containsExactly("user", "admin", "system")
    }
}
