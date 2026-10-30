package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.UserRepository
import com.heracles.platform.domain.AppUser
import com.heracles.platform.domain.UserRole
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class JdbcUserRepository(private val jdbc: JdbcTemplate) : UserRepository {
    private val mapper = RowMapper { rs, _ ->
        AppUser(rs.getObject("id", UUID::class.java), rs.getString("sub"), UserRole.valueOf(rs.getString("role")))
    }

    override fun provision(subject: String): AppUser {
        jdbc.update("insert into app_user (sub) values (?) on conflict (sub) do nothing", subject)
        return jdbc.queryForObject("select id, sub, role from app_user where sub = ?", mapper, subject)!!
    }

    override fun findById(id: UUID): AppUser? =
        jdbc.query("select id, sub, role from app_user where id = ?", mapper, id).firstOrNull()
}
