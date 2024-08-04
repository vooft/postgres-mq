package io.github.vooft.kueue

import org.testcontainers.containers.PostgreSQLContainer

@Suppress("detekt:UtilityClassWithPublicConstructor")
open class IntegrationTest {
    companion object {
        val psql = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("pg-kueue")
            withUsername("test")
            withPassword("test")
            start()
        }
    }
}
