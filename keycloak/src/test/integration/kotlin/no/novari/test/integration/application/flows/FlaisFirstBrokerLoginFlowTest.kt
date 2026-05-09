package no.novari.test.integration.application.flows

import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Realms
import no.novari.test.common.fixture.TestStrings.Users
import no.novari.test.common.utils.KcAdminClient
import no.novari.test.integration.utils.KcFlow.loginWithUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvironmentExtension::class)
class FlaisFirstBrokerLoginFlowTest {
    private val realm = Realms.EXTERNAL
    private val username = Users.ALICE_TELEMARK
    private val firstname = Users.ALICE_FIRST_NAME
    private val lastname = Users.BASIC_LAST_NAME
    private val password = Users.PASSWORD
    private val clientId = Clients.FLAIS_KEYCLOAK_DEMO

    @BeforeEach
    fun setUp(env: KcEnvironment) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        kc.use {
            KcAdminClient
                .findUserByUsername(
                    realmRes,
                    username,
                )?.let { KcAdminClient.deleteUser(realmRes, it.id) }

            assertNull(KcAdminClient.findUserByUsername(realmRes, username))
        }
    }

    @Test
    fun `first login to org creates user with correct idp and org`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val orgAlias = Orgs.TELEMARK
        val idpAlias = Idps.ENTRA_TELEMARK
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            loginWithUser(env, clientId, orgAlias, idpAlias, username, password).use { resp ->
                assertEquals(200, resp.code)

                val user = KcAdminClient.findUserByUsername(realmRes, username)
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
    fun `login to org with existing user without idp, links idp to user`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val orgAlias = Orgs.TELEMARK
        val idpAlias = Idps.ENTRA_TELEMARK
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            val userId = KcAdminClient.createUser(realmRes, username, username, firstname, lastname)
            assertNotNull(userId)

            val links = KcAdminClient.getFederatedIdentities(realmRes, userId)
            assertFalse(links.any { it.identityProvider == idpAlias })
            assertEquals(
                0,
                links.count { it.identityProvider == idpAlias },
            )

            KcAdminClient.addUserToOrg(realmRes, userId, kcConfig.requireOrg(orgAlias).id)

            loginWithUser(env, clientId, orgAlias, idpAlias, username, password).use { resp ->
                assertEquals(200, resp.code)

                val user = KcAdminClient.findUserByUsername(realmRes, username)
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
    fun `login to org with existing user without membership, links org and idp to user`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val orgAlias = Orgs.TELEMARK
        val idpAlias = Idps.ENTRA_TELEMARK
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            val userId = KcAdminClient.createUser(realmRes, username, username, firstname, lastname)
            assertNotNull(userId)

            val member = KcAdminClient.getOrgMember(realmRes, kcConfig.requireOrg(orgAlias).id, userId)
            assertNull(member)

            loginWithUser(env, clientId, orgAlias, idpAlias, username, password).use { resp ->
                assertEquals(200, resp.code)

                val user = KcAdminClient.findUserByUsername(realmRes, username)
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
