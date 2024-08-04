rootProject.name = "pg-kueue"

include(
    ":pg-kueue-types",
    ":pg-kueue-utils",

    ":pg-kueue-pubsub:pg-kueue-pubsub-core",
    ":pg-kueue-pubsub:pg-kueue-pubsub-jdbc",
    ":pg-kueue-pubsub:pg-kueue-pubsub-jdbc-test",
    ":pg-kueue-pubsub:pg-kueue-pubsub-jooq",
    ":pg-kueue-pubsub:pg-kueue-pubsub-jooq-test",

    ":pg-kueue-transport:pg-kueue-transport-core",
    ":pg-kueue-transport:pg-kueue-transport-jdbc",
)

