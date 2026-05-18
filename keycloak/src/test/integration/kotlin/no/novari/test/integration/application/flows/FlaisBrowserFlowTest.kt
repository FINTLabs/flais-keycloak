package no.novari.test.integration.application.flows

import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Users
import no.novari.test.integration.utils.KcFlow.loginWithUser
import no.novari.test.integration.utils.KcFlow.openAuthUrl
import no.novari.test.integration.utils.KcHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvironmentExtension::class)
class FlaisBrowserFlowTest {
    private val username = Users.ALICE_TELEMARK
    private val password = Users.PASSWORD
    private val clientId = Clients.FLAIS_KEYCLOAK_DEMO
    private val orgAlias = Orgs.TELEMARK
    private val idpAlias = Idps.ENTRA_TELEMARK

    @Test
    fun `flow returns code after login`(env: KcEnvironment) {
        loginWithUser(env, clientId, orgAlias, idpAlias, username, password).use { resp ->
            assertEquals(200, resp.code)

            assertNotNull(resp.request.url.queryParameter("code"))
        }
    }

    @Test
    fun `with SSO cookie, org and idp selector is skipped and returns code after login`(env: KcEnvironment) {
        val client = KcHttpClient.create(followRedirects = true)

        loginWithUser(env, clientId, orgAlias, idpAlias, username, password, client).use { resp ->
            assertEquals(200, resp.code)

            openAuthUrl(env = env, clientId = clientId, httpClient = client).use { resp ->
                assertEquals(200, resp.code)

                assertNotNull(resp.request.url.queryParameter("code"))
            }
        }
    }
}
