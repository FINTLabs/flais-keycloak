plugins {
    id("com.avast.gradle.docker-compose") version "0.17.12"
}

tasks {
    register("restartKeycloak") {
        group = "docker"
        description = "Restart only the keycloak container"

        doLast {
            dockerCompose.dockerExecutor.execute("compose", "restart", "keycloak")
            println("Restarted Keycloak")
        }
    }

    register("deployDev") {
        group = "dev"
        description = "Builds provider and theme and copies the jar output to providers folder in local-dev"

        dependsOn("buildProviderDev", "buildThemeDev")
        finalizedBy("restartKeycloak")
    }

    register<Copy>("buildThemeDev") {
        group = "build"
        description = "Builds provider and copies the jar output to providers folder in local-dev"

        dependsOn(":libs:flais-theme:build")

        into(layout.projectDirectory.dir("providers"))
        from(project(":libs:flais-theme").layout.projectDirectory.dir("dist_keycloak"))
    }

    register<Copy>("buildProviderDev") {
        group = "build"
        description = "Builds provider and copies the jar output to providers folder in local-dev"

        dependsOn(":libs:flais-provider:build")

        into(layout.projectDirectory.dir("providers"))
        from(project(":libs:flais-provider").layout.buildDirectory.dir("libs"))
    }
}