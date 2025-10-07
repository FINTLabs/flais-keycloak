import java.text.SimpleDateFormat
import java.util.Date

plugins {
    base
    kotlin("jvm") version "2.2.20"
    id("com.avast.gradle.docker-compose") version "0.17.13"
    id("com.github.ben-manes.versions") version "0.53.0"
}

group = "no.fintlabs"
version = "1.0.0"

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
