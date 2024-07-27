dependencies {
    testImplementation(project(":pg-kueue-jdbc"))
    testImplementation(testFixtures(project(":pg-kueue-utils")))

    testImplementation(libs.pg.jdbc)

    testImplementation(libs.slf4j.simple)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.hikaricp)
}
