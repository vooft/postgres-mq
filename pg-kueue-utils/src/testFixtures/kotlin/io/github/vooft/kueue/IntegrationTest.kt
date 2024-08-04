package io.github.vooft.kueue

import org.testcontainers.containers.PostgreSQLContainer

@Suppress("detekt:UtilityClassWithPublicConstructor")
open class IntegrationTest {
    companion object {
        val psql = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("folter")
            withUsername("test")
            withPassword("test")
            start()

            createConnection("").use { connection ->
                for (migration in MIGRATIONS) {
                    connection.createStatement().use { statement ->
                        statement.execute(migration)
                    }
                }
            }
        }
    }
}

private const val KUEUE_EVENTS_MIGRATION = "/database/1_kueue_events.sql"
private val MIGRATIONS = listOf(KUEUE_EVENTS_MIGRATION).map { IntegrationTest::class.java.getResource(it).readText() }
