package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.IntegrationTest
import io.github.vooft.kueue.KueueChannel
import io.github.vooft.kueue.KueueMessage
import io.github.vooft.kueue.collectUntilClosed
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.postgresql.core.BaseConnection
import java.sql.DriverManager
import java.util.UUID

class JdbcKueueConnectionTest : IntegrationTest() {
    @Test
    fun `should send and receive`(): Unit = runBlocking {
        val channel = KueueChannel(UUID.randomUUID().toString())

        val jdbc = DriverManager.getConnection(psql.jdbcUrl, psql.username, psql.password)
        val connection = JdbcKueueConnection(jdbc.unwrap(BaseConnection::class.java))
        try {
            connection.subscribe(channel)

            val messages = Channel<KueueMessage>()
            launch {
                connection.messages.collectUntilClosed { messages.send(it) }
                messages.close()
            }

            connection.subscribe(channel)

            val message = UUID.randomUUID().toString()
            connection.send(channel, message)

            val received = messages.receive()
            received.channel shouldBe channel
            received.message shouldBe message
        } finally {
            connection.close()
        }
    }
}
