package no.fintlabs.test.integration.application.mapper

import no.fintlabs.test.common.extensions.kc.KcEnvExtension
import no.fintlabs.test.common.utils.kc.KcAdminClient
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
class QlikRolesMapperTest {
    val realm = "external"
    val clientId = "qlik"
    val scope = "openid profile email"
    val password = "password"

    @ParameterizedTest(name = "qlik-roles-mapper for org ({0}) maps correctly ")
    @ValueSource(strings = ["telemark", "rogaland"])
    fun `qlik-mapper maps attribute correctly to access token`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create(followRedirects = true)
        val redirectUri = "${env.flaisKeycloakDemoUrl()}/callback"
        val idpAlias = "entra-$orgAlias"
        val email = "qlik.basic@$orgAlias.no"
        val (authUrl, codeVerifier) =
            KcUrl.authUrl(
                env = env,
                clientId = clientId,
                redirectUri = redirectUri,
                scope = scope,
            )
        requireNotNull(codeVerifier)

        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        kc.use {
            KcAdminClient
                .setClientProtocolMapperConfig(
                    realmRes,
                    clientId,
                    "d928a7f7-0540-43b9-b10f-74b2d384ea8e",
                    "passthroughCounty",
                    "",
                )
        }

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

            val qlik = KcToken.decodeClaim(accessToken, "qlik")
            assertNotNull(qlik)

            qlik as? List<*>
                ?: throw AssertionError("qlik should be a list")

            val roles = KcToken.decodeClaim(accessToken, "roles") as List<*>
            assertNotNull(roles)

            val filteredRoles =
                roles
                    .filterIsInstance<String>()
                    .filter { it.startsWith("https") }

            val prefix = getCounty(orgAlias)

            val expected =
                filteredRoles.flatMap { role ->
                    listOf(role, "${prefix}_$role")
                }

            assertEquals(expected.toSet(), qlik.toSet())
        }
    }

    @ParameterizedTest(name = "qlik-roles-mapper for org ({0}) for passthrough counties maps correctly`")
    @ValueSource(strings = ["telemark", "rogaland"])
    fun `qlik-roles-mapper for passthrough counties maps correctly to access token`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create(followRedirects = true)
        val redirectUri = "${env.flaisKeycloakDemoUrl()}/callback"
        val idpAlias = "entra-$orgAlias"
        val email = "qlik.basic@$orgAlias.no"
        val (authUrl, codeVerifier) =
            KcUrl.authUrl(
                env = env,
                clientId = clientId,
                redirectUri = redirectUri,
                scope = scope,
            )
        requireNotNull(codeVerifier)

        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        kc.use {
            KcAdminClient
                .setClientProtocolMapperConfig(
                    realmRes,
                    clientId,
                    "d928a7f7-0540-43b9-b10f-74b2d384ea8e",
                    "passthroughCounty",
                    getCounty(orgAlias),
                )
        }

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

            val qlik = KcToken.decodeClaim(accessToken, "qlik")
            assertNotNull(qlik)

            qlik as? List<*>
                ?: throw AssertionError("qlik should be a list")

            val roles = KcToken.decodeClaim(accessToken, "roles") as List<*>
            assertNotNull(roles)

            assertEquals(roles.toSet(), qlik.toSet())
        }
    }

    private fun getCounty(orgAlias: String): String {
        return when (orgAlias) {
            "telemark" -> "1"
            "rogaland" -> "22"
            else -> throw IllegalArgumentException("Unknown orgAlias: $orgAlias")
        }
    }
}
