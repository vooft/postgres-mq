package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.SimpleKueueConnection
import java.sql.Connection

class JdbcKueueConnection(internal val jdbcConnection: Connection) : SimpleKueueConnection<Connection>(jdbcConnection) {
    override val isClosed: Boolean get() = connection.isClosed
}
