package io.github.vooft.kueue

import org.testcontainers.containers.PostgreSQLContainer

@Suppress("detekt:UtilityClassWithPublicConstructor")
open class IntegrationTest {
    companion object {
        internal val psql = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("folter")
            withUsername("test")
            withPassword("test")
            start()
        }
    }
}
