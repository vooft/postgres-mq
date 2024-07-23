package io.github.vooft.kueue.impl

import io.github.vooft.kueue.IntegrationTest
import io.github.vooft.kueue.KueueConnection
import io.github.vooft.kueue.KueueConnectionFactory
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.jdbc.JdbcKueueConnection
import io.github.vooft.kueue.jdbc.JdbcKueueConnectionFactory
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.jupiter.api.Test
import org.postgresql.core.BaseConnection
import org.postgresql.ds.PGSimpleDataSource
import java.sql.DriverManager
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class KueueManagerImplTest : IntegrationTest() {
    @Test
    fun `should produce to multiple topics and consume from multiple topics`(): Unit = runBlocking {
        val connectionFactory = JdbcKueueConnectionFactory(
            dataSource = PGSimpleDataSource().apply {
                setUrl(psql.jdbcUrl)
                user = psql.username
                password = psql.password
            }
        )

        val topics = List(10) { KueueTopic(UUID.randomUUID().toString()) }

        val kueueManager = KueueManagerImpl(connectionFactory)
        try {
            val producers = topics.map { kueueManager.createProducer(it) }

            val mutex = Mutex()
            val consumed = mutableMapOf<KueueTopic, MutableList<String>>()
            val subscriptions = topics.map { topic ->
                kueueManager.createSubscription(topic) {
                    mutex.withLock {
                        consumed.computeIfAbsent(topic) { mutableListOf() }.add(it)
                    }
                }
            }

            val messagesPerTopic = 100
            val produced = topics.associateWith { MutableList(messagesPerTopic) { UUID.randomUUID().toString() } }

            for (producer in producers) {
                repeat(messagesPerTopic) {
                    val message = produced.getValue(producer.topic)[it]
                    producer.send(message)
                }
            }

            eventually(5.seconds) {
                val currentConsumed = mutex.withLock { consumed.toMap() }
                for ((topic, messages) in currentConsumed) {
                    messages shouldContainExactly produced.getValue(topic)
                }

                delay(10)
            }

            subscriptions.forEach { it.close() }
        } finally {
            kueueManager.close()
        }
    }

    @Test
    fun `should resubscribe after connection closure`(): Unit = runBlocking {
        val connection1 = DriverManager.getConnection(psql.jdbcUrl, psql.username, psql.password)
        val connection2 = DriverManager.getConnection(psql.jdbcUrl, psql.username, psql.password)

        val remainingConnections = mutableListOf(connection1, connection2)
        val connectionFactory = object : KueueConnectionFactory {
            override suspend fun create(): KueueConnection {
                val connection = remainingConnections.removeFirst()
                return JdbcKueueConnection(connection.unwrap(BaseConnection::class.java))
            }
        }

        val topics = List(10) { KueueTopic(UUID.randomUUID().toString()) }

        val kueueManager = KueueManagerImpl(connectionFactory)
        try {
            val producers = topics.map { kueueManager.createProducer(it) }

            val mutex = Mutex()
            val consumed = mutableMapOf<KueueTopic, MutableList<String>>()
            val subscriptions = topics.map { topic ->
                kueueManager.createSubscription(topic) {
                    mutex.withLock {
                        consumed.computeIfAbsent(topic) { mutableListOf() }.add(it)
                    }
                }
            }

            val batch1 = topics.associateWith { List(10) { UUID.randomUUID().toString() } }
            val batch2 = topics.associateWith { List(10) { UUID.randomUUID().toString() } }

            producers.forEach { producer -> batch1.getValue(producer.topic).forEach { producer.send(it) } }

            eventually(5.seconds) {
                val currentConsumed = mutex.withLock { consumed.toMap() }
                for ((topic, messages) in currentConsumed) {
                    messages shouldContainExactly batch1.getValue(topic)
                }

                delay(10)
            }

            consumed.clear()
            connection1.close()

            producers.forEach { producer -> batch2.getValue(producer.topic).forEach { producer.send(it) } }
            eventually(5.seconds) {
                val currentConsumed = mutex.withLock { consumed.toMap() }
                for ((topic, messages) in currentConsumed) {
                    messages shouldContainExactly batch2.getValue(topic)
                }

                delay(10)
            }

            subscriptions.forEach { it.close() }
        } finally {
            kueueManager.close()
            connection2.close()
        }
    }
}
