import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    pom {
        name = "pg-kueue"
        description = "Kotlin Coroutines PostgresSQL-based message queue using LISTEN/NOTIFYt"
        url = "https://github.com/vooft/pg-kueue"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        scm {
            connection = "https://github.com/vooft/pg-kueue"
            url = "https://github.com/vooft/pg-kueue"
        }
        developers {
            developer {
                name = "pg-kueue team"
            }
        }
    }
}
