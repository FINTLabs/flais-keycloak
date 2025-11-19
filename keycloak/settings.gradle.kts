rootProject.name = "flais-keycloak"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

val skipFlaisScimServer =
    providers
        .gradleProperty("skipFlaisScimServer")
        .map { it.toBooleanStrictOrNull() ?: false }
        .getOrElse(false)

val skipFlaisProvider =
    providers
        .gradleProperty("skipFlaisProvider")
        .map { it.toBooleanStrictOrNull() ?: false }
        .getOrElse(false)

if (!skipFlaisScimServer) {
    include(":libs:flais-scim-server")
}

if (!skipFlaisProvider) {
    include(":libs:flais-provider")
}
