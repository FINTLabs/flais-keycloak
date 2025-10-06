package no.fintlabs.utils

import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.time.Duration

class KeycloakComposeEnvironment(
    composeFile: File = File("../docker-compose.yaml")
) : AutoCloseable {
    private val compose: ComposeContainer =
        ComposeContainer(composeFile).apply {
            withBuild(true)
            withEnv("KEYCLOAK_VERSION", System.getenv("KEYCLOAK_VERSION"))

            withExposedService(
                "keycloak",
                1,
                9000,
                Wait.forHttp("/health/ready")
                    .withStartupTimeout(Duration.ofMinutes(5))
            )

            withExposedService("keycloak", 1, 8080)

            withExposedService(
                "dex-entra-telemark",
                5556,
                Wait.forHttp("/dex/.well-known/openid-configuration")
                    .forPort(5556)
                    .withStartupTimeout(Duration.ofMinutes(2))
            )
            withExposedService(
                "dex-entra-novari",
                5556,
                Wait.forHttp("/dex/.well-known/openid-configuration")
                    .forPort(5556)
                    .withStartupTimeout(Duration.ofMinutes(2))
            )
            withExposedService(
                "dex-idporten",
                5556,
                Wait.forHttp("/dex/.well-known/openid-configuration")
                    .forPort(5556)
                    .withStartupTimeout(Duration.ofMinutes(2))
            )
        }

    fun start() {
        compose.start()
    }

    override fun close() {
        compose.stop()
    }

    val keycloakAdminUser: String = "admin"
    val keycloakAdminPassword: String = "admin"

    fun keycloakServiceUrl(): String {
        val host = compose.getServiceHost("keycloak", 8080)
        val port = compose.getServicePort("keycloak", 8080)
        return "http://$host:$port"
    }

    fun keycloakManagementUrl(): String {
        val host = compose.getServiceHost("keycloak", 9000)
        val port = compose.getServicePort("keycloak", 9000)
        return "http://$host:$port"
    }

    fun dexTelemarkUrl(): String {
        val host = compose.getServiceHost("dex-entra-telemark", 5556)
        val port = compose.getServicePort("dex-entra-telemark", 5556)
        return "http://$host:$port/dex"
    }
    fun dexNovariUrl(): String {
        val host = compose.getServiceHost("dex-entra-novari", 5556)
        val port = compose.getServicePort("dex-entra-novari", 5556)
        return "http://$host:$port/dex"
    }
    fun dexIdportenUrl(): String {
        val host = compose.getServiceHost("dex-idporten", 5556)
        val port = compose.getServicePort("dex-idporten", 5556)
        return "http://$host:$port/dex"
    }
}