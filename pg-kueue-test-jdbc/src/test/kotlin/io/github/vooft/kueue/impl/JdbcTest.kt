package io.github.vooft.kueue.impl

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.vooft.kueue.HappyPathTest
import io.github.vooft.kueue.IntegrationTest
import io.github.vooft.kueue.Kueue
import io.github.vooft.kueue.KueueConnectionProvider
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.jdbc.JdbcKueueConnection
import io.github.vooft.kueue.jdbc.JdbcKueueConnectionPubSub
import io.github.vooft.kueue.jdbc.jdbc
import io.kotest.assertions.nondeterministic.continually
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.jupiter.api.Test
import org.postgresql.core.BaseConnection
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class JdbcTest : IntegrationTest() {
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
                Kueue.jdbc(it).test()
            }
        }
    }

    @Test
    fun `should resubscribe after connection closure`(): Unit = runBlocking {
        val connection1 = DriverManager.getConnection(psql.jdbcUrl, psql.username, psql.password)
        val connection2 = DriverManager.getConnection(psql.jdbcUrl, psql.username, psql.password)

        val remainingConnections = mutableListOf(connection1, connection2)
        val connectionFactory = object : KueueConnectionProvider<Connection, JdbcKueueConnection> {
            override suspend fun wrap(connection: Connection) = JdbcKueueConnection(connection)

            override suspend fun create(): JdbcKueueConnection {
                val connection = remainingConnections.removeFirst()
                return JdbcKueueConnection(connection.unwrap(BaseConnection::class.java))
            }

            override suspend fun close(connection: JdbcKueueConnection) = Unit
        }

        val topics = List(10) { KueueTopic(UUID.randomUUID().toString()) }

        val kueue = KueueImpl(connectionFactory, JdbcKueueConnectionPubSub())
        try {
            val mutex = Mutex()
            val consumed = mutableMapOf<KueueTopic, MutableList<String>>()
            val subscriptions = topics.map { topic ->
                kueue.subscribe(topic) {
                    mutex.withLock {
                        consumed.computeIfAbsent(topic) { mutableListOf() }.add(it)
                    }
                }
            }

            val batch1 = topics.associateWith { List(10) { UUID.randomUUID().toString() } }
            val batch2 = topics.associateWith { List(10) { UUID.randomUUID().toString() } }

            for ((topic, messages) in batch1) {
                for (message in messages) {
                    kueue.send(topic, message)
                }
            }

            eventually(1.seconds) {
                val currentConsumed = mutex.withLock { consumed.toMap() }
                for ((topic, messages) in currentConsumed) {
                    messages shouldContainExactly batch1.getValue(topic)
                }

                delay(10)
            }

            consumed.clear()
            connection1.close()

            for ((topic, messages) in batch2) {
                for (message in messages) {
                    kueue.send(topic, message)
                }
            }

            eventually(1.seconds) {
                val currentConsumed = mutex.withLock { consumed.toMap() }
                for ((topic, messages) in currentConsumed) {
                    messages shouldContainExactly batch2.getValue(topic)
                }

                delay(10)
            }

            subscriptions.forEach { it.close() }
        } finally {
            kueue.close()
            connection2.close()
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

        val kueue = Kueue.jdbc(dataSource)

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

            val kueueTransactionConnection = kueue.wrap(transactionConnection.unwrap(BaseConnection::class.java))

            // send transaction message 1
            kueue.send(topic, txn1, kueueTransactionConnection)

            // send normal message in-between transaction ones
            kueue.send(topic, during)

            // no txn message added
            eventually(1.seconds) {
                mutex.withLock { consumed.toList() } shouldContainExactly listOf(before, during)
            }

            // send second transaction message
            kueue.send(topic, txn2, kueueTransactionConnection)

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
            kueue.send(topic, after, kueueTransactionConnection)

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

        val kueue = Kueue.jdbc(dataSource)

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
