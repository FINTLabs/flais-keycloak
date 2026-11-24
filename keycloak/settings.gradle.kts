rootProject.name = "flais-keycloak"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(":libs:flais-scim-server")
include(":libs:flais-provider")

val keycloakVersion =
    Regex("""(?m)^FROM quay\.io/keycloak/keycloak:([^@\s]+)@sha256:[a-f0-9]{64} AS keycloak-base$""")
        .find(file("Dockerfile").readText())
        ?.groupValues
        ?.get(1)
        ?: error("Dockerfile must define a pinned keycloak-base image")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("keycloak", keycloakVersion)
        }
    }
}
