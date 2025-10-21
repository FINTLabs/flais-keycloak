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

if (!skipScimServer) {
    include(":libs:scim-server")
}

include(":libs:flais-provider")
