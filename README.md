![Build and test](https://github.com/vooft/pg-kueue/actions/workflows/build.yml/badge.svg?branch=main)
![Releases](https://img.shields.io/github/v/release/vooft/pg-kueue)
![Maven Central](https://img.shields.io/maven-central/v/io.github.vooft/pg-kueue-core)
![License](https://img.shields.io/github/license/vooft/pg-kueue)

# pg-kueue
Kotlin Coroutines PostgresSQL-based message queue using LISTEN/NOTIFY

Everything is String-based and for now just follows normal LISTEN/NOTIFY rules.

## Supported database SPIs
This library was designed to be used with different SPIs, providing similar coroutines-based interface.

Currently, only JDBC-based SPI is implemented + helper functions for jOOQ.

### JDBC
pg-kueue uses virtual threads to make any calls to the database in a non-blocking fashion.

It works with any `java.sql.DataSource` implementation, reserving one connection for listening to notifications.

### jOOQ
There is a module that provides a number of helper methods that can work with a jOOQ `DSLContext` and mostly delegate to the JDBC module.

# JDBC usage

## Gradle
```kotlin
    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("io.github.vooft:pg-kueue-jdbc:<version>")
    }
```

## Simple usage
```kotlin
    val dataSource = createMyDataSource()

    val kueue = Kueue.jdbc(dataSource)
    val subscription = kueue.subscribe(KueueTopic("my_topic")) { message: String ->
        println("Received message: $message")
    }

    kueue.send(KueueTopic("my_topic"), "Hello, world!")
    // will print after a tiny delay: "Received message: Hello, world!"
```

You can close subscription, if you would like to stop a particular listener:
```kotlin
    subscription.close()
```

But it is not necessary if the the subscription should exist for the whole Kueue lifecycle.

All subscriptions will be closed automatically when Kueue is closed:
```kotlin
    kueue.close()
```

## Transactional usage
To send a message using existing transaction, you should provide the transactional connection.

Normally, API accepts a instance of a wrapped connection `KueueConnection`, there is a helper method to create it:
```kotlin
    val transactionalConnection = myBeginTransaction()
    kueue.send(KueueTopic("my_topic"), "Hello, world!", kueue.wrap(transactionalConnection))
``` 

There is also an extension function for a specific library to simplify transactional sending:
```kotlin
    val transactionalConnection = myBeginTransaction()
    kueue.send(KueueTopic("my_topic"), "Hello, world!", transactionalConnection) // an extension function must be imported explicitly
```

# Additional modules
## jOOQ JDBC
There is a module that accepts a jOOQ `DSLContext` and provides a similar interface to the JDBC module.

### Gradle
```kotlin
    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("io.github.vooft:pg-kueue-jooq-jdbc:<version>")
    }
```

### Usage
```kotlin
    val dslContext = createMyDslContext()

    // there is a helper method to create a Kueue instance using, for example, a non-transactional DSLContext
    val kueue = Kueue.jooq(dslContext)

    // also there is an extension method that accepts a transactional DSLContext to send notification within a transaction
    kueue.send(KueueTopic("my_topic"), "Hello, world!", myTransactionalDslContext)
```
