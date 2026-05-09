package no.novari.test.integration.application.clients

import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Realms
import no.novari.test.common.fixture.TestStrings.Scopes
import no.novari.test.common.fixture.TestStrings.Users
import no.novari.test.common.utils.KcUrl
import no.novari.test.integration.utils.KcFlow.loginWithUser
import no.novari.test.integration.utils.KcHttpClient
import no.novari.test.integration.utils.KcToken.exchangeCodeForAccessToken
import no.novari.test.integration.utils.KcToken.validateToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvironmentExtension::class)
class PublicClientTokenTest {
    @Test
    fun `code flow returns valid access token`(env: KcEnvironment) {
        val client = KcHttpClient.create(followRedirects = true)
        val realm = Realms.EXTERNAL
        val clientId = Clients.FLAIS_KEYCLOAK_DEMO
        val redirectUri = "${env.flaisKeycloakDemoUrl()}/callback"
        val scope = Scopes.PROFILE_EMAIL
        val orgAlias = Orgs.TELEMARK
        val idpAlias = Idps.ENTRA_TELEMARK
        val username = Users.ALICE_TELEMARK
        val password = Users.PASSWORD
        val (authUrl, codeVerifier) =
            KcUrl.authUrl(
                env = env,
                clientId = clientId,
                redirectUri = redirectUri,
                scope = scope,
            )
        requireNotNull(codeVerifier)

        loginWithUser(
            env,
            clientId,
            orgAlias,
            idpAlias,
            username,
            password,
            client,
            authUrl,
        ).use { resp ->
            assertEquals(200, resp.code)

            val code = resp.request.url.queryParameter("code")
            assertNotNull(code)

            val accessToken =
                exchangeCodeForAccessToken(
                    env,
                    realm,
                    code,
                    redirectUri,
                    clientId,
                    codeVerifier,
                    client,
                )
            assertNotNull(accessToken)

            validateToken(env, realm, accessToken, client).use { resp ->
                assertEquals(200, resp.code)

                val body = resp.body.string()
                assertTrue(body.contains("\"sub\""))
            }
        }
    }
}
