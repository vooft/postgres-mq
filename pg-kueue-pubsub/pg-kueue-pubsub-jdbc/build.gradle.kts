plugins {
    `pg-kueue-base`
    `pg-kueue-publish`
}

dependencies {
    api(project(":pg-kueue-pubsub:pg-kueue-pubsub-core"))
    implementation(project(":pg-kueue-transport:pg-kueue-transport-jdbc"))
}
