package no.novari.test.integration.application.scim

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Realms
import no.novari.test.common.utils.KcAdminClient
import no.novari.test.common.utils.ScimFlow
import no.novari.test.common.utils.ScimFlow.ScimUser
import no.novari.test.integration.utils.KcFlow.loginWithMicrosoftScimUser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KcEnvironmentExtension::class)
class ProvisionedTest {
    private val realm = Realms.EXTERNAL
    private var users: Map<String, List<ScimUser>> = emptyMap()

    private fun baseUrl(
        env: KcEnvironment,
        kcConfig: KcConfig,
        orgAlias: String,
    ): String = "${env.keycloakServiceUrl()}/realms/$realm/scim/v2/${kcConfig.requireOrg(orgAlias).id}"

    private fun tokenUrl(env: KcEnvironment): String = "${env.flaisScimAuthUrl()}/token"

    @BeforeEach
    fun provisionUsers(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        users =
            listOf(Orgs.TELEMARK, Orgs.ROGALAND).associateWith { orgAlias ->
                List(2) { ScimFlow.randomUser(orgAlias) }
            }

        users.forEach { (orgAlias, orgUsers) ->
            ScimFlow.provision(env, kcConfig, orgAlias, orgUsers)
        }
    }

    @AfterEach
    fun cleanUpUsers(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            users.forEach { (orgAlias, orgUsers) ->
                orgUsers.forEach { user ->
                    val kcUser =
                        KcAdminClient.findUserByUsername(realmRes, user.userName)

                    if (kcUser != null) {
                        ScimFlow
                            .deleteUser(
                                baseUrl(env, kcConfig, orgAlias),
                                tokenUrl(env),
                                kcUser.id,
                            ).use { resp ->
                                assertEquals(204, resp.code)
                            }
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "provisioned users in org ({0}) exists")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `provisioned users in org exists`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            users.getValue(orgAlias).forEach { user ->
                val kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)
            }
        }
    }

    @ParameterizedTest(name = "provisioned users in org ({0}) linked to correct idp")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `provisioned users in org linked to correct idp`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            users.getValue(orgAlias).forEach { user ->
                val kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)

                val identities = KcAdminClient.getFederatedIdentities(realmRes, kcUser.id)
                assertNotNull(identities)

                val identity =
                    identities.single { it.identityProvider == Idps.entra(orgAlias) }

