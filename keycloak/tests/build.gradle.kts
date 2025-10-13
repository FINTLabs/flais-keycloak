plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "no.fintlabs"
version = "unspecified"

kotlin {
    jvmToolchain(21)
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
}

tasks.test {
    useJUnitPlatform()
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
    systemProperty("project.rootDir", rootProject.projectDir.absolutePath)
    environment("KEYCLOAK_VERSION", libs.versions.keycloak.get())
}
