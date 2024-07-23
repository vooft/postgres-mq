package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.KueueConnection
import io.github.vooft.kueue.KueueConnectionFactory
import org.postgresql.core.BaseConnection
import org.postgresql.ds.PGSimpleDataSource

internal class JdbcKueueConnectionFactory(private val dataSource: PGSimpleDataSource) : KueueConnectionFactory {
    override suspend fun create(): KueueConnection {
        val connection = dataSource.connection
        return JdbcKueueConnection(connection = connection.unwrap(BaseConnection::class.java))
    }
}
