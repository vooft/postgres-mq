plugins {
    `java-test-fixtures`
    `pg-kueue-base`
    `pg-kueue-publish`
}

dependencies {
    api(libs.kotlin.logging)
    api(libs.bundles.coroutines)

    testFixturesApi(libs.testcontainers.postgres)
    testFixturesApi(libs.kotest.assertions)
    testFixturesImplementation(project(":pg-kueue-pubsub:pg-kueue-pubsub-core"))
    testFixturesImplementation(project(":pg-kueue-transport:pg-kueue-transport-core"))
}
