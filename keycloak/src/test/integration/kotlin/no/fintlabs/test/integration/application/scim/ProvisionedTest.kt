package no.fintlabs.test.integration.application.scim

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.fintlabs.test.common.config.KcConfig
import no.fintlabs.test.common.extensions.kc.KcEnvExtension
import no.fintlabs.test.common.utils.kc.KcAdminClient
import no.fintlabs.test.common.utils.kc.KcEnvironment
import no.fintlabs.test.integration.utils.KcFlow.loginWithUser
import no.fintlabs.test.integration.utils.ScimFlow
import no.fintlabs.test.integration.utils.ScimFlow.ScimUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KcEnvExtension::class)
class ProvisionedTest {
    private val realm = "external"

    private val users =
        mapOf(
            "telemark" to
                listOf(
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                                "urn:ietf:params:scim:schemas:extension:fint:2.0:User",
                            ),
                        externalId = "11111111-1111-1111-1111-111111111111",
                        userName = "alice.basic@telemark.no",
                        active = true,
                        name = ScimUser.Name("Alice", "Basic"),
                        emails = listOf(ScimUser.Email("alice.basic@telemark.no", primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                        fintUserExtension = ScimUser.FintUserExtension("1234", "1234", "alice.basic@telemark.no"),
                    ),
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                            ),
                        externalId = "22222222-2222-2222-2222-222222222222",
                        userName = "jon.basic@telemark.no",
                        active = true,
                        name = ScimUser.Name("Jon", "Basic"),
                        emails = listOf(ScimUser.Email("jon.basic@telemark.no", primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                        fintUserExtension = ScimUser.FintUserExtension("1234", "1234", "jon.basic@telemark.no"),
                    ),
                ),
            "rogaland" to
                listOf(
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                                "urn:ietf:params:scim:schemas:extension:fint:2.0:User",
                            ),
                        externalId = "11111111-1111-1111-1111-111111111111",
                        userName = "alice.basic@rogaland.no",
                        active = true,
                        name = ScimUser.Name("Alice", "Basic"),
                        emails = listOf(ScimUser.Email("alice.basic@rogaland.no", primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                        fintUserExtension = ScimUser.FintUserExtension("1234", "1234", "alice.basic@rogaland.no"),
                    ),
                    ScimUser(
                        schemas =
                            listOf(
                                "urn:ietf:params:scim:schemas:core:2.0:User",
                            ),
                        externalId = "22222222-2222-2222-2222-222222222222",
                        userName = "jon.basic@rogaland.no",
                        active = true,
                        name = ScimUser.Name("Jon", "Basic"),
                        emails = listOf(ScimUser.Email("jon.basic@rogaland.no", primary = true)),
                        roles =
                            listOf(
                                ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                                ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                            ),
                        fintUserExtension = ScimUser.FintUserExtension("1234", "1234", "jon.basic@rogaland.no"),
                    ),
                ),
        )

    @BeforeEach
    fun provisionOnce(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            KcAdminClient.deleteAllUsers(realmRes)
        }

