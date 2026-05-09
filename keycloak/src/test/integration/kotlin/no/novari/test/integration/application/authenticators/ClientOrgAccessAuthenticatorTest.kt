package no.novari.test.integration.application.authenticators

import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Pages
import no.novari.test.common.fixture.TestStrings.Users
import no.novari.test.integration.utils.KcContextParser
import no.novari.test.integration.utils.KcFlow.continueFromAuthentikIdp
import no.novari.test.integration.utils.KcFlow.continueFromIdpSelector
import no.novari.test.integration.utils.KcFlow.loginWithUser
import no.novari.test.integration.utils.KcFlow.openAuthUrl
import no.novari.test.integration.utils.KcHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvironmentExtension::class)
class ClientOrgAccessAuthenticatorTest {
    @Test
    fun `tampering broker login url to different idp is denied by post login flow with client-org-access-authenticator`(
        env: KcEnvironment,
    ) {
        loginWithUser(
            env = env,
            clientId = Clients.FLAIS_KEYCLOAK_DEMO,
            orgAlias = Orgs.ROGALAND,
            idpAlias = Orgs.ROGALAND,
            username = Users.ALICE_ROGALAND,
            password = Users.PASSWORD,
            hasIdpSelector = false,
        ).use { resp ->
            assertEquals(200, resp.code)
        }

        val client = KcHttpClient.create(followRedirects = false)

        openAuthUrl(env = env, clientId = Clients.FLAIS_KEYCLOAK_DEMO_TELEMARK, httpClient = client).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromIdpSelector(kc.url.loginAction!!, Idps.ENTRA_TELEMARK, client).use { resp ->
                assertEquals(303, resp.code)

                val redirectUrl =
                    resp.header("Location")
                        ?: error("Missing Location header")

                val req =
                    Request
                        .Builder()
                        .url(redirectUrl.replace(Idps.ENTRA_TELEMARK, Idps.ENTRA_ROGALAND))
                        .build()

                val client =
                    client
                        .newBuilder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()

                client.newCall(req).execute().use { resp ->
                    assertEquals(200, resp.code)

                    continueFromAuthentikIdp(resp.request.url, Users.ALICE_ROGALAND, Users.PASSWORD, client).use { resp ->
                        assertEquals(200, resp.code)

                        val html = resp.body.string()
                        val kc = KcContextParser.parseKcContext(html)

                        assertEquals(Pages.ERROR, kc.pageId)
                        assertEquals("error", kc.message!!.type)
                        assertEquals("Your organization does not have access to this application", kc.message.summary)
                    }
                }
            }
        }
    }
}
