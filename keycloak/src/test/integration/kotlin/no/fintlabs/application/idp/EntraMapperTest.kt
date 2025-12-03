package no.fintlabs.application.idp

import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcAdminClient
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.KcFlow.loginWithUser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class EntraMapperTest {
    @Test
    fun `map_roles_to_user_attribute maps roles from claim to attribute`(env: KcComposeEnvironment) {
        val realm = "external"
        val email = "alice.basic@telemark.no"
        val password = "password"
        val clientId = "flais-keycloak-demo"
        val orgAlias = "telemark"
        val idpAlias = "entra-telemark"

        loginWithUser(env, clientId, orgAlias, idpAlias, email, password).use { resp ->
            Assertions.assertEquals(200, resp.code)

            val (kc, realmRes) = KcAdminClient.connect(env, realm)

            kc.use {
                val user = KcAdminClient.findUserByUsername(realmRes, email)
                assertEquals(listOf("read", "write", "admin"), user?.attributes["roles"])
            }
        }
    }
}
