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
import no.novari.test.common.utils.KcAdminClient
import no.novari.test.common.utils.KcUrl
import no.novari.test.common.utils.ScimFlow
import no.novari.test.common.utils.ScimFlow.ScimUser
import no.novari.test.integration.utils.KcFlow.loginWithMicrosoftScimUser
import no.novari.test.integration.utils.KcHttpClient
import no.novari.test.integration.utils.KcToken
import no.novari.test.integration.utils.KcToken.exchangeCodeForAccessToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

@ExtendWith(KcEnvironmentExtension::class)
class QlikRolesMapperTest {
    private var users: Map<String, List<ScimUser>> = emptyMap()
    private val realm = Realms.EXTERNAL
    private val clientId = Clients.QLIK
    private val scope = Scopes.PROFILE_EMAIL

    @BeforeEach
    fun provisionUsers(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        users =
            listOf(Orgs.TELEMARK, Orgs.ROGALAND).associateWith { orgAlias ->
                List(2) {
                    val roleValues =
                        listOf(
                            "https://role-${UUID.randomUUID()}",
                            "https://role-${UUID.randomUUID()}",
                            "other-role-${UUID.randomUUID()}",
                        )

                    ScimFlow.randomUser(
                        orgAlias,
                        roles =
                            roleValues.map { role ->
                                ScimUser.Role(
                                    value = role,
                                    display = role,
                                    type = "WindowsAzureActiveDirectoryRole",
                                    primary = false,
                                )
                            },
                    )
                }
            }

        users.forEach { (orgAlias, orgUsers) ->
            ScimFlow.provision(env, kcConfig, orgAlias, orgUsers)
        }
    }

    @ParameterizedTest(name = "qlik-roles-mapper for org ({0}) maps correctly")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `qlik-mapper maps attribute correctly to access token`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create(followRedirects = true)
        val redirectUri = Uris.redirectCallback(env.flaisKeycloakDemoUrl())
        val idpAlias = Idps.entra(orgAlias)
        val user = users.getValue(orgAlias).first()

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
            KcAdminClient.setClientProtocolMapperConfig(
                realmRes,
                clientId,
                "d928a7f7-0540-43b9-b10f-74b2d384ea8e",
                "passthroughCounty",
                "",
            )
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

            val qlikClaim = KcToken.decodeClaim(accessToken, "qlik")
            val qlik =
                qlikClaim as? List<*>
                    ?: throw AssertionError("qlik should be a list: $qlikClaim")

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

            val filteredRoles =
                user.roles
                    .orEmpty()
                    .mapNotNull { it.value }
                    .filter { it.startsWith("https") }

            val prefix = getCounty(orgAlias)
            val expected =
                filteredRoles.flatMap { role ->
                    listOf(role, "${prefix}_$role")
                }

            assertEquals(expected.toSet(), qlik.toSet())
        }
    }

    @ParameterizedTest(name = "qlik-roles-mapper for org ({0}) for passthrough counties maps correctly")
    @ValueSource(strings = [Orgs.TELEMARK, Orgs.ROGALAND])
    fun `qlik-roles-mapper for passthrough counties maps correctly to access token`(
        orgAlias: String,
        env: KcEnvironment,
    ) {
        val client = KcHttpClient.create(followRedirects = true)
        val redirectUri = Uris.redirectCallback(env.flaisKeycloakDemoUrl())
        val idpAlias = Idps.entra(orgAlias)
        val user = users.getValue(orgAlias).first()

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
            KcAdminClient.setClientProtocolMapperConfig(
                realmRes,
                clientId,
                "d928a7f7-0540-43b9-b10f-74b2d384ea8e",
                "passthroughCounty",
                getCounty(orgAlias),
            )
        }

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

            val qlikClaim = KcToken.decodeClaim(accessToken, "qlik")
            val qlik =
                qlikClaim as? List<*>
                    ?: throw AssertionError("qlik should be a list: $qlikClaim")

            val rolesClaim = KcToken.decodeClaim(accessToken, "roles")
            val roles =
                rolesClaim as? List<*>
                    ?: throw AssertionError("roles should be a list: $rolesClaim")

            val expected =
                user.roles
                    .orEmpty()
                    .mapNotNull { it.value }
                    .toSet()

            assertEquals(expected, roles.toSet())
            assertEquals(expected, qlik.toSet())
        }
    }

    private fun getCounty(orgAlias: String): String =
        when (orgAlias) {
            Orgs.TELEMARK -> "1"
            Orgs.ROGALAND -> "22"
            else -> throw IllegalArgumentException("Unknown orgAlias: $orgAlias")
        }
}
