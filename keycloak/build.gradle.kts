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

val koverCli by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

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

    koverCli(libs.kover.cli)
}

allprojects {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.add("integrationTest")
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

dockerCompose {
    environment.put("KEYCLOAK_VERSION", libs.versions.keycloak.get())
}

tasks.register<Exec>("koverIntegrationXmlReport") {
    group = "verification"
    description = "Generates Kover XML report from Keycloak integration test IC data"

    dependsOn(
        subprojects.mapNotNull { p ->
            p.tasks.findByName("classes")?.let { p.path + ":classes" }
        },
    )

    val kovercliJar = koverCli.elements.map { it.single().asFile }
    doFirst {
        val args =
            mutableListOf(
                "java",
                "-jar",
                kovercliJar.get().absolutePath,
                "report",
                layout.buildDirectory
                    .file("kover/keycloak.ic")
                    .get()
                    .asFile.absolutePath,
                "--classfiles",
                "$rootDir/libs/flais-provider/build/classes/kotlin/main",
                "--classfiles",
                "$rootDir/libs/flais-scim-server/build/classes/kotlin/main",
                "--src",
                "$rootDir/libs/flais-provider/src/main/kotlin",
                "--src",
                "$rootDir/libs/flais-scim-server/src/main/kotlin",
                "--xml",
                layout.buildDirectory
                    .dir("reports/kover/report.xml")
                    .get()
                    .asFile.absolutePath,
            )

        commandLine(args)
    }
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
}

tasks.register("deployDev") {
    group = "docker"
    description = "Deploy local dev with compose"

    doLast {
        dockerCompose.dockerExecutor.execute(
            "compose",
            "-f",
            "docker-compose.yaml",
            "-f",
            "docker-compose.dev.yaml",
            "up",
            "-d",
            "--build",
            "keycloak",
        )
        println("Rebuilt & restarted Keycloak")
    }
}

tasks.register("restart") {
    group = "docker"
    description = "Rebuild the keycloak image and recreate the container"

    doLast {
        dockerCompose.dockerExecutor.execute("compose", "build", "--pull", "keycloak")
        dockerCompose.dockerExecutor.execute(
            "compose",
            "-f",
            "docker-compose.yaml",
            "-f",
            "docker-compose.dev.yaml",
            "up",
            "-d",
            "--force-recreate",
            "keycloak",
        )
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
