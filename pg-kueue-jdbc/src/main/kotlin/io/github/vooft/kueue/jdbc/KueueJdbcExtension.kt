package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.Kueue
import io.github.vooft.kueue.impl.KueueImpl
import org.postgresql.ds.PGSimpleDataSource

fun Kueue.Companion.jdbc(
    jdbcUrl: String,
    username: String,
    password: String,
    customizer: (PGSimpleDataSource) -> Unit = { }
): Kueue = KueueImpl(
    JdbcKueueConnectionFactory(
        dataSource = PGSimpleDataSource().also {
            it.setUrl(jdbcUrl)
            it.user = username
            it.password = password
            customizer(it)
        }
    )
)
