package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.common.withVirtualThreadDispatcher
import org.intellij.lang.annotations.Language
import org.postgresql.core.BaseConnection

internal suspend fun BaseConnection.execute(@Language("SQL") query: String) = withVirtualThreadDispatcher {
    createStatement().use {
        it.execute(query)
    }
}

internal suspend fun <T> JdbcKueueConnection.useUnwrapped(block: suspend (BaseConnection) -> T): T =
    block(jdbcConnection.unwrap(BaseConnection::class.java))
