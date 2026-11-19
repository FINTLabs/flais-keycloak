package no.novari.test.integration.application.mapper

import no.novari.test.common.config.KcConfig
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

@ExtendWith(KcEnvironmentExtension::class)
class OrgSpecificAttributeMapperTest {
    @ParameterizedTest(name = "org-specific-attribute-mapper for org ({0}) maps correctly")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `org-specific-attribute-mapper maps attribute correctly to access token`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
        scimUsers: List<ScimUser>,
    ) {
        val client = KcHttpClient.create(followRedirects = true)
        val realm = Realms.EXTERNAL
        val clientId = Clients.FLAIS_KEYCLOAK_DEMO
        val redirectUri = Uris.redirectCallback(env.flaisKeycloakDemoUrl())
        val scope = Scopes.PROFILE_EMAIL_ORGANIZATION
        val idpAlias = Idps.entra(orgAlias)
        val user =
            scimUsers.single { user ->
                user.emails.orEmpty().any {
                    it.value.endsWith("@$orgAlias.no")
                }
            }

        val (authUrl, codeVerifier) =
            KcUrl.authUrl(
                env = env,
                clientId = clientId,
                redirectUri = redirectUri,
                scope = scope,
            )
        requireNotNull(codeVerifier)

        loginWithMicrosoftScimUser(
            env,
            clientId,
            orgAlias,
            idpAlias,
            user,
            httpClient = client,
            httpUrl = authUrl,
            hasIdpSelector = (orgAlias == Orgs.TELEMARK),
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

            val organizationNumber = KcToken.decodeClaim(accessToken, "organizationnumber")
            assertNotNull(organizationNumber)

            organizationNumber as? String
                ?: throw AssertionError("organizationnumber should be a string")

            val attribute = kcConfig.requireOrg(orgAlias).attributes["ORGANIZATION_NUMBER"]?.first()

            assertEquals(attribute, organizationNumber)
        }
    }
}
