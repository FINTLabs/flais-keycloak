import java.text.SimpleDateFormat
import java.util.Date

plugins {
    base
    kotlin("jvm") version "2.2.20"
    id("com.avast.gradle.docker-compose") version "0.17.12"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testcontainers:testcontainers:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("com.github.dasniko:testcontainers-keycloak:3.8.0")
}

val keycloakTestImageTag = "flais-keycloak:test"
tasks.register<Exec>("keycloakTestImage") {
    group = "docker"
    description = "Build the local Keycloak image used by tests"
    commandLine("docker", "build", "-t", keycloakTestImageTag, ".")
}
tasks.withType<Test>().configureEach {
    dependsOn("keycloakTestImage")
    useJUnitPlatform()
    systemProperty("keycloak.image", keycloakTestImageTag)
}

tasks {
    register("deployDev") {
        group = "docker"
        description = "Deploy local dev with compose"

        doLast {
            dockerCompose.dockerExecutor.execute("compose", "up", "-d", "--build", "keycloak")
            println("Rebuilt & restarted Keycloak")
        }
    }

    register("restart") {
        group = "docker"
        description = "Rebuild the keycloak image and recreate the container"

        doLast {
            dockerCompose.dockerExecutor.execute("compose", "build", "--pull", "keycloak")
            dockerCompose.dockerExecutor.execute("compose", "up", "-d", "--force-recreate", "keycloak")
            println("Rebuilt & restarted Keycloak")
        }
    }

    register<Exec>("buildImage") {
        group = "docker"
        description = "Build the flais Keycloak image locally with timestamp"

        doFirst {
            val timestamp = SimpleDateFormat("yyyy.MM.dd-HH.mm").format(Date())
            println("Building image with tag: $timestamp")
            commandLine("docker", "build", "-t", "flais-keycloak:$timestamp", ".")
        }
    }
}