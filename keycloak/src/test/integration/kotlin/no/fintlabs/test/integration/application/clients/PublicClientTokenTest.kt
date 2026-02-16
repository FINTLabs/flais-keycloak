package no.fintlabs.test.integration.application.clients

import no.fintlabs.test.common.extensions.kc.KcEnvExtension
import no.fintlabs.test.common.utils.kc.KcEnvironment
import no.fintlabs.test.common.utils.kc.KcUrl
import no.fintlabs.test.integration.utils.KcFlow.loginWithUser
import no.fintlabs.test.integration.utils.KcHttpClient
import no.fintlabs.test.integration.utils.KcToken.exchangeCodeForAccessToken
import no.fintlabs.test.integration.utils.KcToken.validateToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class PublicClientTokenTest {
    @Test
    fun `code flow returns valid access token`(env: KcEnvironment) {
        val client = KcHttpClient.create(followRedirects = true)
        val realm = "external"
        val clientId = "flais-keycloak-demo"
        val redirectUri = "${env.flaisKeycloakDemoUrl()}/callback"
        val scope = "openid profile email"
        val orgAlias = "telemark"
        val idpAlias = "entra-telemark"
        val email = "alice.basic@telemark.no"
        val password = "password"
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
            email,
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
