package com.heracles.platform.application.port.out

import com.heracles.platform.domain.AppUser
import java.util.UUID

interface UserRepository {
    /** Finds the user of [subject] or creates it as USER. Safe when two first calls race. Needs the system scope. */
    fun provision(subject: String): AppUser

    /** Row-level security decides what is visible: a user finds only themselves. */
    fun findById(id: UUID): AppUser?
}
