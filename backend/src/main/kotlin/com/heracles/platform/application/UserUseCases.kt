package com.heracles.platform.application

import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.application.port.out.UserRepository
import com.heracles.platform.domain.AccessContext
import com.heracles.platform.domain.AppUser
import com.heracles.platform.domain.Identity
import com.heracles.platform.domain.UserNotFoundException
import com.heracles.platform.domain.UserRole
import java.util.UUID

/** First call of a new `sub` creates the user as USER; later calls return the same one. */
class ProvisionUser(private val transactions: TransactionContext, private val users: UserRepository) {
    operator fun invoke(identity: Identity): AppUser =
        transactions.run(AccessContext.system()) { users.provision(identity.subject) }
}

/** What the API tells the caller about themselves: only the identifier and the role. */
data class Me(val id: UUID, val role: UserRole)

class GetMe(private val transactions: TransactionContext, private val users: UserRepository) {
    operator fun invoke(userId: UUID): Me {
        val user = transactions.run(AccessContext.forUser(userId)) { users.findById(userId) } ?: throw UserNotFoundException(userId)
        return Me(user.id, user.role)
    }
}
