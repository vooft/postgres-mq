plugins {
    `pg-kueue-base`
    `pg-kueue-publish`
}

dependencies {
    implementation(project(":pg-kueue-utils"))
    implementation(project(":pg-kueue-transport:pg-kueue-transport-core"))
}
