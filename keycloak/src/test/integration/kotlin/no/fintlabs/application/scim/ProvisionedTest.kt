package no.fintlabs.application.scim

import no.fintlabs.config.KcConfig
import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcAdminClient
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.KcFlow.loginWithUser
import no.fintlabs.utils.ScimFlow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KcEnvExtension::class)
class ProvisionedTest {
    private val realm = "external"
    private val usersTelemark =
        listOf(
            ScimFlow.ScimUser(
                externalId = "11111111-1111-1111-1111-111111111111",
                userName = "alice.basic@telemark.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Alice", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("alice.basic@telemark.no", primary = true)),
            ),
            ScimFlow.ScimUser(
                externalId = "22222222-2222-2222-2222-222222222222",
                userName = "jon.basic@telemark.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Jon", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("jon.basic@telemark.no", primary = true)),
            ),
        )

    val usersRogaland =
        listOf(
            ScimFlow.ScimUser(
                externalId = "11111111-1111-1111-1111-111111111111",
                userName = "alice.basic@rogaland.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Alice", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("alice.basic@rogaland.no", primary = true)),
            ),
            ScimFlow.ScimUser(
                externalId = "22222222-2222-2222-2222-222222222222",
                userName = "jon.basic@rogaland.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Jon", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("jon.basic@rogaland.no", primary = true)),
            ),
        )

    @BeforeAll
    @AfterEach
    fun provisionOnce(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            KcAdminClient.deleteAllUsers(realmRes)
        }

        usersTelemark.forEach { user ->
            ScimFlow
                .createUser(
                    "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg("telemark").id}",
                    "${env.flaisScimAuthUrl()}/token",
                    user,
                ).use { resp ->
                    assertEquals(201, resp.code)
                }
        }
        usersRogaland.forEach { user ->
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

    @Test
    fun `provisioned users in org (telemark) exists`(env: KcComposeEnvironment) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            usersTelemark.forEach { u ->
                val user = KcAdminClient.findUserByEmail(realmRes, u.userName)
                assertNotNull(user)
            }
        }
    }

    @Test
    fun `provisioned users in org (rogaland) exists`(env: KcComposeEnvironment) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            usersTelemark.forEach { u ->
                val user = KcAdminClient.findUserByEmail(realmRes, u.userName)
                assertNotNull(user)
            }
        }
    }

    @Test
    fun `provisioned users in org (rogaland) linked to correct idp`(env: KcComposeEnvironment) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            usersRogaland.forEach { u ->
                val user = KcAdminClient.findUserByEmail(realmRes, u.userName)
                assertNotNull(user)
                val identities = KcAdminClient.getFederatedIdentities(realmRes, user.id)
                assertNotNull(identities)
                assertEquals("entra-rogaland", identities.first().identityProvider)
            }
        }
    }

    @Test
    fun `provisioned users in org (telemark) linked to correct idp`(env: KcComposeEnvironment) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            usersTelemark.forEach { u ->
                val user = KcAdminClient.findUserByEmail(realmRes, u.userName)
                assertNotNull(user)
                val identities = KcAdminClient.getFederatedIdentities(realmRes, user.id)
                assertNotNull(identities)
                assertEquals("entra-telemark", identities.first().identityProvider)
            }
        }
    }

    @Test
    fun `provisioned users in org (telemark) can login`(env: KcComposeEnvironment) {
        val clientId = "flais-keycloak-demo"
        val orgAlias = "telemark"
        val idpAlias = "entra-telemark"

        usersTelemark.forEach { u ->
            loginWithUser(env, clientId, orgAlias, idpAlias, u.userName, "password").use { resp ->
                assertEquals(200, resp.code)

                assertNotNull(resp.request.url.queryParameter("code"))
            }
        }
    }

    @Test
    fun `updating provisioned users updates users with new info`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            val usersTelemark =
                listOf(
                    ScimFlow.ScimUser(
                        id = KcAdminClient.findUserByUsername(realmRes, "alice.basic@telemark.no")?.id,
                        externalId = "11111111-1111-1111-1111-111111111111",
                        userName = "alice.basic@telemark.no",
                        active = true,
                        name = ScimFlow.ScimUser.Name("Alice 2", "Basic 2"),
                        emails =
                            listOf(
                                ScimFlow.ScimUser.Email(
                                    "alice.basic2@telemark.no",
                                    primary = true,
                                ),
                            ),
                    ),
                )

            val usersRogaland =
                listOf(
                    ScimFlow.ScimUser(
                        id = KcAdminClient.findUserByUsername(realmRes, "alice.basic@rogaland.no")?.id,
                        externalId = "11111111-1111-1111-1111-111111111111",
                        userName = "alice.basic@rogaland.no",
                        active = true,
                        name = ScimFlow.ScimUser.Name("Alice 2", "Basic 2"),
                        emails =
                            listOf(
                                ScimFlow.ScimUser.Email(
                                    "alice.basic2@rogaland.no",
                                    primary = true,
                                ),
                            ),
                    ),
                )

            usersTelemark.forEach { user ->
                ScimFlow
                    .updateUser(
                        "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg("telemark").id}",
                        "${env.flaisScimAuthUrl()}/token",
                        user.id!!,
                        user,
                    ).use { resp ->
                        assertEquals(200, resp.code)
                    }
            }

            usersRogaland.forEach { user ->
                ScimFlow
                    .updateUser(
                        "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg("rogaland").id}",
                        "${env.flaisScimAuthUrl()}/token",
                        user.id!!,
                        user,
                    ).use { resp ->
                        assertEquals(200, resp.code)
                    }
            }

            usersTelemark.forEach { u ->
                val user = KcAdminClient.findUserByUsername(realmRes, u.userName)
                assertNotNull(user)
                assertEquals(u.name.givenName, user.firstName)
                assertEquals(u.name.familyName, user.lastName)
                assertEquals(u.emails.first().value, user.email)
            }

            usersRogaland.forEach { u ->
                val user = KcAdminClient.findUserByUsername(realmRes, u.userName)
                assertNotNull(user)
                assertEquals(u.name.givenName, user.firstName)
                assertEquals(u.name.familyName, user.lastName)
                assertEquals(u.emails.first().value, user.email)
            }
        }
    }

    @Test
    fun `deprovision users are removed`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            usersTelemark.forEach { user ->
                val user = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(user)

                ScimFlow
                    .deleteUser(
                        "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg("telemark").id}",
                        "${env.flaisScimAuthUrl()}/token",
                        user.id,
                    ).use { resp ->
                        assertEquals(204, resp.code)
                    }
            }

            usersRogaland.forEach { user ->
                val user = KcAdminClient.findUserByUsername(realmRes, user.userName)
                assertNotNull(user)

                ScimFlow
                    .deleteUser(
                        "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg("rogaland").id}",
                        "${env.flaisScimAuthUrl()}/token",
                        user.id,
                    ).use { resp ->
                        assertEquals(204, resp.code)
                    }
            }

            usersTelemark.forEach { u ->
                val user = KcAdminClient.findUserByUsername(realmRes, u.userName)
                assertNull(user)
            }

            usersRogaland.forEach { u ->
                val user = KcAdminClient.findUserByUsername(realmRes, u.userName)
                assertNull(user)
            }
        }
    }
}
