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
import no.novari.test.common.utils.ScimFlow
import no.novari.test.common.utils.ScimFlow.ScimUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(KcEnvironmentExtension::class)
class ProvisionTest {
    private fun assertEmailsMatch(
        user: ScimUser,
        resource: JsonObject,
    ) {
        val expectedEmails = user.emails

        if (expectedEmails.isNullOrEmpty()) {
            assertFalse(resource.containsKey("emails"))
        } else {
            val actualEmails =
                resource
                    .getValue("emails")
                    .jsonArray
                    .map {
                        it.jsonObject
                            .getValue("value")
                            .jsonPrimitive.content
                    }

            assertEquals(
                expectedEmails.map { it.value }.sorted(),
                actualEmails.sorted(),
            )
        }
    }

    @ParameterizedTest(name = "provision users to org ({0}) returns ok")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `provision users to org returns ok`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val users =
            ScimFlow.provision(
                env = env,
                kcConfig = kcConfig,
                orgAlias = orgAlias,
                users =
                    listOf(
                        ScimFlow.randomUser(orgAlias = orgAlias),
                        ScimFlow.randomUser(orgAlias = orgAlias, email = null),
                        ScimFlow.randomUser(orgAlias = orgAlias, email = null).apply {
                            emails = emptyList()
                        },
                    ),
            )

        users.forEach { user ->
            assertNotNull(user.id)
        }
    }

    @ParameterizedTest(name = "patch work email in org ({0})")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `patch replaces email selected by work type`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val baseUrl =
            "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}"
        val tokenUrl = "${env.flaisScimAuthUrl()}/token"

        val user =
            ScimFlow
                .provision(
                    env = env,
                    kcConfig = kcConfig,
                    orgAlias = orgAlias,
                    users =
                        listOf(
                            ScimFlow.randomUser(orgAlias = orgAlias).apply {
                                emails =
                                    requireNotNull(emails).map {
                                        it.copy(type = "work")
                                    }
                            },
                        ),
                ).single()

        val userId = requireNotNull(user.id)
        val updatedEmail = "updated-${requireNotNull(user.emails).single().value}"

        ScimFlow
            .patchUser(
                baseUrl = baseUrl,
                tokenUrl = tokenUrl,
                id = userId,
                operation =
                    ScimFlow.PatchRequest(
                        operations =
                            listOf(
                                ScimFlow.PatchRequest.PatchOperation(
                                    op = "replace",
                                    path = """emails[type eq "work"].value""",
                                    value = JsonPrimitive(updatedEmail),
                                ),
                            ),
                    ),
            ).use { response ->
                val body = requireNotNull(response.body).string()
                assertEquals(200, response.code, body)
            }

        ScimFlow
            .listUsers(
                baseUrl = baseUrl,
                tokenUrl = tokenUrl,
                filter = """userName eq "${user.userName}"""",
            ).use { response ->
                val body = requireNotNull(response.body).string()
                assertEquals(200, response.code, body)

                val resource =
                    Json
                        .parseToJsonElement(body)
                        .jsonObject
                        .getValue("Resources")
                        .jsonArray
                        .single()
                        .jsonObject

                val email =
                    resource
                        .getValue("emails")
                        .jsonArray
                        .single()
                        .jsonObject

                assertEquals(userId, resource.getValue("id").jsonPrimitive.content)
                assertEquals(updatedEmail, email.getValue("value").jsonPrimitive.content)
                assertEquals("work", email.getValue("type").jsonPrimitive.content)
            }
    }

    @ParameterizedTest(name = "email should be fully omitted if empty in org ({0})")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `email should be fully omitted if empty`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val baseUrl =
            "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}"
        val tokenUrl = "${env.flaisScimAuthUrl()}/token"

        val users =
            listOf(
                ScimFlow.randomUser(orgAlias = orgAlias),
                ScimFlow.randomUser(orgAlias = orgAlias, email = null),
                ScimFlow.randomUser(orgAlias = orgAlias, email = null).apply {
                    emails = emptyList()
                },
            )

        users.forEach { user ->
            val userId =
                ScimFlow
                    .createUser(
                        baseUrl = baseUrl,
                        tokenUrl = tokenUrl,
                        user = user,
                    ).use { response ->
                        val body = requireNotNull(response.body).string()
                        assertEquals(201, response.code, body)

                        val resource = Json.parseToJsonElement(body).jsonObject
                        assertEmailsMatch(user, resource)

                        resource.getValue("id").jsonPrimitive.content
                    }

            ScimFlow
                .listUsers(
                    baseUrl = baseUrl,
                    tokenUrl = tokenUrl,
                    filter = """userName eq "${user.userName}"""",
                ).use { response ->
                    val body = requireNotNull(response.body).string()
                    assertEquals(200, response.code, body)

                    val resource =
                        Json
                            .parseToJsonElement(body)
                            .jsonObject
                            .getValue("Resources")
                            .jsonArray
                            .single()
                            .jsonObject

                    assertEquals(userId, resource.getValue("id").jsonPrimitive.content)
                    assertEmailsMatch(user, resource)
                }
        }
    }

    @ParameterizedTest(name = "delete user with invalid id in org ({0}) returns not found")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `delete user with invalid id returns not found`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        ScimFlow
            .deleteUser(
                baseUrl =
                    "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}",
                tokenUrl = "${env.flaisScimAuthUrl()}/token",
                id = ScimFlow.randomUser(orgAlias = orgAlias).externalId,
            ).use { response ->
                val body = requireNotNull(response.body).string()
                assertEquals(404, response.code, body)
            }
    }
}
