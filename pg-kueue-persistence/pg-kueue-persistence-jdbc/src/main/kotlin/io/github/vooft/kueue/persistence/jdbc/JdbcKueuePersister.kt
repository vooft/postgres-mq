package io.github.vooft.kueue.persistence.jdbc

import io.github.vooft.kueue.common.withVirtualThreadDispatcher
import io.github.vooft.kueue.jdbc.JdbcKueueConnection
import io.github.vooft.kueue.persistence.KueuePersister
import io.github.vooft.kueue.useUnwrapped
import java.sql.Connection
import java.time.OffsetDateTime
import java.time.ZoneOffset

class JdbcKueuePersister : KueuePersister<Connection, JdbcKueueConnection> {
    override suspend fun saveMessage(topic: String, message: String, kueueConnection: JdbcKueueConnection): Unit =
        withVirtualThreadDispatcher {
            kueueConnection.useUnwrapped { connection ->
                connection.prepareStatement("INSERT INTO kueue_events (topic, message, created_at) VALUES (?, ?, ?)").use { statement ->
                    statement.setString(1, topic)
                    statement.setString(2, message)
                    statement.setObject(3, OffsetDateTime.now(ZoneOffset.UTC))

                    statement.executeUpdate()
                }
            }
        }
}
