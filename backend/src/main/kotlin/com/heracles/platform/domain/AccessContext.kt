package com.heracles.platform.domain

import java.util.UUID

/** Who a database transaction runs for, and with which scope (ADR-0003). */
enum class AccessScope(val databaseValue: String) {
    USER("user"),
    ADMIN("admin"),
    SYSTEM("system"),
}

data class AccessContext(val userId: UUID?, val scope: AccessScope) {
    init {
        when (scope) {
            AccessScope.USER -> require(userId != null) { "user scope requires a user id" }
            AccessScope.ADMIN, AccessScope.SYSTEM -> require(userId == null) { "${scope.databaseValue} scope carries no user id" }
        }
    }

    companion object {
        fun forUser(userId: UUID) = AccessContext(userId, AccessScope.USER)
        fun admin() = AccessContext(null, AccessScope.ADMIN)
        fun system() = AccessContext(null, AccessScope.SYSTEM)
    }
}
