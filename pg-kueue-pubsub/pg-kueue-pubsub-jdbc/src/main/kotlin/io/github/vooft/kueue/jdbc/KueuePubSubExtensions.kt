package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.KueuePubSub
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.impl.KueuePubSubImpl
import java.sql.Connection
import javax.sql.DataSource

fun KueuePubSub.Companion.jdbc(dataSource: DataSource): KueuePubSub<Connection, JdbcKueueConnection> = KueuePubSubImpl(
    connectionProvider = JdbcDataSourceKueueConnectionProvider(dataSource),
    transport = JdbcKueueTransport()
)

suspend fun KueuePubSub<Connection, JdbcKueueConnection>.publish(topic: KueueTopic, message: String, jdbcConnection: Connection? = null) {
    publish(topic, message, jdbcConnection?.let { JdbcKueueConnection(it) })
}
