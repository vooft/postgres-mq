package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.KueueEventPersister
import io.github.vooft.kueue.KueueTopic
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class JdbcKueueEventPersister : KueueEventPersister<JdbcKueueConnection> {
    override suspend fun persist(kueueConnection: JdbcKueueConnection, topic: KueueTopic, message: String) {
        kueueConnection.useUnwrapped { connection ->
            connection.prepareStatement("INSERT INTO kueue_events (id, topic, message, created_at) VALUES (?, ?, ?, ?)").use {
                it.setObject(1, UUID.randomUUID())
                it.setString(2, topic.topic)
                it.setString(3, message)
                it.setObject(4, OffsetDateTime.now(ZoneOffset.UTC))

                it.execute()
            }
        }
    }
}
