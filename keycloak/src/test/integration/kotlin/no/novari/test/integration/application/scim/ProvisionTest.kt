package no.novari.test.integration.application.scim

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Users
import no.novari.test.common.utils.ScimFlow
import no.novari.test.common.utils.ScimFlow.ScimUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

@ExtendWith(KcEnvironmentExtension::class)
class ProvisionTest {
    private val users =
        mapOf(
            Orgs.TELEMARK to
                listOf(
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                                "urn:ietf:params:scim:schemas:extension:fint:2.0:User",
                            ),
                        externalId = Users.ALICE_TELEMARK,
                        userName = Users.ALICE_TELEMARK,
                        active = true,
                        emails = listOf(ScimUser.Email(Users.ALICE_TELEMARK_EMAIL, primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                        fintUserExtension =
                            ScimUser.FintUserExtension(
                                Users.ALICE_TELEMARK,
                                Users.BASIC_LAST_NAME,
                                "1234",
                                "1234",
                                Users.ALICE_TELEMARK_EMAIL,
                            ),
                    ),
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                            ),
                        externalId = Users.JON_TELEMARK,
                        userName = Users.JON_TELEMARK,
                        active = true,
                        emails = listOf(ScimUser.Email(Users.JON_TELEMARK_EMAIL, primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                        fintUserExtension =
                            ScimUser.FintUserExtension(
                                Users.JON_FIRST_NAME,
                                Users.BASIC_LAST_NAME,
                                "1234",
                                "1234",
                                Users.JON_TELEMARK_EMAIL,
                            ),
                    ),
                ),
            Orgs.ROGALAND to
                listOf(
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                                "urn:ietf:params:scim:schemas:extension:fint:2.0:User",
                            ),
                        externalId = Users.ALICE_ROGALAND,
                        userName = Users.ALICE_ROGALAND,
                        active = true,
                        emails = listOf(ScimUser.Email(Users.ALICE_ROGALAND_EMAIL, primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                        fintUserExtension =
                            ScimUser.FintUserExtension(
                                Users.ALICE_FIRST_NAME,
                                Users.BASIC_LAST_NAME,
                                "1234",
                                "1234",
                                Users.ALICE_ROGALAND_EMAIL,
                            ),
                    ),
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                            ),
                        externalId = Users.JON_ROGALAND,
                        userName = Users.JON_ROGALAND,
                        active = true,
                        emails = listOf(ScimUser.Email(Users.JON_ROGALAND_EMAIL, primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                        fintUserExtension =
                            ScimUser.FintUserExtension(
                                Users.JON_FIRST_NAME,
                                Users.BASIC_LAST_NAME,
                                "1234",
                                "1234",
                                Users.JON_ROGALAND_EMAIL,
                            ),
                    ),
                ),
        )

    @ParameterizedTest(name = "provision users to org ({0}) returns ok")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `provision users to org returns ok`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        users[orgAlias]?.forEach { user ->
            ScimFlow
                .createUser(
                    "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}",
                    "${env.flaisScimAuthUrl()}/token",
                    user,
                ).use { resp ->
                    assertEquals(201, resp.code)
                }
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
        val domain =
            when (orgAlias) {
                Orgs.TELEMARK -> "telemark.no"
                Orgs.ROGALAND -> "rogaland.no"
                else -> error("Unsupported test organization: $orgAlias")
            }

        val uniqueId = UUID.randomUUID().toString()
        val username = "patch-email-$uniqueId@$domain"
        val updatedEmail = "updated-$uniqueId@$domain"

        val userId =
            ScimFlow
                .createUser(
                    baseUrl,
                    tokenUrl,
                    ScimUser(
                        schemas = listOf("urn:ietf:params:scim:schemas:core:2.0:User"),
                        externalId = uniqueId,
                        userName = username,
                        active = true,
                        emails = listOf(ScimUser.Email(username, primary = true)),
                    ),
                ).use { response ->
                    val body = requireNotNull(response.body).string()
                    assertEquals(201, response.code, body)
                    Json
                        .parseToJsonElement(body)
                        .jsonObject
                        .getValue("id")
                        .jsonPrimitive.content
                }

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
                baseUrl,
                tokenUrl,
                filter = """userName eq "$username"""",
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
}
