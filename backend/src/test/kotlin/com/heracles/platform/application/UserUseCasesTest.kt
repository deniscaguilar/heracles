package com.heracles.platform.application

import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.application.port.out.UserRepository
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.AccessScope
import com.heracles.platform.domain.AppUser
import com.heracles.platform.domain.Identity
import com.heracles.platform.domain.UserNotFoundException
import com.heracles.platform.domain.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

private class FakeTransactionContext : TransactionContext {
    val contexts = mutableListOf<AccessContext>()
    override fun <T> run(context: AccessContext, block: () -> T): T {
        contexts += context
        return block()
    }
}

private class FakeUserRepository : UserRepository {
    val users = mutableListOf<AppUser>()
    override fun provision(subject: String): AppUser =
        users.firstOrNull { it.subject == subject } ?: AppUser(UUID.randomUUID(), subject, UserRole.USER).also { users += it }
    override fun findById(id: UUID): AppUser? = users.firstOrNull { it.id == id }
}

class UserUseCasesTest {
    private val transactions = FakeTransactionContext()
    private val users = FakeUserRepository()
    private val provisionUser = ProvisionUser(transactions, users)
    private val getMe = GetMe(transactions, users)

    @Test
    fun `a new subject becomes a USER`() {
        val user = provisionUser(Identity("sub-new"))
        assertThat(user.role).isEqualTo(UserRole.USER)
        assertThat(user.subject).isEqualTo("sub-new")
    }

    @Test
    fun `provisioning the same subject again returns the same user`() {
        val first = provisionUser(Identity("sub-1"))
        val second = provisionUser(Identity("sub-1"))
        assertThat(second.id).isEqualTo(first.id)
        assertThat(users.users).hasSize(1)
    }

    @Test
    fun `provisioning runs in the system scope because the user id is not known yet`() {
        provisionUser(Identity("sub-1"))
        assertThat(transactions.contexts).containsExactly(AccessContext.system())
    }

    @Test
    fun `me is read in the scope of that very user and carries only id and role`() {
        val user = provisionUser(Identity("sub-1"))
        val me = getMe(user.id)
        assertThat(me).isEqualTo(Me(user.id, UserRole.USER))
        assertThat(transactions.contexts.last()).isEqualTo(AccessContext(user.id, AccessScope.USER))
    }

    @Test
    fun `me of an unknown user is not found`() {
        assertThatThrownBy { getMe(UUID.randomUUID()) }.isInstanceOf(UserNotFoundException::class.java)
    }
}
