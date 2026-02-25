package no.fintlabs.test.integration.application.mapper

import no.fintlabs.test.common.config.KcConfig
import no.fintlabs.test.common.extensions.kc.KcEnvExtension
import no.fintlabs.test.common.utils.kc.KcEnvironment
import no.fintlabs.test.common.utils.kc.KcUrl
import no.fintlabs.test.integration.utils.KcFlow.loginWithUser
import no.fintlabs.test.integration.utils.KcHttpClient
import no.fintlabs.test.integration.utils.KcToken
import no.fintlabs.test.integration.utils.KcToken.exchangeCodeForAccessToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(KcEnvExtension::class)
class OrgSpecificAttributeMapperTest {
    @ParameterizedTest(name = "org-specific-attribute-mapper for org ({0}) maps correctly ")
    @ValueSource(strings = ["telemark", "rogaland"])
    fun `org-specific-attribute-mapper maps attribute correctly to access token`(
        orgAlias: String,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val client = KcHttpClient.create(followRedirects = true)
        val realm = "external"
        val clientId = "flais-keycloak-demo"
        val redirectUri = "${env.flaisKeycloakDemoUrl()}/callback"
        val scope = "openid profile email organization"
        val idpAlias = "entra-$orgAlias"
        val email = "alice.basic@$orgAlias.no"
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
            hasIdpSelector = (orgAlias == "telemark"),
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
