package io.github.vooft.kueue.impl

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.vooft.kueue.HappyPathTest
import io.github.vooft.kueue.IntegrationTest
import io.github.vooft.kueue.Kueue
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.jooq.jdbc.jooq
import io.github.vooft.kueue.jooq.jdbc.send
import io.kotest.assertions.nondeterministic.continually
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test
import org.postgresql.core.BaseConnection
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class JooqTest : IntegrationTest() {
    @Test
    fun `should produce to multiple topics and consume from multiple topics`(): Unit = runBlocking {
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = psql.jdbcUrl
                username = psql.username
                password = psql.password
            }
        ).use {
            with(HappyPathTest) {
                Kueue.jooq(it).happyPathTest()
            }
        }
    }

    @Test
    fun `should send after transaction commit`(): Unit = runBlocking {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = psql.jdbcUrl
                username = psql.username
                password = psql.password
            }
        )

        val topic = KueueTopic(UUID.randomUUID().toString())

        val kueue = Kueue.jooq(dataSource)

        try {
            val mutex = Mutex()
            val consumed = mutableListOf<String>()

            val subscription = kueue.subscribe(topic) {
                mutex.withLock { consumed.add(it) }
            }

            val before = UUID.randomUUID().toString()
            val during = UUID.randomUUID().toString()
            val after = UUID.randomUUID().toString()

            val txn1 = UUID.randomUUID().toString()
            val txn2 = UUID.randomUUID().toString()

            // send normal message
            kueue.send(topic, before)

            eventually(1.seconds) {
                mutex.withLock { consumed.toList() } shouldContainExactly listOf(before)
            }

            // start transaction
            val transactionConnection = dataSource.connection
            transactionConnection.autoCommit = false

            val txnDsl = DSL.using(transactionConnection, SQLDialect.POSTGRES)

            // send transaction message 1
            kueue.send(topic, txn1, txnDsl)

            // send normal message in-between transaction ones
            kueue.send(topic, during)

            // no txn message added
            eventually(1.seconds) {
                mutex.withLock { consumed.toList() } shouldContainExactly listOf(before, during)
            }

            // send second transaction message
            kueue.send(topic, txn2, txnDsl)

            // still no txn messages
            mutex.withLock { consumed.toList() } shouldContainExactly listOf(before, during)

            // commit transaction
            transactionConnection.commit()

            // both txn messages added
            eventually(1.seconds) {
                mutex.withLock { consumed.toList() } shouldContainExactly listOf(before, during, txn1, txn2)
            }

            // send normal message after transaction over the transaction connection
            transactionConnection.autoCommit = true
            kueue.send(topic, after, txnDsl)

            // all messages added
            eventually(1.seconds) {
                mutex.withLock { consumed.toList() } shouldContainExactly listOf(before, during, txn1, txn2, after)
            }

            subscription.close()
        } finally {
            kueue.close()
            dataSource.close()
        }
    }

    @Test
    fun `should not send after transaction rollback`(): Unit = runBlocking {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = psql.jdbcUrl
                username = psql.username
                password = psql.password
            }
        )

        val topic = KueueTopic(UUID.randomUUID().toString())

        val kueue = Kueue.jooq(dataSource)

        try {
            val mutex = Mutex()
            val consumed = mutableListOf<String>()

            val subscription = kueue.subscribe(topic) {
                mutex.withLock { consumed.add(it) }
            }

            val before = UUID.randomUUID().toString()
            val during = UUID.randomUUID().toString()
            val after = UUID.randomUUID().toString()

            val txn = UUID.randomUUID().toString()

            // send normal message
            kueue.send(topic, before)

            eventually(1.seconds) {
                mutex.withLock { consumed.toList() } shouldContainExactly listOf(before)
            }

            // start transaction
            val transactionConnection = dataSource.connection
            transactionConnection.autoCommit = false

            val kueueTransactionConnection = kueue.wrap(transactionConnection.unwrap(BaseConnection::class.java))

            // send transaction message
            kueue.send(topic, txn, kueueTransactionConnection)

            // send normal message in-between transaction ones
            kueue.send(topic, during)

            // no txn message added
            eventually(1.seconds) {
                mutex.withLock { consumed.toList() } shouldContainExactly listOf(before, during)
            }

            // rollback transaction
            transactionConnection.rollback()

            // still no txn messages
            continually(1.seconds) {
                mutex.withLock { consumed.toList() } shouldContainExactly listOf(before, during)
            }

            // send normal message after transaction over the transaction connection
            transactionConnection.autoCommit = true
            kueue.send(topic, after, kueueTransactionConnection)

            // all messages added
            eventually(1.seconds) {
                mutex.withLock { consumed.toList() } shouldContainExactly listOf(before, during, after)
            }

            subscription.close()
        } finally {
            kueue.close()
            dataSource.close()
        }
    }
}
