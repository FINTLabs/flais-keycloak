package no.fintlabs.test.integration.application.authenticators

import no.fintlabs.test.common.extensions.kc.KcEnvExtension
import no.fintlabs.test.common.utils.kc.KcEnvironment
import no.fintlabs.test.integration.utils.KcContextParser
import no.fintlabs.test.integration.utils.KcFlow.continueFromAuthentikIdp
import no.fintlabs.test.integration.utils.KcFlow.continueFromIdpSelector
import no.fintlabs.test.integration.utils.KcFlow.loginWithUser
import no.fintlabs.test.integration.utils.KcFlow.openAuthUrl
import no.fintlabs.test.integration.utils.KcHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class ClientOrgAccessAuthenticatorTest {
    private val rogalandEmail = "alice.basic@rogaland.no"
    private val rogalandPassword = "password"

    @Test
    fun `tampering broker login url to different idp is denied by post login flow with client-org-access-authenticator`(
        env: KcEnvironment,
    ) {
        loginWithUser(
            env = env,
            clientId = "flais-keycloak-demo",
            orgAlias = "rogaland",
            idpAlias = "entra-rogaland",
            username = rogalandEmail,
            password = rogalandPassword,
            hasIdpSelector = false,
        ).use { resp ->
            assertEquals(200, resp.code)
        }

        val client = KcHttpClient.create(followRedirects = false)

        openAuthUrl(env = env, clientId = "flais-keycloak-demo-telemark", httpClient = client).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromIdpSelector(kc.url.loginAction!!, "entra-telemark", client).use { resp ->
                assertEquals(303, resp.code)

                val redirectUrl =
                    resp.header("Location")
                        ?: error("Missing Location header")

                val req =
                    Request
                        .Builder()
                        .url(redirectUrl.replace("entra-telemark", "entra-rogaland"))
                        .build()

                val client =
                    client
                        .newBuilder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()

                client.newCall(req).execute().use { resp ->
                    assertEquals(200, resp.code)

                    continueFromAuthentikIdp(resp.request.url, rogalandEmail, rogalandPassword, client).use { resp ->
                        assertEquals(200, resp.code)

                        val html = resp.body.string()
                        val kc = KcContextParser.parseKcContext(html)

                        assertEquals("error", kc.pageId)
                        assertEquals("error", kc.message!!.type)
                        assertEquals("Your organization does not have access to this application", kc.message.summary)
                    }
                }
            }
        }
    }
}
