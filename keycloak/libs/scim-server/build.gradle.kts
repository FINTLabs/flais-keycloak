plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    fun runAndCapture(vararg cmd: String): String =
        ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .readText()
            .trim()

    val ghUser = runAndCapture("gh", "api", "user", "--jq", ".login")
    val ghToken = runAndCapture("gh", "auth", "token")

    maven {
        url = uri("https://maven.pkg.github.com/Metatavu/keycloak-scim-server")
        credentials {
            username = ghUser
            password = ghToken
        }
    }
}

val scimOnly by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    add(
        scimOnly.name,
        libs.keycloak.scim.server
            .get(),
    )
}

tasks.register<Copy>("copyScimJar") {
    from(scimOnly)
    into(layout.projectDirectory)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.build {
    finalizedBy(tasks.named("copyScimJar"))
}
