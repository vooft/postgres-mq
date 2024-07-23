package io.github.vooft.kueue.impl

import io.github.vooft.kueue.IntegrationTest
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.jdbc.JdbcKueueConnectionFactory
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
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
                currentConsumed.all { it.value.size == messagesPerTopic } shouldBe true

                delay(10)
            }

            subscriptions.forEach { it.close() }
        } finally {
            kueueManager.close()
        }
    }
}
