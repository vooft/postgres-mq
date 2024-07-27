package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.Kueue
import io.github.vooft.kueue.impl.KueueImpl
import java.sql.Connection
import javax.sql.DataSource

fun Kueue.Companion.jdbc(dataSource: DataSource): Kueue<Connection, JdbcKueueConnection> = KueueImpl(
    connectionProvider = DataSourceKueueConnectionProvider(dataSource),
    pubSub = JdbcKueueConnectionPubSub()
)
