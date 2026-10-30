package com.heracles.platform.domain

import java.util.UUID

enum class UserRole { USER, ADMIN, MODERATOR }

data class AppUser(val id: UUID, val subject: String, val role: UserRole)

class UserNotFoundException(id: UUID) : RuntimeException("user not found: $id")
