import java.text.SimpleDateFormat
import java.util.Date

plugins {
    base
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.docker.compose)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

group = "no.fintlabs"
version = "1.0.0"

dependencies {
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.slf4j.simple)

    testImplementation(libs.keycloak.admin.client)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp)
    testImplementation(libs.awaitility.kotlin)
}

allprojects {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

val coverageMode = providers.gradleProperty("coverageMode").orNull
kover {
    currentProject {
        instrumentation {
            if (coverageMode == "unit") {
                disabledForTestTasks.add("integrationTest")
            }
            if (coverageMode == "integration") {
                disabledForTestTasks.add("test")
            }
        }
    }
    reports {
        filters {
            includes {
                classes("no.fintlabs.*")
            }
        }
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlinx.kover") {
        val coverageMode = providers.gradleProperty("coverageMode").orNull
        kover {
            currentProject {
                instrumentation {
                    if (coverageMode == "integration") {
                        disabledForTestTasks.add("test")
                    }
                }
            }
        }
    }
}

dockerCompose {
    environment.put("KEYCLOAK_VERSION", libs.versions.keycloak.get())
}

val integrationTestSourceSet by sourceSets.creating {
    kotlin.srcDir("src/test/integration/kotlin")
    resources.srcDir("src/test/integration/resources")
    compileClasspath += sourceSets["main"].output + configurations["testCompileClasspath"]
    runtimeClasspath += output + compileClasspath + configurations["testRuntimeClasspath"]
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath

    useJUnitPlatform()

    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
    systemProperty("project.rootDir", rootProject.projectDir.absolutePath)

    environment("KEYCLOAK_VERSION", libs.versions.keycloak.get())

    shouldRunAfter(tasks.named("test"))
}

tasks.register("deployDev") {
    group = "docker"
    description = "Deploy local dev with compose"

    doLast {
        dockerCompose.dockerExecutor.execute("compose", "up", "-d", "--build", "keycloak")
        println("Rebuilt & restarted Keycloak")
    }
}

tasks.register("restart") {
    group = "docker"
    description = "Rebuild the keycloak image and recreate the container"

    doLast {
        dockerCompose.dockerExecutor.execute("compose", "build", "--pull", "keycloak")
        dockerCompose.dockerExecutor.execute("compose", "up", "-d", "--force-recreate", "keycloak")
        println("Rebuilt & restarted Keycloak")
    }
}

tasks.register<Exec>("buildImage") {
    group = "docker"
    description = "Build the flais Keycloak image locally with timestamp"

    doFirst {
        val timestamp = SimpleDateFormat("yyyy.MM.dd-HH.mm").format(Date())
        println("Building image with tag: $timestamp")
        commandLine("docker", "build", "-t", "flais-keycloak:$timestamp", ".")
    }
}

tasks.register<Exec>("checkDeps") {
    group = "tools"
    description = "Check dependencies for new versions"
    doFirst {
        commandLine("./gradlew", "dependencyUpdates")
    }
}
