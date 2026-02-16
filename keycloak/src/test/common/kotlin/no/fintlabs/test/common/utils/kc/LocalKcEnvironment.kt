package no.fintlabs.test.common.utils.kc

import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import java.io.File
import java.time.Duration

/**
 * Local test implementation of [KcEnvironment] backed by Testcontainers and Docker Compose.
 * Starts a full Keycloak-based test environment with dependent services.
 * Provides dynamically exposed URLs for use in tests.
 */
class LocalKcEnvironment(
    baseComposeFile: File = File("./docker-compose.yaml"),
    testComposeFile: File = File("./docker-compose.test.yaml"),
) : KcEnvironment {
    private fun koverAgentJar(): File? {
        return File("build/kover")
            .listFiles { f -> f.isFile && f.extension == "jar" }
            ?.toList()
            ?.single()
    }

    private val compose: ComposeContainer =
        ComposeContainer(baseComposeFile, testComposeFile).apply {
            withLocalCompose(true)
            withBuild(true)

            File("build/kover/agent.args").writeText(
                """
                report.file=/kover/keycloak.ic
                report.append=false
                include=no.fintlabs.*
                """.trimIndent(),
            )

            withEnv("KOVER_AGENT_JAR_NAME", koverAgentJar()?.name)
            withEnv("KEYCLOAK_VERSION", System.getenv("KEYCLOAK_VERSION"))

            withExposedService("keycloak", 9000)
            withExposedService("keycloak", 8080)

            waitingFor(
                "keycloak",
                WaitAllStrategy()
                    .withStrategy(
                        Wait
                            .forHttp("/health/ready")
                            .forPort(9000)
                            .withStartupTimeout(Duration.ofMinutes(15)),
                    ).withStrategy(
                        Wait
                            .forHttp("/")
                            .forPort(8080)
                            .withStartupTimeout(Duration.ofMinutes(15)),
                    ),
            )

            withExposedService(
                "authentik",
                9000,
                Wait
                    .forHttp("/-/health/ready/")
                    .forPort(9000)
                    .withStartupTimeout(Duration.ofMinutes(15)),
            )

            withExposedService(
                "flais-scim-auth",
                9090,
                Wait
                    .forHttp("/healthz")
                    .forPort(9090)
                    .withStartupTimeout(Duration.ofMinutes(15)),
            )

            withExposedService(
                "flais-keycloak-demo",
                80,
                Wait
                    .forHttp("/healthz")
                    .forPort(80)
                    .withStartupTimeout(Duration.ofMinutes(15)),
            )
        }

    fun start() {
        compose.start()
    }

    override fun close() {
        compose.stop()
    }

    override val keycloakAdminUser: String = "admin"
    override val keycloakAdminPassword: String = "admin"

    override fun keycloakInternalUrl(): String = "http://keycloak:8080"

    override fun keycloakServiceUrl(): String {
        val host = compose.getServiceHost("keycloak", 8080)
        val port = compose.getServicePort("keycloak", 8080)
        return "http://$host:$port"
    }

    override fun keycloakManagementUrl(): String {
        val host = compose.getServiceHost("keycloak", 9000)
        val port = compose.getServicePort("keycloak", 9000)
        return "http://$host:$port"
    }

    override fun flaisScimAuthUrl(): String {
        val host = compose.getServiceHost("flais-scim-auth", 9090)
        val port = compose.getServicePort("flais-scim-auth", 9090)
        return "http://$host:$port"
    }

    override fun authentikUrl(): String {
        val host = compose.getServiceHost("authentik", 9000)
        val port = compose.getServicePort("authentik", 9000)
        return "http://$host:$port"
    }

    override fun flaisKeycloakDemoUrl(): String {
        val host = compose.getServiceHost("flais-keycloak-demo", 80)
        val port = compose.getServicePort("flais-keycloak-demo", 80)
        return "http://$host:$port"
    }
}
