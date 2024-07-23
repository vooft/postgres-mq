plugins {
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlin.logging)
    api(libs.bundles.coroutines)
    testFixturesApi(libs.testcontainers.postgres)
}
