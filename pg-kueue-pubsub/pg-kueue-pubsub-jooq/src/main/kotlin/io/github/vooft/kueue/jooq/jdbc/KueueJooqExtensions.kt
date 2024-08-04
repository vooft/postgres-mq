package io.github.vooft.kueue.jooq.jdbc

import io.github.vooft.kueue.KueuePubSub
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.impl.KueuePubSubImpl
import io.github.vooft.kueue.jdbc.JdbcDataSourceKueueConnectionProvider
import io.github.vooft.kueue.jdbc.JdbcKueueConnection
import io.github.vooft.kueue.jdbc.JdbcKueueTransport
import org.jooq.DSLContext
import java.sql.Connection
import javax.sql.DataSource

fun KueuePubSub.Companion.jooq(dataSource: DataSource) = KueuePubSubImpl(
    connectionProvider = JdbcDataSourceKueueConnectionProvider(dataSource),
    transport = JdbcKueueTransport()
)

suspend fun KueuePubSub<Connection, JdbcKueueConnection>.publish(topic: KueueTopic, message: String, transactionalDsl: DSLContext) {
    val connectionProvider = transactionalDsl.configuration().connectionProvider()
    val connection = requireNotNull(connectionProvider.acquire()) { "Unable to acquire connection from DSLContext" }

    try {
        publish(topic, message, JdbcKueueConnection(connection))
    } finally {
        connectionProvider.release(connection)
    }
}
