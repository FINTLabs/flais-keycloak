package no.fintlabs.application.flows

import no.fintlabs.config.KcConfig
import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcAdminClient
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.KcFlow.loginWithUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class FlaisFirstBrokerLoginFlowTest {
    private val realm = "external"
    private val email = "alice.basic@telemark.no"
    private val username = "alice.basic@telemark.no"
    private val firstname = "Alice"
    private val lastname = "Basic"
    private val password = "password"
    private val clientId = "flais-keycloak-demo"

    @BeforeEach
    fun setUp(env: KcComposeEnvironment) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        kc.use {
            KcAdminClient
                .findUserByUsername(
                    realmRes,
                    email,
                )?.let { KcAdminClient.deleteUser(realmRes, it.id) }

            assertNull(KcAdminClient.findUserByUsername(realmRes, email))
        }
    }

    @Test
    fun `first login to org (telemark) creates user with correct idp and org`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        val orgAlias = "telemark"
        val idpAlias = "entra-telemark"
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            loginWithUser(env, clientId, orgAlias, idpAlias, email, password).use { resp ->
                assertEquals(200, resp.code)

                val user = KcAdminClient.findUserByUsername(realmRes, email)
                assertNotNull(user)

                val member =
                    KcAdminClient.getOrgMember(
                        realmRes,
                        kcConfig.requireOrg(orgAlias).id,
                        user.id,
                    )
                assertNotNull(member)

                val links = KcAdminClient.getFederatedIdentities(realmRes, user.id)
                assertTrue(links.any { it.identityProvider == idpAlias })
                assertEquals(
                    1,
                    links.count { it.identityProvider == idpAlias },
                )
            }
        }
    }

    @Test
    fun `login to org (telemark) with existing user without idp, links idp to user`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        val orgAlias = "telemark"
        val idpAlias = "entra-telemark"
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            val userId = KcAdminClient.createUser(realmRes, username, email, firstname, lastname)
            assertNotNull(userId)

            val links = KcAdminClient.getFederatedIdentities(realmRes, userId)
            assertFalse(links.any { it.identityProvider == idpAlias })
            assertEquals(
                0,
                links.count { it.identityProvider == idpAlias },
            )

            KcAdminClient.addUserToOrg(realmRes, userId, kcConfig.requireOrg(orgAlias).id)

            loginWithUser(env, clientId, orgAlias, idpAlias, email, password).use { resp ->
                assertEquals(200, resp.code)

                val user = KcAdminClient.findUserByUsername(realmRes, email)
                assertNotNull(user)

                val links = KcAdminClient.getFederatedIdentities(realmRes, user.id)
                assertTrue(links.any { it.identityProvider == idpAlias })
                assertEquals(
                    1,
                    links.count { it.identityProvider == idpAlias },
                )
            }
        }
    }

    @Test
    fun `login to org (telemark) with existing user without membership, links org and idp to user`(
        env: KcComposeEnvironment,
        kcConfig: KcConfig,
    ) {
        val orgAlias = "telemark"
        val idpAlias = "entra-telemark"
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            val userId = KcAdminClient.createUser(realmRes, username, email, firstname, lastname)
            assertNotNull(userId)

            val member = KcAdminClient.getOrgMember(realmRes, kcConfig.requireOrg(orgAlias).id, userId)
            assertNull(member)

            loginWithUser(env, clientId, orgAlias, idpAlias, email, password).use { resp ->
                assertEquals(200, resp.code)

                val user = KcAdminClient.findUserByUsername(realmRes, email)
                assertNotNull(user)

                val member =
                    KcAdminClient.getOrgMember(
                        realmRes,
                        kcConfig.requireOrg(orgAlias).id,
                        userId,
                    )
                assertNotNull(member)

                val links = KcAdminClient.getFederatedIdentities(realmRes, user.id)
                assertTrue(links.any { it.identityProvider == idpAlias })
                assertEquals(
                    1,
                    links.count { it.identityProvider == idpAlias },
                )
            }
        }
    }
}
