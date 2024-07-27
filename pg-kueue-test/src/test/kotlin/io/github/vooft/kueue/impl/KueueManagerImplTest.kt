package io.github.vooft.kueue.impl

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.vooft.kueue.IntegrationTest
import io.github.vooft.kueue.Kueue
import io.github.vooft.kueue.KueueConnectionProvider
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.jdbc.JdbcKueueConnection
import io.github.vooft.kueue.jdbc.JdbcKueueConnectionPubSub
import io.github.vooft.kueue.jdbc.jdbc
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.jupiter.api.Test
import org.postgresql.core.BaseConnection
import java.sql.DriverManager
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class KueueManagerImplTest : IntegrationTest() {
    @Test
    fun `should produce to multiple topics and consume from multiple topics`(): Unit = runBlocking {
        val topics = List(10) { KueueTopic(UUID.randomUUID().toString()) }

        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = psql.jdbcUrl
                username = psql.username
                password = psql.password
            }
        )

        val kueue = Kueue.jdbc(dataSource)
        try {
            val mutex = Mutex()
            val consumed = mutableMapOf<KueueTopic, MutableList<String>>()
            val subscriptions = topics.map { topic ->
                kueue.subscribe(topic) {
                    mutex.withLock { consumed.computeIfAbsent(topic) { mutableListOf() }.add(it) }
                }
            }

            val messagesPerTopic = 100
            val produced = topics.associateWith { MutableList(messagesPerTopic) { UUID.randomUUID().toString() } }

            for (topic in topics) {
                repeat(messagesPerTopic) {
                    val message = produced.getValue(topic)[it]
                    kueue.send(topic, message)
                }
            }

            eventually(5.seconds) {
                for ((topic, messages) in produced) {
                    consumed[topic] shouldContainExactly messages
                }

                delay(10)
            }

            subscriptions.forEach { it.close() }
        } finally {
            kueue.close()
        }
    }

    @Test
    fun `should resubscribe after connection closure`(): Unit = runBlocking {
        val connection1 = DriverManager.getConnection(psql.jdbcUrl, psql.username, psql.password)
        val connection2 = DriverManager.getConnection(psql.jdbcUrl, psql.username, psql.password)

        val remainingConnections = mutableListOf(connection1, connection2)
        val connectionFactory = object : KueueConnectionProvider<BaseConnection, JdbcKueueConnection> {
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

            eventually(5.seconds) {
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

            eventually(5.seconds) {
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
}
