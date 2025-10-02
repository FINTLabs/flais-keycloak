import java.text.SimpleDateFormat
import java.util.Date

plugins {
    base
    kotlin("jvm") version "2.2.20"
    id("com.avast.gradle.docker-compose") version "0.17.12"
}

group = "no.fintlabs"
version = "1.0.0"

allprojects {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

val keycloakTestImageTag = "flais-keycloak:test"
val keycloakTestImage = tasks.register<Exec>("keycloakTestImage") {
    group = "docker"
    description = "Build the local Keycloak image used by tests"
    commandLine("docker", "build", "-t", keycloakTestImageTag, ".")
}

// subprojects {
//     plugins.withId("java") {
//         the<JavaPluginExtension>().toolchain {
//             languageVersion.set(JavaLanguageVersion.of(21))
//         }
//     }

//     tasks.withType<Test>().configureEach {
//         useJUnitPlatform()
//         dependsOn(keycloakTestImage)
//         systemProperty("keycloak.image", keycloakTestImageTag)
//     }
// }

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
