import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

group = "no.novari"
version = "1.0"

dependencies {
    implementation(platform(libs.keycloak.spi.bom))
    implementation(platform(libs.resteasy.bom))

    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.scim.server.sdk)
    implementation(libs.nimbusds.jwt)

    compileOnly(libs.keycloak.core)
    compileOnly(libs.keycloak.services)
    compileOnly(libs.keycloak.server.spi)
    compileOnly(libs.keycloak.server.spi.priv)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.resteasy.core)
}

configurations {
    testImplementation {
        extendsFrom(compileOnly.get())
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    minimize {
        exclude(dependency("com.fasterxml.jackson.module:jackson-module-jakarta-xmlbind-annotations:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}
