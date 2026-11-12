package no.novari.test.integration.application.scim

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Realms
import no.novari.test.common.utils.ScimFlow
import no.novari.test.common.utils.ScimHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(KcEnvironmentExtension::class)
class ScimUserLocationTest {
    @ParameterizedTest(name = "POST user Location header resolves to the created user in {0}")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `post returns a canonical Location header that retrieves the created user`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        withUser(orgAlias, env, kcConfig) {
            assertEquals(expectedLocation, location)
            assertEquals(
                created
                    .getValue("meta")
                    .jsonObject
                    .getValue("location")
                    .jsonPrimitive.content,
                location,
            )

            val request =
                Request
                    .Builder()
                    .url(requireNotNull(location))
                    .get()
                    .build()
            val retrieved = client.newCall(request).execute().readResource(200)

            assertMeta(retrieved, id, expectedLocation)
            assertEquals(user.userName, retrieved.getValue("userName").jsonPrimitive.content)
        }
    }

    @ParameterizedTest(name = "POST user JSON metadata remains canonical in {0}")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `post returns canonical resource type and location`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        withUser(orgAlias, env, kcConfig) {
            assertMeta(created, id, expectedLocation)
        }
    }

    @ParameterizedTest(name = "GET user JSON metadata remains canonical in {0}")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `get returns canonical resource type and location`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        withUser(orgAlias, env, kcConfig) {
            val location =
                created
                    .getValue("meta")
                    .jsonObject
                    .getValue("location")
                    .jsonPrimitive.content
            val request =
                Request
                    .Builder()
                    .url(location)
                    .get()
                    .build()
            val retrieved = client.newCall(request).execute().readResource(200)
            assertMeta(retrieved, id, expectedLocation)
            assertEquals(user.userName, retrieved.getValue("userName").jsonPrimitive.content)
        }
    }

    @ParameterizedTest(name = "PUT user JSON metadata remains canonical in {0}")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `update returns canonical resource type and location`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        withUser(orgAlias, env, kcConfig) {
            val replaced =
                ScimFlow
                    .updateUser(
                        baseUrl,
                        tokenUrl,
                        id,
                        user.copy(active = false),
                        client,
                    ).readResource(200)
            assertMeta(replaced, id, expectedLocation)
            assertEquals(
                false,
                replaced
                    .getValue("active")
                    .jsonPrimitive.content
                    .toBooleanStrict(),
            )
        }
    }

    @ParameterizedTest(name = "PATCH user JSON metadata remains canonical in {0}")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `patch returns canonical resource type and location`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        withUser(orgAlias, env, kcConfig) {
            val patched =
                ScimFlow
                    .patchUser(
                        baseUrl,
                        tokenUrl,
                        id,
                        ScimFlow.PatchRequest(
                            listOf(ScimFlow.PatchRequest.PatchOperation("replace", "active", JsonPrimitive(false))),
                        ),
                        client,
                    ).readResource(200)
            assertMeta(patched, id, expectedLocation)
            assertEquals(
                false,
                patched
                    .getValue("active")
                    .jsonPrimitive.content
                    .toBooleanStrict(),
            )
        }
    }

    @ParameterizedTest(name = "list user JSON metadata remains canonical in {0}")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `list returns canonical resource type and location`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        withUser(orgAlias, env, kcConfig) {
            listOf(false, true).forEach { cursorPagination ->
                val page =
                    ScimFlow
                        .listUsers(
                            baseUrl,
                            tokenUrl,
                            filter = """userName eq "${user.userName}"""",
                            startIndex = if (cursorPagination) null else 1,
                            count = 1,
                            cursor = if (cursorPagination) "" else null,
                            httpClient = client,
                        ).readResource(200)
                assertEquals(
                    1,
                    page
                        .getValue("totalResults")
                        .jsonPrimitive.content
                        .toInt(),
                )
                val listed =
                    page
                        .getValue("Resources")
                        .jsonArray
                        .single()
                        .jsonObject
                assertMeta(listed, id, expectedLocation)
            }
        }
    }

    private fun withUser(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
        test: UserFixture.() -> Unit,
    ) {
        val baseUrl = "${env.keycloakServiceUrl()}/realms/${Realms.EXTERNAL}/scim/v2/${kcConfig.requireOrg(orgAlias).id}"
        val tokenUrl = "${env.flaisScimAuthUrl()}/token"
        val client = ScimHttpClient.create(tokenUrl)
        val user =
            ScimFlow.randomUser(
                domain =
                    when (orgAlias) {
                        Orgs.TELEMARK -> "telemark.no"
                        Orgs.ROGALAND -> "rogaland.no"
                        else -> error("Unsupported organization: $orgAlias")
                    },
            )
        val response = ScimFlow.createUser(baseUrl, tokenUrl, user, client)
        val location = response.header("Location")
        val created = response.readResource(201)
        val fixture = UserFixture(baseUrl, tokenUrl, client, user, created, location)

        try {
            fixture.test()
        } finally {
            ScimFlow.deleteUser(baseUrl, tokenUrl, fixture.id, client).use { response ->
                assertEquals(204, response.code, response.body.string())
            }
        }
    }

    private data class UserFixture(
        val baseUrl: String,
        val tokenUrl: String,
        val client: OkHttpClient,
        val user: ScimFlow.ScimUser,
        val created: JsonObject,
        val location: String?,
    ) {
        val id = created.getValue("id").jsonPrimitive.content
        val expectedLocation = "$baseUrl/Users/$id"
    }

    private fun Response.readResource(expectedStatus: Int): JsonObject =
        use {
            val content = body.string()
            assertEquals(expectedStatus, code, content)
            Json.parseToJsonElement(content).jsonObject
        }

    private fun assertMeta(
        resource: JsonObject,
        expectedId: String,
        expectedLocation: String,
    ) {
        assertEquals(expectedId, resource.getValue("id").jsonPrimitive.content)
        val meta = resource.getValue("meta").jsonObject
        assertEquals("User", meta.getValue("resourceType").jsonPrimitive.content)
        assertEquals(expectedLocation, meta.getValue("location").jsonPrimitive.content)
    }
}
