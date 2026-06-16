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

buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.codehaus.plexus") {
                useVersion(libs.versions.plexus.get())
                because("Override Shadow Gradle Plugin transitive Plexus-Utils version to avoid CVEs in the bundled version")
            }
            if (requested.group == "org.apache.logging.log4j") {
                useVersion(libs.versions.log4j.get())
                because("Override Shadow Gradle Plugin transitive Log4j version to avoid CVEs in the bundled version")
            }
        }
    }
}

group = "no.novari"

dependencies {
    implementation(platform(libs.keycloak.spi.bom))
    implementation(platform(libs.netty.bom)) {
        because("Override Keycloak Services transitive Netty version to avoid CVEs in the bundled version")
    }
    implementation(platform(libs.protobuf.bom)) {
        because("Override Keycloak Services Protobuf version to avoid CVEs in the bundled version")
    }

    compileOnly(libs.keycloak.core)
    compileOnly(libs.keycloak.services)
    compileOnly(libs.keycloak.server.spi)
    compileOnly(libs.keycloak.server.spi.priv)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)

    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

configurations {
    testImplementation {
        extendsFrom(compileOnly.get())
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}
