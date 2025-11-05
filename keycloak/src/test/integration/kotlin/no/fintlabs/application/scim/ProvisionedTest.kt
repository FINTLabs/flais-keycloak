package no.fintlabs.application.scim

import no.fintlabs.config.KcConfig
import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcAdminClient
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.KcFlow.loginWithUser
import no.fintlabs.utils.ScimFlow
import no.fintlabs.utils.ScimFlow.deprovisionUsers
import no.fintlabs.utils.ScimFlow.provisionUsers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KcEnvExtension::class)
class ProvisionedTest {
    private val realm = "external"
    private val usersTelemark =
        listOf(
            ScimFlow.ScimUser(
                externalId = "CiQxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTESBWxvY2Fs",
                userName = "alice.basic@telemark.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Alice", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("alice.basic@telemark.no", primary = true)),
                groups = emptyList(),
            ),
            ScimFlow.ScimUser(
                externalId = "CiQyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjISBWxvY2Fs",
                userName = "jon.basic@telemark.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Jon", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("jon.basic@telemark.no")),
                groups = emptyList(),
            ),
        )

    val usersRogaland =
        listOf(
            ScimFlow.ScimUser(
                externalId = "CiQxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTESBWxvY2Fs",
                userName = "alice.basic@rogaland.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Alice", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("alice.basic@rogaland.no", primary = true)),
                groups = emptyList(),
            ),
            ScimFlow.ScimUser(
                externalId = "CiQyMjIyMjIyMi0yMjIyLTIyMjItMjIyMi0yMjIyMjIyMjIyMjISEWxvY2Fs",
                userName = "jon.basic@rogaland.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Jon", "Basic"),
                emails = listOf(ScimFlow.ScimUser.Email("jon.basic@rogaland.no")),
                groups = emptyList(),
            ),
        )

    @BeforeAll
    @AfterEach
    fun provisionOnce(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        provisionUsers(
            env.scimClientTelemarkUrl(),
            kcConfig.requireOrg("telemark").id,
            usersTelemark,
        ).use { resp ->
            Assertions.assertEquals(200, resp.code)
        }
        provisionUsers(
            env.scimClientRogalandUrl(),
            kcConfig.requireOrg("rogaland").id,
            usersRogaland,
        ).use { resp ->
            Assertions.assertEquals(200, resp.code)
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
                Assertions.assertEquals(200, resp.code)

                Assertions.assertNotNull(resp.request.url.queryParameter("code"))
            }
        }
    }

    @Test
    fun `patching provisioned users updates users with new info`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        val usersTelemark =
            listOf(
                ScimFlow.ScimUser(
                    externalId = "CiQxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTESBWxvY2Fs",
                    userName = "alice.basic@telemark.no",
                    active = true,
                    name = ScimFlow.ScimUser.Name("Alice 2", "Basic 2"),
                    emails = listOf(ScimFlow.ScimUser.Email("alice.basic2@telemark.no", primary = true)),
                    groups = emptyList(),
                ),
            )

        val usersRogaland =
            listOf(
                ScimFlow.ScimUser(
                    externalId = "CiQxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTESBWxvY2Fs",
                    userName = "alice.basic@rogaland.no",
                    active = true,
                    name = ScimFlow.ScimUser.Name("Alice 2", "Basic 2"),
                    emails = listOf(ScimFlow.ScimUser.Email("alice.basic2@rogaland.no", primary = true)),
                    groups = emptyList(),
                ),
            )

        provisionUsers(
            env.scimClientTelemarkUrl(),
            kcConfig.requireOrg("telemark").id,
            usersTelemark,
        ).use { resp ->
            Assertions.assertEquals(200, resp.code)
        }

        provisionUsers(
            env.scimClientRogalandUrl(),
            kcConfig.requireOrg("rogaland").id,
            usersRogaland,
        ).use { resp ->
            Assertions.assertEquals(200, resp.code)
        }

        kc.use {
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
    fun `deprovision provisioned users are removed`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        val usersTelemark =
            listOf(
                ScimFlow.ScimUser(
                    externalId = "CiQxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTESBWxvY2Fs",
                    userName = "alice.basic@telemark.no",
                    active = true,
                    name = ScimFlow.ScimUser.Name("Alice", "Basic"),
                    emails = listOf(ScimFlow.ScimUser.Email("alice.basic@telemark.no", primary = true)),
                    groups = emptyList(),
                ),
            )

        val usersRogaland =
            listOf(
                ScimFlow.ScimUser(
                    externalId = "CiQxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTESBWxvY2Fs",
                    userName = "alice.basic@rogaland.no",
                    active = true,
                    name = ScimFlow.ScimUser.Name("Alice", "Basic"),
                    emails = listOf(ScimFlow.ScimUser.Email("alice.basic@rogaland.no", primary = true)),
                    groups = emptyList(),
                ),
            )

        deprovisionUsers(
            env.scimClientTelemarkUrl(),
            kcConfig.requireOrg("telemark").id,
            usersTelemark,
        ).use { resp ->
            Assertions.assertEquals(200, resp.code)
        }

        deprovisionUsers(
            env.scimClientRogalandUrl(),
            kcConfig.requireOrg("rogaland").id,
            usersRogaland,
        ).use { resp ->
            Assertions.assertEquals(200, resp.code)
        }

        kc.use {
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
