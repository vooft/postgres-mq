plugins {
    `pg-kueue-base`
}

dependencies {
    testImplementation(project(":pg-kueue-pubsub:pg-kueue-pubsub-jooq"))
    testImplementation(project(":pg-kueue-pubsub:pg-kueue-pubsub-jdbc"))
    testImplementation(project(":pg-kueue-transport:pg-kueue-transport-jdbc"))
    testImplementation(testFixtures(project(":pg-kueue-utils")))

    testImplementation(libs.pg.jdbc)
    testImplementation(libs.jooq)

    testImplementation(libs.slf4j.simple)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.hikaricp)
}
