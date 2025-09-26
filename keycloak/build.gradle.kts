import java.text.SimpleDateFormat
import java.util.Date

plugins {
    base
    id("com.avast.gradle.docker-compose") version "0.17.12"
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