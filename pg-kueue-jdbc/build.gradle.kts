dependencies {
    api(project(":pg-kueue-core"))
    implementation(project(":pg-kueue-utils"))

    compileOnly(libs.pg.jdbc)

    testImplementation(testFixtures(project(":pg-kueue-utils")))
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.pg.jdbc)
    testImplementation(libs.slf4j.simple)
}
