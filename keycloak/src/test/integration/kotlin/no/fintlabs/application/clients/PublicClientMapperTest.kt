package no.fintlabs.application.clients

import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.KcFlow.loginWithUser
import no.fintlabs.utils.KcHttpClient
import no.fintlabs.utils.KcToken.exchangeCodeForAccessToken
import no.fintlabs.utils.KcUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.util.JsonSerialization
import java.util.Base64

@ExtendWith(KcEnvExtension::class)
class PublicClientMapperTest {
    @Test
    fun `map_roles_to_token maps roles correctly to access token`(env: KcComposeEnvironment) {
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

            val parts = accessToken.split(".")
            assertEquals(3, parts.size)

            val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
            val payloadJson = String(payloadBytes, Charsets.UTF_8)

            val json =
                JsonSerialization.readValue(
                    payloadJson.byteInputStream(),
                    Map::class.java,
                ) as Map<*, *>

            assertTrue(json.containsKey("roles"))

            val roles =
                json["roles"] as? List<*>
                    ?: throw AssertionError("roles should be a list: $payloadJson")

            assertEquals(listOf("read", "write", "admin"), roles)
        }
    }
}
