rootProject.name = "flais-keycloak"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(":libs:flais-provider")

if (file("tests").isDirectory) {
    include(":tests")
}
