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
    testImplementation(libs.playwright)

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
            disabledForTestTasks.add("systemTest")
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

@Suppress("UnstableApiUsage")
fun JvmTestSuite.commonTestSources() {
    sources {
        kotlin { srcDir("src/test/common/kotlin") }
    }
}

@Suppress("UnstableApiUsage")
fun JvmTestSuite.addSuiteSources(name: String) {
    sources {
        kotlin { srcDir("src/test/$name/kotlin") }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")
    systemProperty("project.rootDir", rootProject.projectDir.absolutePath)

    environment("KEYCLOAK_VERSION", libs.versions.keycloak.get())
    environment("PLAYWRIGHT_VERSION", libs.versions.playwright.get())
}

@Suppress("UnstableApiUsage", "unused")
testing {
    suites {
        withType<JvmTestSuite> {
            useJUnitJupiter()

            dependencies {
                implementation(project())
            }

            if (name != "test") {
                configurations {
                    named("${name}Implementation").configure {
                        extendsFrom(configurations.testImplementation.get())
                    }
                    named("${name}RuntimeOnly").configure {
                        extendsFrom(configurations.testRuntimeOnly.get())
                    }
                    named("${name}CompileOnly").configure {
                        extendsFrom(configurations.testCompileOnly.get())
                    }
                }
            }
        }

        val integrationTest by registering(JvmTestSuite::class) {
            commonTestSources()
            addSuiteSources("integration")

            targets {
                all {
                    testTask.configure {
                        description = "Runs integration tests."
                        group = "verification"
                    }
                }
            }
        }

        val systemTest by registering(JvmTestSuite::class) {
            commonTestSources()
            addSuiteSources("system")

            targets {
                all {
                    testTask.configure {
                        description = "Runs system tests."
                        group = "verification"
                    }
                }
            }
        }
    }
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

tasks.register("runDev") {
    group = "docker"
    description = "Run local dev with compose"

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
        println("Built & started Keycloak dev environment")
    }
}

tasks.register("restartDev") {
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

tasks.register("stopDev") {
    group = "docker"
    description = "Stop local dev"

    doLast {
        dockerCompose.dockerExecutor.execute("compose", "stop")
        println("Stopped Keycloak dev environment")
    }
}

tasks.register("cleanupDev") {
    group = "docker"
    description = "Remove local dev docker"

    doLast {
        dockerCompose.dockerExecutor.execute("compose", "rm", "-s", "-f")
        println("Cleaned up Keycloak dev environment")
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
