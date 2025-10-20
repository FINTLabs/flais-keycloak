import java.text.SimpleDateFormat
import java.util.Date

plugins {
    base
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.docker.compose)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization)
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
}

sourceSets {
    @Suppress("UNUSED_VARIABLE")
    val test by getting {
        kotlin { srcDirs(layout.projectDirectory.dir("src/test/integration/kotlin")) }
        resources { setSrcDirs(listOf("src/test/integration/resources")) }
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
    systemProperty("project.rootDir", rootProject.projectDir.absolutePath)
    environment("KEYCLOAK_VERSION", libs.versions.keycloak.get())

    dependsOn("ktlintCheck")
}

allprojects {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dockerCompose {
    environment.put("KEYCLOAK_VERSION", libs.versions.keycloak.get())
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
