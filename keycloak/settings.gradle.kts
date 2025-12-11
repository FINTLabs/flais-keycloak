rootProject.name = "flais-keycloak"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(":libs:flais-scim-server")
include(":libs:flais-provider")
