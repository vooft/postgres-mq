dependencies {
    api(project(":pg-kueue-jooq-jdbc"))
    testImplementation(testFixtures(project(":pg-kueue-utils")))

    testImplementation(libs.pg.jdbc)
    testImplementation(libs.jooq)

    testImplementation(libs.slf4j.simple)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.hikaricp)
}
