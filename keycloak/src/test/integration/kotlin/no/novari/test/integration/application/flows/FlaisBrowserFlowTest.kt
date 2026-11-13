package no.novari.test.integration.application.flows

import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.utils.ScimFlow.ScimUser
import no.novari.test.integration.utils.KcFlow.loginWithMicrosoftScimUser
import no.novari.test.integration.utils.KcFlow.openAuthUrl
import no.novari.test.integration.utils.KcHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvironmentExtension::class)
class FlaisBrowserFlowTest {
    private val clientId = Clients.FLAIS_KEYCLOAK_DEMO
    private val orgAlias = Orgs.TELEMARK
    private val idpAlias = Idps.ENTRA_TELEMARK

    @Test
    fun `flow returns code after login`(
        env: KcEnvironment,
        scimUsers: List<ScimUser>,
    ) {
        val user =
            scimUsers.single { user ->
                user.emails.orEmpty().any {
                    it.value.endsWith("@$orgAlias.no")
                }
            }

        loginWithMicrosoftScimUser(env, clientId, orgAlias, idpAlias, user).use { resp ->
            assertEquals(200, resp.code)

            assertNotNull(resp.request.url.queryParameter("code"))
        }
    }

    @Test
    fun `with SSO cookie, org and idp selector is skipped and returns code after login`(
        env: KcEnvironment,
        scimUsers: List<ScimUser>,
    ) {
        val client = KcHttpClient.create(followRedirects = true)
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
        ).use { loginResponse ->
            assertEquals(200, loginResponse.code)

            openAuthUrl(env = env, clientId = clientId, httpClient = client).use { resp ->
                assertEquals(200, resp.code)

                assertNotNull(resp.request.url.queryParameter("code"))
            }
        }
    }
}
