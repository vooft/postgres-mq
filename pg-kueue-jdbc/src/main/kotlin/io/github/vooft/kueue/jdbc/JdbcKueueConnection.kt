package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.SimpleKueueConnection
import org.postgresql.core.BaseConnection

class JdbcKueueConnection(internal val jdbcConnection: BaseConnection) : SimpleKueueConnection<BaseConnection>(jdbcConnection) {
    override val isClosed: Boolean get() = connection.isClosed
}
