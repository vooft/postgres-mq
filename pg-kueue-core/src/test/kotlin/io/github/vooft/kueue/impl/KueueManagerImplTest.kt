package io.github.vooft.kueue.impl

import io.github.vooft.kueue.IntegrationTest
import io.github.vooft.kueue.KueueTopic
import io.github.vooft.kueue.jdbc.JdbcKueueConnectionFactory
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import java.util.StringJoiner
import java.util.UUID

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
            val consumers = topics.map { kueueManager.createConsumer(it) }

            val messages = List(1000) { UUID.randomUUID().toString() }

            val mutex = Mutex()
            val consumed = mutableMapOf<KueueTopic, StringJoiner>()



            producers.forEachIndexed { index, producer ->
                producer.send(messages[index])
            }

            consumers.forEachIndexed { index, consumer ->
                val message = consumer.receive()
                messages[index] shouldBe message
            }
        } finally {
            kueueManager.close()
        }
    }
}