                assertEquals(user.externalId, identity.userId)
            }
        }
    }

    @ParameterizedTest(name = "provisioned users in org ({0}) can login")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `provisioned users in org can login`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val clientId = Clients.FLAIS_KEYCLOAK_DEMO
        val idpAlias = Idps.entra(orgAlias)

        users.getValue(orgAlias).forEach { user ->
            loginWithMicrosoftScimUser(
                env,
                clientId,
                orgAlias,
                idpAlias,
                user,
                hasIdpSelector = (orgAlias == Orgs.TELEMARK),
            ).use { resp ->
                assertEquals(200, resp.code)
                assertNotNull(resp.request.url.queryParameter("code"))
            }
        }
    }

    @ParameterizedTest(name = "updating provisioned users in org ({0}) updates users with new info")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `updating provisioned users in org updates users with new info`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            users.getValue(orgAlias).forEach { templateUser ->
                val existingUser =
                    KcAdminClient.findUserByUsername(realmRes, templateUser.userName)
                assertNotNull(existingUser)

                val extension = requireNotNull(templateUser.fintUserExtension)
                val email =
                    templateUser.emails
                        .orEmpty()
                        .single()
                        .value

                val user =
                    templateUser.copy(
                        id = existingUser.id,
                        emails =
                            listOf(
                                ScimUser.Email("new-$email", primary = true),
                            ),
                        fintUserExtension =
                            extension.copy(
                                givenName = "newgiven",
                                familyName = "newfamily",
                            ),
                    )

                ScimFlow
                    .updateUser(
                        baseUrl(env, kcConfig, orgAlias),
                        tokenUrl(env),
                        existingUser.id,
                        user,
                    ).use { resp ->
                        assertEquals(200, resp.code)
                    }

                val kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)

                assertEquals(user.fintUserExtension?.givenName, kcUser.firstName)
                assertEquals(user.fintUserExtension?.familyName, kcUser.lastName)
                assertEquals(user.emails?.single()?.value, kcUser.email)
            }
        }
    }

    @ParameterizedTest(name = "patching provisioned users in org ({0}) patches users with new info")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `patching provisioned users in org patches users with new info`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        val newRoles =
            JsonArray(
                listOf("manager", "admin").map { role ->
                    JsonObject(
                        mapOf(
                            "primary" to JsonPrimitive(false),
                            "type" to JsonPrimitive("WindowsAzureActiveDirectoryRole"),
                            "display" to JsonPrimitive(role),
                            "value" to JsonPrimitive(role),
                        ),
                    )
                },
            )

        kc.use {
            users.getValue(orgAlias).forEach { user ->
                val existingUser =
                    KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(existingUser)

                ScimFlow
                    .patchUser(
                        baseUrl(env, kcConfig, orgAlias),
                        tokenUrl(env),
                        existingUser.id,
                        ScimFlow.PatchRequest(
                            listOf(
                                ScimFlow.PatchRequest.PatchOperation(
                                    op = "replace",
                                    path = "roles",
                                    value = newRoles,
                                ),
                            ),
                        ),
                    ).use { resp ->
                        assertEquals(200, resp.code)
                    }

                val kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)

                val roles = kcUser.attributes["roles"]
                assertNotNull(roles)

                assertEquals(
                    newRoles
                        .map { role ->
                            role.jsonObject
                                .getValue("value")
                                .jsonPrimitive.content
                        }.toSet(),
                    roles.toSet(),
                )
            }
        }
    }

    @ParameterizedTest(name = "patching provisioned users in org ({0}) using full urn definition patches users with new info")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `patching provisioned users in org using full urn definition patches users with new info`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        val coreUrn = "urn:ietf:params:scim:schemas:core:2.0:User"
        val extensionUrnPrefix = "urn:ietf:params:scim:schemas:extension:fint:2.0:User"

        val extensionAttributes =
            JsonObject(
                mapOf(
                    "employeeId" to JsonPrimitive("111111111111"),
                    "studentNumber" to JsonPrimitive("111111111111"),
                    "userPrincipalName" to JsonPrimitive("test@test.no"),
                ),
            )

        val standardFintUserExtensionAttributes =
            JsonObject(
                mapOf(extensionUrnPrefix to extensionAttributes),
            )

        val entraFintUserExtensionAttributes =
            JsonObject(
                mapOf(
                    "$extensionUrnPrefix:givenName" to JsonPrimitive("Jeanette"),
                    "$extensionUrnPrefix:familyName" to JsonPrimitive("Bergersen"),
                    "$extensionUrnPrefix:employeeId" to JsonPrimitive("111111111111"),
                    "$extensionUrnPrefix:studentNumber" to JsonPrimitive("111111111111"),
                    "$extensionUrnPrefix:userPrincipalName" to JsonPrimitive("test@test.no"),
                ),
            )

        kc.use {
            users.getValue(orgAlias).forEach { user ->
                val existingUser =
                    KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(existingUser)

                ScimFlow
                    .patchUser(
                        baseUrl(env, kcConfig, orgAlias),
                        tokenUrl(env),
                        existingUser.id,
                        ScimFlow.PatchRequest(
                            listOf(
                                ScimFlow.PatchRequest.PatchOperation(
                                    op = "replace",
                                    value = standardFintUserExtensionAttributes,
                                ),
                            ),
                        ),
                    ).use { resp ->
                        assertEquals(200, resp.code)
                    }

                val afterStandardPatch =
                    KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(afterStandardPatch)

                extensionAttributes.forEach { (attributeName, expected) ->
                    assertEquals(
                        expected.jsonPrimitive.content,
                        afterStandardPatch.attributes[attributeName]?.first(),
                    )
                }

                ScimFlow
                    .patchUser(
                        baseUrl(env, kcConfig, orgAlias),
                        tokenUrl(env),
                        existingUser.id,
                        ScimFlow.PatchRequest(
                            listOf(
                                ScimFlow.PatchRequest.PatchOperation(
                                    op = "replace",
                                    value = entraFintUserExtensionAttributes,
                                ),
                            ),
                        ),
                    ).use { resp ->
                        assertEquals(200, resp.code)
                    }

                val afterEntraPatch =
                    KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(afterEntraPatch)

                entraFintUserExtensionAttributes.forEach { (key, expected) ->
                    val attributeName = key.substringAfterLast(":")
                    val actual =
                        when (attributeName) {
                            "givenName" -> afterEntraPatch.firstName
                            "familyName" -> afterEntraPatch.lastName
                            else -> afterEntraPatch.attributes[attributeName]?.first()
                        }

                    assertEquals(expected.jsonPrimitive.content, actual)
                }

                val entraCoreUserAttributes =
                    JsonObject(
                        mapOf(
                            "$coreUrn:externalId" to JsonPrimitive(UUID.randomUUID().toString()),
                        ),
                    )

                ScimFlow
                    .patchUser(
                        baseUrl(env, kcConfig, orgAlias),
                        tokenUrl(env),
                        existingUser.id,
                        ScimFlow.PatchRequest(
                            listOf(
                                ScimFlow.PatchRequest.PatchOperation(
                                    op = "replace",
                                    value = entraCoreUserAttributes,
                                ),
                            ),
                        ),
                    ).use { resp ->
                        assertEquals(200, resp.code)
                    }

                val afterCorePatch =
                    KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(afterCorePatch)

                assertEquals("Jeanette", afterCorePatch.firstName)
                assertEquals("Bergersen", afterCorePatch.lastName)
            }
        }
    }

    @ParameterizedTest(name = "deprovision users in org ({0}) removed")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `deprovision users in org removed`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            users.getValue(orgAlias).forEach { user ->
                val kcUser = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(kcUser)

                ScimFlow
                    .deleteUser(
                        baseUrl(env, kcConfig, orgAlias),
                        tokenUrl(env),
                        kcUser.id,
                    ).use { resp ->
                        assertEquals(204, resp.code)
                    }

                assertNull(KcAdminClient.findUserByUsername(realmRes, user.userName))
            }
        }
    }
}
