package no.novari.test.system.benchmark.scim

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.utils.KcAdminClient
import no.novari.test.common.utils.ScimFlow
import no.novari.test.common.utils.ScimHttpClient
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.representations.idm.UserRepresentation
import java.io.File
import java.util.Locale
import kotlin.math.ceil

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
    ) = runBenchmark(env, config, traverseAll = false)

    @Test
    fun `benchmark SCIM traversal`(
        env: KcEnvironment,
        config: KcConfig,
    ) = runBenchmark(env, config, traverseAll = true)

    private fun runBenchmark(
        env: KcEnvironment,
        config: KcConfig,
        traverseAll: Boolean,
    ) {
        val baseUrl = "${env.keycloakServiceUrl()}/realms/external/scim/v2/${config.requireOrg(Orgs.TELEMARK).id}"
        val tokenUrl = "${env.flaisScimAuthUrl()}/token"
        val client = OkHttpClient()
        var users = emptyList<UserRepresentation>()
        val reportName = if (traverseAll) "traversal" else "searches"
        val report = File(System.getProperty("project.rootDir"), "build/reports/scim-search-benchmark/$reportName.csv")
        report.parentFile.mkdirs()
        report.writeText("scenario,users,warmups,iterations,min_ms,median_ms,p95_ms,max_ms\n")
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

            val target = email(userCount / 2)
            val cases =
                if (traverseAll) {
                    listOf(SearchCase("full-pagination-$pageSize", total = userCount, count = pageSize))
                } else {
                    listOf(
                        SearchCase("first-page", total = userCount),
                        SearchCase("deep-page", total = userCount, startIndex = userCount - 99),
                        SearchCase("count-only", total = userCount, count = 0),
                        SearchCase("username-eq", "userName eq \"$target\"", 1),
                        SearchCase("external-id-eq", "externalId eq \"benchmark-${userCount / 2}\"", 1),
                        SearchCase("email-value-eq", "emails.value eq \"$target\"", 1),
                        SearchCase("email-type-filter", "emails[type eq \"work\" and value eq \"$target\"]", 1),
                        SearchCase("email-primary-filter", "emails[primary eq true and value eq \"$target\"]", 1),
                        SearchCase("substring", "userName co \"benchmark-\"", userCount),
                        SearchCase("active", "active eq true", (userCount + 1) / 2),
                        SearchCase("role-eq", "roles.value eq \"reader\"", userCount),
                        SearchCase("no-match", "userName eq \"missing@telemark.no\"", 0),
                    )
                }

            for (case in cases) {
                fun sample(): Double {
                    // Reuse connections and fetch a fresh token outside each sample's timing window.
                    val token = ScimHttpClient.getAccessToken(tokenUrl)
                    val searchClient =
                        client
                            .newBuilder()
                            .apply {
                                addInterceptor { chain ->
                                    chain.proceed(
                                        chain
                                            .request()
                                            .newBuilder()
                                            .header("Authorization", "Bearer $token")
                                            .build(),
                                    )
                                }
                            }.build()
                    return if (traverseAll) {
                        traverseUsers(baseUrl, tokenUrl, searchClient, users.map { it.id }.toSet())
                    } else {
                        search(baseUrl, tokenUrl, searchClient, case)
                    }
                }
                repeat(warmups) { sample() }
                val samples = List(iterations) { sample() }.sorted()
                val middle = samples.size / 2
                val median = if (samples.size % 2 == 0) (samples[middle - 1] + samples[middle]) / 2 else samples[middle]
                val timings = listOf(samples.first(), median, samples[ceil(samples.size * 0.95).toInt() - 1], samples.last())
                val row =
                    listOf(case.name, userCount, warmups, iterations).joinToString(",") +
                        "," + timings.joinToString(",") { String.format(Locale.ROOT, "%.3f", it) }
                report.appendText("$row\n")
                println(row)
            }
            println("SCIM search benchmark report: ${report.absolutePath}")
        } finally {
            try {
                KcAdminClient.deleteUsers(realm, users)
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
                admin.close()
            }
        }
    }

    private fun search(
        baseUrl: String,
        tokenUrl: String,
        client: OkHttpClient,
        case: SearchCase,
    ): Double {
        val started = System.nanoTime()
        val body =
            ScimFlow
                .listUsers(
                    baseUrl,
                    tokenUrl,
                    filter = case.filter,
                    startIndex = case.startIndex,
                    count = case.count,
                    sortBy = "userName",
                    sortOrder = "ascending",
                    httpClient = client,
                ).use { response ->
                    val text = response.body.string()
                    assertEquals(200, response.code, text)
                    text
                }
        val elapsed = (System.nanoTime() - started) / 1_000_000.0
        val result = Json.parseToJsonElement(body).jsonObject
        assertEquals(case.total, result.getValue("totalResults").jsonPrimitive.int, case.name)
        val resources = result["Resources"]?.jsonArray.orEmpty()
        assertEquals(minOf(case.count, (case.total - case.startIndex + 1).coerceAtLeast(0)), resources.size, case.name)
        if (case.total == 1) {
            assertEquals(
                email(userCount / 2),
                resources
                    .single()
                    .jsonObject
                    .getValue("userName")
                    .jsonPrimitive.content,
            )
        }
        return elapsed
    }

    private fun traverseUsers(
        baseUrl: String,
        tokenUrl: String,
        client: OkHttpClient,
        expectedIds: Set<String>,
    ): Double {
        val seen = mutableSetOf<String>()
        var startIndex = 1
        var elapsed = 0.0
        while (seen.size < userCount) {
            val started = System.nanoTime()
            val body =
                ScimFlow
                    .listUsers(
                        baseUrl,
                        tokenUrl,
                        startIndex = startIndex,
                        count = pageSize,
                        sortBy = "userName",
                        sortOrder = "ascending",
                        httpClient = client,
                    ).use { response ->
                        val text = response.body.string()
                        assertEquals(200, response.code, text)
                        text
                    }
            elapsed += (System.nanoTime() - started) / 1_000_000.0
            val result = Json.parseToJsonElement(body).jsonObject
            assertEquals(userCount, result.getValue("totalResults").jsonPrimitive.int)
            assertEquals(startIndex, result.getValue("startIndex").jsonPrimitive.int)
            val resources = result["Resources"]?.jsonArray.orEmpty()
            check(resources.isNotEmpty()) { "Empty page at $startIndex before all users were read" }
            check(resources.size <= pageSize) { "Page exceeds requested size" }
            resources.forEach { resource ->
                val id =
                    resource.jsonObject
                        .getValue("id")
                        .jsonPrimitive.content
                check(seen.add(id)) { "Duplicate user $id at page starting at $startIndex" }
            }
            startIndex += resources.size
        }
        assertEquals(expectedIds, seen, "Pagination must return every seeded user exactly once")
        return elapsed
    }

    private fun email(index: Int) = "benchmark-${index.toString().padStart(7, '0')}@telemark.no"

    private fun setting(
        name: String,
        default: Int,
        minimum: Int,
    ): Int = (System.getenv(name)?.toInt() ?: default).also { require(it >= minimum) { "$name must be >= $minimum" } }

    private data class SearchCase(
        val name: String,
        val filter: String? = null,
        val total: Int,
        val startIndex: Int = 1,
        val count: Int = 100,
    )
}
