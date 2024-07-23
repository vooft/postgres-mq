dependencies {
    implementation(libs.bundles.coroutines)
    implementation(libs.kotlin.logging)

    implementation(libs.pg.jdbc)

    testImplementation(libs.slf4j.simple)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.testcontainers.postgres)
}
