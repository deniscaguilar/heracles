package com.heracles.support

import com.heracles.platform.adapter.out.QueueTablesIT
import com.heracles.platform.adapter.out.RowLevelSecurityIT
import kotlin.reflect.KClass

/**
 * Register here the test that proves user isolation for each table with a `user_id` column ("schema.table").
 * DatabaseCatalogIT fails for a table that is missing from this list (CA7).
 */
object IsolationTests {
    val covered: Map<String, KClass<*>> = mapOf(
        "platform.test_user_note" to RowLevelSecurityIT::class,
        "platform.test_health_sample" to RowLevelSecurityIT::class,
        "platform.task" to QueueTablesIT::class,
    )
}
