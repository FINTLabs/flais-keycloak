plugins {
    base
}

tasks {
    register("deployLocalDev") {
        group = "deploy"
        description = "Builds dev and relaunches keycloak to use changes"
        dependsOn(":apps:local-dev:deployDev")
        doLast {
            println("Done")
        }
    }

    register("buildLibs") {
        group = "build"
        description = "Builds all libs"
        dependsOn(":libs:flais-provider:build", ":libs:flais-theme:build")
    }
}
