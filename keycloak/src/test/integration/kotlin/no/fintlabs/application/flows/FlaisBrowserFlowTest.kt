package no.fintlabs.application.flows

import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.KcFlow.loginWithUser
import no.fintlabs.utils.KcFlow.openAuthUrl
import no.fintlabs.utils.KcHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class FlaisBrowserFlowTest {
    private val email = "alice.basic@telemark.no"
    private val password = "password"
    private val clientId = "flais-keycloak-demo"
    private val orgAlias = "telemark"
    private val idpAlias = "entra-telemark"

    @Test
    fun `flow returns code after login`(env: KcComposeEnvironment) {
        loginWithUser(env, clientId, orgAlias, idpAlias, email, password).use { resp ->
            assertEquals(200, resp.code)

            assertNotNull(resp.request.url.queryParameter("code"))
        }
    }

    @Test
    fun `with SSO cookie, org and idp selector is skipped and returns code after login`(env: KcComposeEnvironment) {
        val client = KcHttpClient.create(followRedirects = true)

        loginWithUser(env, clientId, orgAlias, idpAlias, email, password, client).use { resp ->
            assertEquals(200, resp.code)

            openAuthUrl(env = env, clientId = clientId, httpClient = client).use { resp ->
                assertEquals(200, resp.code)

                assertNotNull(resp.request.url.queryParameter("code"))
            }
        }
    }
}
