package no.novari.test.integration.application.idp

import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Realms
import no.novari.test.common.utils.KcAdminClient
import no.novari.test.common.utils.ScimFlow.ScimUser
import no.novari.test.integration.utils.KcFlow.loginWithMicrosoftScimUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvironmentExtension::class)
class EntraMapperTest {
    @Test
    fun `map_roles_to_user_attribute maps roles from claim to attribute`(
        env: KcEnvironment,
        scimUsers: List<ScimUser>,
    ) {
        val realm = Realms.EXTERNAL
        val clientId = Clients.FLAIS_KEYCLOAK_DEMO
        val orgAlias = Orgs.TELEMARK
        val idpAlias = Idps.ENTRA_TELEMARK
        val scimUser =
            scimUsers.single { user ->
                user.emails.orEmpty().any {
                    it.value.endsWith("@$orgAlias.no")
                }
            }

        loginWithMicrosoftScimUser(env, clientId, orgAlias, idpAlias, scimUser).use { resp ->
            assertEquals(200, resp.code)

            val (kc, realmRes) = KcAdminClient.connect(env, realm)

            kc.use {
                val user = KcAdminClient.findUserByUsername(realmRes, scimUser.userName)
                assertNotNull(user)

                val roles = user.attributes["roles"]
                assertNotNull(roles)

                assertEquals(
                    scimUser.roles
                        .orEmpty()
                        .mapNotNull { it.value }
                        .toSet(),
                    roles.toSet(),
                )
            }
        }
    }
}
