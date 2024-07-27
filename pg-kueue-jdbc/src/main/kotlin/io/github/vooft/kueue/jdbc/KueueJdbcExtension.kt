package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.Kueue
import io.github.vooft.kueue.impl.KueueImpl
import org.postgresql.core.BaseConnection
import javax.sql.DataSource

fun Kueue.Companion.jdbc(dataSource: DataSource): Kueue<BaseConnection, JdbcKueueConnection> = KueueImpl(
    connectionProvider = DataSourceKueueConnectionProvider(dataSource),
    pubSub = JdbcKueueConnectionPubSub()
)
