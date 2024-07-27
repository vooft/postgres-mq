package io.github.vooft.kueue.jooq.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.vooft.kueue.IntegrationTest.Companion.psql
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test

class JooqJdbcTest {
    @Test
    fun test() {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = psql.jdbcUrl
                username = psql.username
                password = psql.password
            }
        )

        val rootDsl = DSL.using(dataSource, SQLDialect.POSTGRES)
        rootDsl.transactionResult { config ->
            val txnDsl = DSL.using(config)
            val provider = txnDsl.configuration().connectionProvider()
            println(txnDsl.configuration())
            val conn1 = provider.acquire()
            println(conn1)
            val conn2 = provider.acquire()
            println(conn2)
        }
    }
}
