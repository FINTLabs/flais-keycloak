package no.fintlabs.utils

import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import java.io.File
import java.time.Duration

/**
 * Testcontainers-based environment for running Keycloak and related providers in integration tests.
 *
 * Simplifies the process of setting up the environment in tests.
 */
class KcComposeEnvironment(
    composeFile: File = File("./docker-compose.yaml"),
) : AutoCloseable {
    private val compose: ComposeContainer =
        ComposeContainer(composeFile).apply {
            withBuild(true)

            withEnv("COMPOSE_PROFILES", "test")
            withEnv("KEYCLOAK_VERSION", System.getenv("KEYCLOAK_VERSION"))

            withExposedService("keycloak-test", 1, 9000)
            withExposedService("keycloak-test", 1, 8080)

            waitingFor(
                "keycloak-test",
                WaitAllStrategy()
                    .withStrategy(
                        Wait
                            .forHttp("/health/ready")
                            .forPort(9000)
                            .withStartupTimeout(Duration.ofMinutes(2)),
                    ).withStrategy(
                        Wait
                            .forHttp("/")
                            .forPort(8080)
                            .withStartupTimeout(Duration.ofMinutes(2)),
                    ),
            )

            withExposedService(
                "flais-scim-client-telemark",
                9090,
                Wait
                    .forHttp("/healthz")
                    .forPort(9090)
                    .withStartupTimeout(Duration.ofMinutes(1)),
            )
            withExposedService(
                "flais-scim-client-rogaland",
                9090,
                Wait
                    .forHttp("/healthz")
                    .forPort(9090)
                    .withStartupTimeout(Duration.ofMinutes(1)),
            )

            withExposedService(
                "dex-entra-telemark",
                5556,
                Wait
                    .forHttp("/dex/.well-known/openid-configuration")
                    .forPort(5556)
                    .withStartupTimeout(Duration.ofMinutes(1)),
            )
            withExposedService(
                "dex-entra-novari",
                5556,
                Wait
                    .forHttp("/dex/.well-known/openid-configuration")
                    .forPort(5556)
                    .withStartupTimeout(Duration.ofMinutes(1)),
            )
            withExposedService(
                "dex-idporten",
                5556,
                Wait
                    .forHttp("/dex/.well-known/openid-configuration")
                    .forPort(5556)
                    .withStartupTimeout(Duration.ofMinutes(1)),
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
        val host = compose.getServiceHost("keycloak-test", 8080)
        val port = compose.getServicePort("keycloak-test", 8080)
        return "http://$host:$port"
    }

    fun keycloakManagementUrl(): String {
        val host = compose.getServiceHost("keycloak-test", 9000)
        val port = compose.getServicePort("keycloak-test", 9000)
        return "http://$host:$port"
    }

    fun scimClientTelemarkUrl(): String {
        val host = compose.getServiceHost("flais-scim-client-telemark", 9090)
        val port = compose.getServicePort("flais-scim-client-telemark", 9090)
        return "http://$host:$port"
    }

    fun scimClientRogalandUrl(): String {
        val host = compose.getServiceHost("flais-scim-client-rogaland", 9090)
        val port = compose.getServicePort("flais-scim-client-rogaland", 9090)
        return "http://$host:$port"
    }
}