        users["telemark"]?.forEach { user ->
            ScimFlow
                .createUser(
                    "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg("telemark").id}",
                    "${env.flaisScimAuthUrl()}/token",
                    user,
                ).use { resp ->
                    assertEquals(201, resp.code)
                }
        }
        users["rogaland"]?.forEach { user ->
            ScimFlow
                .createUser(
                    "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg("rogaland").id}",
                    "${env.flaisScimAuthUrl()}/token",
                    user,
                ).use { resp ->
                    assertEquals(201, resp.code)
                }
        }
    }

    @ParameterizedTest(name = "provisioned users in org ({0}) exists")
    @ValueSource(strings = ["telemark", "rogaland"])
    fun `provisioned users in org exists`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            users[orgAlias]?.forEach { user ->
                val kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)
            }
        }
    }

    @ParameterizedTest(name = "provisioned users in org ({0}) linked to correct idp")
    @ValueSource(strings = ["telemark", "rogaland"])
    fun `provisioned users in org linked to correct idp`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            users[orgAlias]?.forEach { user ->
                val kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)
                val identities = KcAdminClient.getFederatedIdentities(realmRes, kcUser.id)
                assertNotNull(identities)
                assertEquals("entra-$orgAlias", identities.first().identityProvider)
            }
        }
    }

    @ParameterizedTest(name = "provisioned users in org ({0}) can login")
    @ValueSource(strings = ["telemark", "rogaland"])
    fun `provisioned users in org can login`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val clientId = "flais-keycloak-demo"
        val idpAlias = "entra-$orgAlias"

        users[orgAlias]?.forEach { user ->
            loginWithUser(
                env,
                clientId,
                orgAlias,
                idpAlias,
                user.userName,
                "password",
                hasIdpSelector = (orgAlias == "telemark"),
            ).use { resp ->
                assertEquals(200, resp.code)

                assertNotNull(resp.request.url.queryParameter("code"))
            }
        }
    }

    @ParameterizedTest(name = "updating provisioned users in org ({0}) updates users with new info")
    @ValueSource(strings = ["telemark", "rogaland"])
    fun `updating provisioned users in org updates users with new info`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            users[orgAlias]?.forEach { templateUser ->
                val user = templateUser.copy()
                val newName = ScimUser.Name("new", "name")
                val newEmails = listOf(ScimUser.Email("new-${user.emails.first().value}", primary = true))

                user.id = KcAdminClient.findUserByUsername(realmRes, user.userName)?.id
                user.name = newName
                user.emails = newEmails

                ScimFlow
                    .updateUser(
                        "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}",
                        "${env.flaisScimAuthUrl()}/token",
                        user.id!!,
                        user,
                    ).use { resp ->
                        assertEquals(200, resp.code)
                    }

                val kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)
                assertEquals(user.name.givenName, kcUser.firstName)
                assertEquals(user.name.familyName, kcUser.lastName)
                assertEquals(user.emails.first().value, kcUser.email)
            }
        }
    }

    @ParameterizedTest(name = "patching provisioned users in org ({0}) patches users with new info")
    @ValueSource(strings = ["telemark", "rogaland"])
    fun `patching provisioned users in org patches users with new info`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        val newRoles =
            JsonArray(
                listOf(
                    JsonObject(
                        mapOf(
                            "primary" to JsonPrimitive(false),
                            "type" to JsonPrimitive("WindowsAzureActiveDirectoryRole"),
                            "display" to JsonPrimitive("manager"),
                            "value" to JsonPrimitive("manager"),
                        ),
                    ),
                    JsonObject(
                        mapOf(
                            "primary" to JsonPrimitive(false),
                            "type" to JsonPrimitive("WindowsAzureActiveDirectoryRole"),
                            "display" to JsonPrimitive("admin"),
                            "value" to JsonPrimitive("admin"),
                        ),
                    ),
                ),
            )

        kc.use {
            users[orgAlias]?.forEach { user ->
                var kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)

                ScimFlow
                    .patchUser(
                        "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}",
                        "${env.flaisScimAuthUrl()}/token",
                        kcUser.id,
                        ScimFlow.PatchRequest(
                            listOf(
                                ScimFlow.PatchRequest.PatchOperation(
                                    "replace",
                                    "roles",
                                    newRoles,
                                ),
                            ),
                        ),
                    ).use { resp ->
                        assertEquals(200, resp.code)
                    }

                kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)
                assertEquals(
                    newRoles.map { role ->
                        role.jsonObject["value"]!!.jsonPrimitive.content
                    },
                    kcUser.attributes["roles"],
                )
            }
        }
    }

    @ParameterizedTest(name = "patching provisioned users in org ({0}) using full urn definition patches users with new info")
    @ValueSource(strings = ["telemark", "rogaland"])
    fun `patching provisioned users in org using full urn definition patches users with new info`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        val coreUrn = "urn:ietf:params:scim:schemas:core:2.0:User"

        val entraCoreUserAttributes =
            JsonObject(
                mapOf(
                    "$coreUrn:name.givenName" to JsonPrimitive("Jeanette"),
                    "$coreUrn:name.familyName" to JsonPrimitive("Bergersen"),
                ),
            )

        val extensionUrnPrefix = "urn:ietf:params:scim:schemas:extension:fint:2.0:User"
        val standardFintUserExtensionAttributes =
            JsonObject(
                mapOf(
                    extensionUrnPrefix to
                        JsonObject(
                            mapOf(
                                "employeeId" to JsonPrimitive("111111111111"),
                                "studentNumber" to JsonPrimitive("111111111111"),
                                "userPrincipalName" to JsonPrimitive("test@test.no"),
                                "userPrincipalName" to JsonPrimitive("test@test.no"),
                            ),
                        ),
                ),
            )

        val entraFintUserExtensionAttributes =
            JsonObject(
                mapOf(
                    "$extensionUrnPrefix:employeeId" to JsonPrimitive("111111111111"),
                    "$extensionUrnPrefix:studentNumber" to JsonPrimitive("111111111111"),
                    "$extensionUrnPrefix:userPrincipalName" to JsonPrimitive("test@test.no"),
                ),
            )

        kc.use {
            users[orgAlias]?.forEach { user ->
                var kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)

                ScimFlow
                    .patchUser(
                        "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}",
                        "${env.flaisScimAuthUrl()}/token",
                        kcUser.id,
                        ScimFlow.PatchRequest(
                            listOf(
                                ScimFlow.PatchRequest.PatchOperation(
                                    "replace",
                                    value = standardFintUserExtensionAttributes,
                                ),
                            ),
                        ),
                    ).use { resp ->
                        assertEquals(200, resp.code)
                    }

                kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)
                val fintUserExtension =
                    standardFintUserExtensionAttributes
                        .jsonObject[extensionUrnPrefix]!!
                        .jsonObject

                assertEquals(
                    fintUserExtension["employeeId"]!!.jsonPrimitive.content,
                    kcUser.attributes["employeeId"]?.first(),
                )

                assertEquals(
                    fintUserExtension["studentNumber"]!!.jsonPrimitive.content,
                    kcUser.attributes["studentNumber"]?.first(),
                )

                assertEquals(
                    fintUserExtension["userPrincipalName"]!!.jsonPrimitive.content,
                    kcUser.attributes["userPrincipalName"]?.first(),
                )
            }

            users[orgAlias]?.forEach { user ->
                var kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)

                ScimFlow
                    .patchUser(
                        "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}",
                        "${env.flaisScimAuthUrl()}/token",
                        kcUser.id,
                        ScimFlow.PatchRequest(
                            listOf(
                                ScimFlow.PatchRequest.PatchOperation(
                                    "replace",
                                    value = entraFintUserExtensionAttributes,
                                ),
                            ),
                        ),
                    ).use { resp ->
                        assertEquals(200, resp.code)
                    }

                kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)
                entraFintUserExtensionAttributes
                    .filterKeys { it.startsWith(extensionUrnPrefix) }
                    .forEach { (key, value) ->
                        val attributeName = key.substringAfterLast(":")

                        assertEquals(
                            value.jsonPrimitive.content,
                            kcUser.attributes[attributeName]?.first(),
                            "Mismatch for attribute $attributeName",
                        )
                    }
            }

            users[orgAlias]?.forEach { user ->
                var kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)

                ScimFlow
                    .patchUser(
                        "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}",
                        "${env.flaisScimAuthUrl()}/token",
                        kcUser.id,
                        ScimFlow.PatchRequest(
                            listOf(
                                ScimFlow.PatchRequest.PatchOperation(
                                    "replace",
                                    value = entraCoreUserAttributes,
                                ),
                            ),
                        ),
                    ).use { resp ->
                        assertEquals(200, resp.code)
                    }

                kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)

                assertEquals("Jeanette", kcUser.firstName, "Mismatch for givenName/firstName")
                assertEquals("Bergersen", kcUser.lastName, "Mismatch for familyName/lastName")
            }
        }
    }

    @ParameterizedTest(name = "deprovision users in org ({0}) removed")
    @ValueSource(strings = ["telemark", "rogaland"])
    fun `deprovision users in org removed`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            users[orgAlias]?.forEach { user ->
                var kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)

                ScimFlow
                    .deleteUser(
                        "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}",
                        "${env.flaisScimAuthUrl()}/token",
                        kcUser.id,
                    ).use { resp ->
                        assertEquals(204, resp.code)
                    }

                kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNull(kcUser)
            }
        }
    }
}
