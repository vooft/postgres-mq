plugins {
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlin.logging)
    api(libs.bundles.coroutines)

    testFixturesApi(libs.testcontainers.postgres)
    testFixturesApi(libs.kotest.assertions)
    testFixturesImplementation(project(":pg-kueue-core"))
}
