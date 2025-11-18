rootProject.name = "flais-keycloak"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

val skipScimServer =
    providers
        .gradleProperty("skipScimServer")
        .map { it.toBooleanStrictOrNull() ?: false }
        .getOrElse(false)

include(":libs:flais-scim-server")

include(":libs:flais-provider")

include("libs:flais-scim-server")
