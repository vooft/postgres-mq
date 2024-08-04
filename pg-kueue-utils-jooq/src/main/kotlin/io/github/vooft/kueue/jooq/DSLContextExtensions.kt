package io.github.vooft.kueue.jooq

import org.jooq.DSLContext
import java.sql.Connection

suspend fun DSLContext.withConnection(block: suspend (Connection) -> Unit) {
    val connectionProvider = configuration().connectionProvider()
    val connection = requireNotNull(connectionProvider.acquire()) { "Unable to acquire connection from DSLContext" }

    try {
        block(connection)
    } finally {
        connectionProvider.release(connection)
    }
}
