import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    id("com.github.node-gradle.node") version "7.1.0"
}

tasks {
    clean {
        delete("dist", "dist_keycloak")
    }

    register<NpmTask>("buildTheme") {
        args = listOf("run", "build-keycloak-theme")
        dependsOn(named("npmInstall"))
        outputs.dir(layout.projectDirectory.dir("dist_keycloak"))
    }

    build {
        dependsOn(named("buildTheme"))
        outputs.dir(layout.projectDirectory.dir("dist_keycloak"))
    }
}