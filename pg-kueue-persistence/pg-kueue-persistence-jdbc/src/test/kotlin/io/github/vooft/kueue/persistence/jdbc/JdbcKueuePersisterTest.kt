package io.github.vooft.kueue.persistence.jdbc

import io.github.vooft.kueue.IntegrationTest
import io.github.vooft.kueue.jdbc.JdbcKueueConnection
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class JdbcKueuePersisterTest : IntegrationTest() {

    private val persister = JdbcKueuePersister()

    @BeforeEach
    fun setUp() {
        Flyway.configure()
            .dataSource(psql.jdbcUrl, psql.username, psql.password)
            .locations("classpath:kueue-database")
            .load()
            .migrate()

        psql.createConnection("").use {
            it.createStatement().execute("TRUNCATE kueue_events CASCADE")
        }
    }

    @Test
    fun `should save message`() {
        val topic = UUID.randomUUID().toString()
        val message = UUID.randomUUID().toString()
        psql.createConnection("").use { connection ->
            runBlocking {
                persister.saveMessage(topic, message, JdbcKueueConnection(connection))
            }
        }

        psql.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM kueue_events").use { resultSet ->
                    resultSet.next()

                    resultSet.getInt("id") shouldBe 1
                    resultSet.getString("topic") shouldBe topic
                    resultSet.getString("message") shouldBe message
                }
            }
        }
    }
}
