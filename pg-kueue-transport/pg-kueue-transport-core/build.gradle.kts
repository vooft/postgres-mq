plugins {
    `pg-kueue-base`
    `pg-kueue-publish`
}

dependencies {
    api(project(":pg-kueue-types"))
    implementation(project(":pg-kueue-utils"))
}
