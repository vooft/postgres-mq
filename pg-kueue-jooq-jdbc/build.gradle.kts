dependencies {
    api(project(":pg-kueue-core"))
    api(project(":pg-kueue-jdbc"))
    implementation(project(":pg-kueue-utils"))

    compileOnly(libs.pg.jdbc)
    compileOnly(libs.jooq)

    testImplementation(testFixtures(project(":pg-kueue-utils")))
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.pg.jdbc)
    testImplementation(libs.jooq)
    testImplementation(libs.slf4j.simple)
    testImplementation(libs.hikaricp)
}
