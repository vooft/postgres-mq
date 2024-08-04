plugins {
    `pg-kueue-base`
    `pg-kueue-publish`
}

dependencies {
    api(project(":pg-kueue-persistence:pg-kueue-persistence-jdbc"))

    implementation(project(":pg-kueue-utils"))
    implementation(project(":pg-kueue-utils-jooq"))

    compileOnly(libs.jooq)

    testImplementation(testFixtures(project(":pg-kueue-utils")))
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.pg.jdbc)
    testImplementation(libs.jooq)
    testImplementation(libs.slf4j.simple)
    testImplementation(libs.hikaricp)
}
