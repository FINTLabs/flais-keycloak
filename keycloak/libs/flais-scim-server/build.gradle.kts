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

dependencies {
    implementation(platform(libs.keycloak.spi.bom))
    implementation(platform(libs.resteasy.bom))
    implementation(platform(libs.netty.bom)) {
        because("Override Keycloak transitive Netty version to avoid CVEs in the bundled version")
    }
    implementation(platform(libs.protobuf.bom)) {
        because("Override Keycloak Services Protobuf version to avoid CVEs in the bundled version")
    }

    implementation(libs.scim.server.sdk)
    implementation(libs.nimbusds.jwt)

    compileOnly(libs.keycloak.core)
    compileOnly(libs.keycloak.services)
    compileOnly(libs.keycloak.server.spi)
    compileOnly(libs.keycloak.server.spi.priv)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.resteasy.core)

    testRuntimeOnly(libs.junit.platform.launcher)
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
