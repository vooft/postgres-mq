package io.github.vooft.kueue.jdbc

import io.github.vooft.kueue.IntegrationTest
import io.github.vooft.kueue.KueueTopic
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.postgresql.core.BaseConnection
import java.sql.DriverManager
import java.util.UUID

class JdbcKueueConnectionTest : IntegrationTest() {
    @Test
    fun `should send and receive`(): Unit = runBlocking {
        val channel = KueueTopic(UUID.randomUUID().toString())

        val jdbc = DriverManager.getConnection(psql.jdbcUrl, psql.username, psql.password)
        val connection = JdbcKueueConnection(jdbc.unwrap(BaseConnection::class.java))
        try {
            connection.listen(channel)

            connection.listen(channel)

            val message = UUID.randomUUID().toString()
            connection.notify(channel, message)

            val received = connection.messages.receive()
            received.topic shouldBe channel
            received.message shouldBe message
        } finally {
            connection.close()
        }
    }
}
