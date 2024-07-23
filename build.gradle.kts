import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.maven.central.publish)
    alias(libs.plugins.dokka)
}

allprojects {
    group = "io.github.vooft"
    version = System.getenv("TAG") ?: "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

val publishAllTaskName = "publishAndReleaseToMavenCentralAll"
tasks.create(publishAllTaskName)

val publishAllToMavenLocalTaskName = "publishAllToMavenLocal"
tasks.create(publishAllToMavenLocalTaskName)

subprojects {

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jmailen.kotlinter")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "com.vanniktech.maven.publish")
    apply(plugin = "org.jetbrains.dokka")

    repositories {
        mavenCentral()
    }

    dependencies {
        addPlatform(this@subprojects, platform("org.jetbrains.kotlin:kotlin-bom:${rootProject.project.libs.versions.kotlin.get()}"))
    }

    detekt {
        buildUponDefaultConfig = true
        config.from(files("$rootDir/detekt.yaml"))
        basePath = rootDir.absolutePath
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    configurations {
        compileOnly {
            extendsFrom(configurations.annotationProcessor.get())
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            events = mutableSetOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
            showStandardStreams = true
        }
    }

    tasks.withType<LintTask> {
        source("build.gradle.kts", "settings.gradle.kts")
        exclude {
            it.file.path.startsWith("$buildDir") && !it.file.path.endsWith("gradle.kts")
        }
        dependsOn("formatKotlin")
    }

    tasks.withType<FormatTask> {
        source("build.gradle.kts", "settings.gradle.kts")
        exclude {
            it.file.path.startsWith("$buildDir") && !it.file.path.endsWith("gradle.kts")
        }
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xcontext-receivers")
            allWarningsAsErrors = true
            jvmTarget.set(JvmTarget.JVM_21)
        }
// 		doLast {
// 			configurations.compileClasspath.get().forEach {
// 				println("TRACER checking: " + it.name)
// 				assert(it.exists())
// 			}
// 		}
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

    tasks.findByName("publishAndReleaseToMavenCentralAll")?.dependsOn(publishAllTaskName)
    tasks.findByName("publishToMavenLocal")?.dependsOn(publishAllToMavenLocalTaskName)
}

//tasks.create("publish") {
//    subprojects.forEach { project -> project.tasks.findByName("publish")?.let { dependsOn(it) } }
//}

repositories {
    mavenCentral()
}

fun DependencyHandler.addPlatform(project: Project, platform: Dependency) {
    val availableConfigurations = project.configurations.map { it.name }.toSet()
    availableConfigurations.intersect(
        setOf("api", "implementation", "testImplementation")
    ).forEach { configuration ->
        add(configuration, platform)
    }
}

fun <D : Dependency> DependencyHandler.addPlatform(project: Project, platform: Provider<D>) = addPlatform(project, platform.get())
