package no.novari.test.integration.application.idp

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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvironmentExtension::class)
class EntraMapperTest {
    @Test
    fun `map_roles_to_user_attribute maps roles from claim to attribute`(env: KcEnvironment) {
        val realm = Realms.EXTERNAL
        val username = Users.ALICE_TELEMARK
        val password = Users.PASSWORD
        val clientId = Clients.FLAIS_KEYCLOAK_DEMO
        val orgAlias = Orgs.TELEMARK
        val idpAlias = Idps.ENTRA_TELEMARK

        loginWithUser(env, clientId, orgAlias, idpAlias, username, password).use { resp ->
            assertEquals(200, resp.code)

            val (kc, realmRes) = KcAdminClient.connect(env, realm)

            kc.use {
                val user = KcAdminClient.findUserByUsername(realmRes, username)
                assertEquals(listOf("read", "write", "admin"), user?.attributes["roles"])
            }
        }
    }
}
