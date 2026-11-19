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
import no.novari.test.integration.utils.KcToken
import no.novari.test.integration.utils.KcToken.exchangeCodeForAccessToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.collections.any
import kotlin.collections.orEmpty

@ExtendWith(KcEnvironmentExtension::class)
class PublicClientMapperTest {
    @ParameterizedTest(name = "map_roles_to_token maps roles correctly for org ({0})")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `map_roles_to_token maps roles correctly to access token`(
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

            val rolesClaim = KcToken.decodeClaim(accessToken, "roles")
            val roles =
                rolesClaim as? List<*>
                    ?: throw AssertionError("roles should be a list: $rolesClaim")

            assertEquals(
                user.roles
                    .orEmpty()
                    .mapNotNull { it.value }
                    .toSet(),
                roles.toSet(),
            )
        }
    }
}
