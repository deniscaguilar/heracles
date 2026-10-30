package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.TaskReader
import com.heracles.platform.domain.TaskView
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class JdbcTaskReader(private val jdbc: JdbcTemplate) : TaskReader {
    // Only the columns a screen needs: not the error code, the attempts or the type.
    override fun find(id: UUID): TaskView? =
        jdbc.query("select id, status, result_ref from task where id = ?", { rs, _ ->
            TaskView(rs.getObject("id", UUID::class.java), rs.getString("status"), rs.getString("result_ref"))
        }, id).firstOrNull()
}
