package no.fintlabs.application.scim

import no.fintlabs.config.KcConfig
import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcAdminClient
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.ScimFlow
import no.fintlabs.utils.ScimFlow.provisionUsers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
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
                externalId = "test-app",
                userName = "alice@telemark.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Alice", "Anders"),
                emails = listOf(ScimFlow.ScimUser.Email("alice@telemark.no", primary = true)),
                groups = emptyList(),
            ),
            ScimFlow.ScimUser(
                externalId = "test-app",
                userName = "bob@telemark.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Bob", "Berg"),
                emails = listOf(ScimFlow.ScimUser.Email("bob@telemark.no")),
                groups = emptyList(),
            ),
        )
    val usersRogaland =
        listOf(
            ScimFlow.ScimUser(
                externalId = "test-app",
                userName = "alice@rogaland.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Alice", "Anders"),
                emails = listOf(ScimFlow.ScimUser.Email("alice@rogaland.no", primary = true)),
                groups = emptyList(),
            ),
            ScimFlow.ScimUser(
                externalId = "test-app",
                userName = "bob@rogaland.no",
                active = true,
                name = ScimFlow.ScimUser.Name("Bob", "Berg"),
                emails = listOf(ScimFlow.ScimUser.Email("bob@rogaland.no")),
                groups = emptyList(),
            ),
        )

    @BeforeAll
    fun provisionOnce(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        provisionUsers(
            "${env.scimClientTelemarkUrl()}/provision/${kcConfig.requireOrg("telemark").id}".toHttpUrl(),
            usersTelemark,
        ).use { resp ->
            Assertions.assertEquals(200, resp.code)
        }
        provisionUsers(
            "${env.scimClientRogalandUrl()}/provision/${kcConfig.requireOrg("rogaland").id}".toHttpUrl(),
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
}
