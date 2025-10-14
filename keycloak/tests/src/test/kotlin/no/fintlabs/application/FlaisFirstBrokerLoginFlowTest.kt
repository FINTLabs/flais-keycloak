package no.fintlabs.application

import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcAdminClient
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.KcFlowUtils.loginWithUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class FlaisFirstBrokerLoginFlowTest {
    private val realm = "external"
    private val email = "alice.basic@telemark.no"
    private val password = "password"
    private val clientId = "flais-keycloak-demo"
    private val orgAlias = "telemark"
    private val idpAlias = "entra-telemark"

    @Test
    fun `first login to org (telemark) creates user with correct idp`(env: KcComposeEnvironment) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        kc.use {
            KcAdminClient.findUserByEmail(realmRes, email)?.let { KcAdminClient.deleteUser(realmRes, it.id) }

            loginWithUser(env, clientId, orgAlias, idpAlias, email, password).use { resp ->
                assertEquals(200, resp.code)

                val user = KcAdminClient.findUserByEmail(realmRes, email)
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
}
