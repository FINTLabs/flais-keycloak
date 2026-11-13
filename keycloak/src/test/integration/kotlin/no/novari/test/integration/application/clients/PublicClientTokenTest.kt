package no.novari.test.integration.application.clients

import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Realms
import no.novari.test.common.fixture.TestStrings.Scopes
import no.novari.test.common.fixture.TestStrings.Uris
import no.novari.test.common.utils.KcUrl
import no.novari.test.common.utils.ScimFlow.ScimUser
import no.novari.test.integration.utils.KcFlow.loginWithMicrosoftScimUser
import no.novari.test.integration.utils.KcHttpClient
import no.novari.test.integration.utils.KcToken.exchangeCodeForAccessToken
import no.novari.test.integration.utils.KcToken.validateToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(KcEnvironmentExtension::class)
class PublicClientTokenTest {
    @ParameterizedTest(name = "code flow returns valid access token for org ({0})")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `code flow returns valid access token`(
        orgAlias: String,
        env: KcEnvironment,
        scimUsers: List<ScimUser>,
    ) {
        val client = KcHttpClient.create(followRedirects = true)
        val realm = Realms.EXTERNAL
        val clientId = Clients.FLAIS_KEYCLOAK_DEMO
        val redirectUri = Uris.redirectCallback(env.flaisKeycloakDemoUrl())
        val scope = Scopes.PROFILE_EMAIL
        val idpAlias =
            when (orgAlias) {
                Orgs.TELEMARK -> Idps.ENTRA_TELEMARK
                Orgs.ROGALAND -> Idps.ENTRA_ROGALAND
                else -> error("Unsupported organization: $orgAlias")
            }

        val (authUrl, codeVerifier) =
            KcUrl.authUrl(
                env = env,
                clientId = clientId,
                redirectUri = redirectUri,
                scope = scope,
            )
        requireNotNull(codeVerifier)

        val user =
            scimUsers.single { user ->
                user.emails.orEmpty().any {
                    it.value.endsWith("@$orgAlias.no")
                }
            }

        loginWithMicrosoftScimUser(
            env,
            clientId,
            orgAlias,
            idpAlias,
            user,
            httpClient = client,
            httpUrl = authUrl,
            hasIdpSelector = orgAlias == Orgs.TELEMARK,
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

            validateToken(env, realm, accessToken, client).use { validationResponse ->
                assertEquals(200, validationResponse.code)

                val body = validationResponse.body.string()
                assertTrue(body.contains("\"sub\""))
            }
        }
    }
}
