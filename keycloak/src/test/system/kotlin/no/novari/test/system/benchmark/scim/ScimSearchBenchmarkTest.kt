package no.novari.test.system.benchmark.scim

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.utils.KcAdminClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.representations.idm.UserRepresentation
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.k6.K6Container
import org.testcontainers.utility.MountableFile
import java.io.File
import java.time.Duration

@EnabledIfEnvironmentVariable(named = "SCIM_SEARCH_BENCHMARK", matches = "true")
@ExtendWith(KcEnvironmentExtension::class)
class ScimSearchBenchmarkTest {
    private val userCount = setting("SCIM_BENCHMARK_USERS", 10_000, 1000)
    private val warmups = setting("SCIM_BENCHMARK_WARMUPS", 5, 1)
    private val iterations = setting("SCIM_BENCHMARK_ITERATIONS", 30, 2)
    private val pageSize = setting("SCIM_BENCHMARK_PAGE_SIZE", 100, 1)

    @Test
    fun `benchmark SCIM searches`(
        env: KcEnvironment,
        config: KcConfig,
    ) = runBenchmark(env, config, "searches")

    @Test
    fun `benchmark SCIM traversal`(
        env: KcEnvironment,
        config: KcConfig,
    ) = runBenchmark(env, config, "traversal")

    private fun runBenchmark(
        env: KcEnvironment,
        config: KcConfig,
        mode: String,
    ) {
        val reportDir = File(System.getProperty("benchmark.reportDir"), mode).apply { mkdirs() }
        listOf("summary.json", "results.csv", "report.html").forEach { File(reportDir, it).delete() }
        val fixture = File(reportDir, "fixture.json")
        var users = emptyList<UserRepresentation>()
        val (admin, realm) = KcAdminClient.connect(env, "external")
        try {
            users =
                KcAdminClient.addScaleTestUsers(
                    env = env,
                    kcConfig = config,
                    realmName = "external",
                    orgAlias = Orgs.TELEMARK,
                    userCount = userCount,
                    failureThresholdPercent = 0.0,
                )
            fixture.writeText(
                buildJsonObject {
                    put("baseUrl", "${env.keycloakInternalUrl()}/realms/external/scim/v2/${config.requireOrg(Orgs.TELEMARK).id}")
                    put("tokenUrl", "http://flais-scim-auth:9090/token")
                    put("mode", mode)
                    put("users", userCount)
                    put("warmups", warmups)
                    put("iterations", iterations)
                    put("pageSize", pageSize)
                    put("expectedIds", JsonArray(users.map { JsonPrimitive(it.id) }))
                }.toString(),
            )
            runK6(fixture, reportDir)
            println("k6 benchmark reports: ${reportDir.absolutePath}")
        } finally {
            try {
                KcAdminClient.deleteUsers(realm, users)
            } finally {
                admin.close()
                fixture.delete()
            }
        }
    }

    private fun runK6(
        fixture: File,
        reportDir: File,
    ) {
        K6Container("grafana/k6:2.2.0")
            .withNetworkMode("keycloak")
            .withWorkingDirectory("/home/k6")
            .withTestScript(MountableFile.forClasspathResource("k6/scim-benchmark.js"))
            .withCopyFileToContainer(MountableFile.forHostPath(fixture.toPath()), "/home/k6/fixture.json")
            .withScriptVar("SCIM_FIXTURE", "/home/k6/fixture.json")
            .withCmdOptions("--no-usage-report", "--no-color", "--quiet")
            .withStartupAttempts(1)
            .withStartupCheckStrategy(OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(62)))
            .use { container ->
                val execution = runCatching { container.start() }
                val reports =
                    runCatching {
                        File(reportDir, "k6.log").writeText(container.logs)
                        println(container.logs)
                        listOf("summary.json", "results.csv", "report.html").forEach { name ->
                            container.copyFileFromContainer("/home/k6/$name", File(reportDir, name).absolutePath)
                        }
                    }
                execution.exceptionOrNull()?.let { failure ->
                    reports.exceptionOrNull()?.let(failure::addSuppressed)
                    throw IllegalStateException("k6 failed; see ${File(reportDir, "k6.log")}", failure)
                }
                reports.getOrThrow()
            }
    }

    private fun setting(
        name: String,
        default: Int,
        minimum: Int,
    ): Int = (System.getenv(name)?.toInt() ?: default).also { require(it >= minimum) { "$name must be >= $minimum" } }
}
