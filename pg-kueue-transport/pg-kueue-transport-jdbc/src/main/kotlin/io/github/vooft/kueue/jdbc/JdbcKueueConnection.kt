package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.KueueConnection
import kotlinx.coroutines.sync.Mutex
import java.sql.Connection

class JdbcKueueConnection(internal val connection: Connection) : KueueConnection<Connection> {
    private val mutex = Mutex()

    override val isClosed: Boolean get() = connection.isClosed

    override suspend fun acquire(): Connection {
        mutex.lock()
        return connection
    }

    override suspend fun release() {
        mutex.unlock()
    }
}
