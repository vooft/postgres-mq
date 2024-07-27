package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.KueueConnectionProvider
import io.github.vooft.kueue.common.withVirtualThreadDispatcher
import org.postgresql.core.BaseConnection
import javax.sql.DataSource

class DataSourceKueueConnectionProvider(private val dataSource: DataSource) : KueueConnectionProvider<BaseConnection, JdbcKueueConnection> {
    override suspend fun create(): JdbcKueueConnection = withVirtualThreadDispatcher {
        val connection = dataSource.connection
        JdbcKueueConnection(jdbcConnection = connection.unwrap(BaseConnection::class.java))
    }

    override suspend fun close(connection: JdbcKueueConnection) = withVirtualThreadDispatcher {
        connection.jdbcConnection.close()
    }
}
