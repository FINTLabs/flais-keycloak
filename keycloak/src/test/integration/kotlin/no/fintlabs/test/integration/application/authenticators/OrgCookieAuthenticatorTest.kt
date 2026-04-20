package no.fintlabs.test.integration.application.authenticators

import no.fintlabs.test.common.extensions.kc.KcEnvExtension
import no.fintlabs.test.common.utils.kc.KcEnvironment
import no.fintlabs.test.integration.utils.KcContextParser
import no.fintlabs.test.integration.utils.KcFlow.loginWithUser
import no.fintlabs.test.integration.utils.KcFlow.openAuthUrl
import no.fintlabs.test.integration.utils.KcHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class OrgCookieAuthenticatorTest {
    private val email = "alice.basic@telemark.no"
    private val password = "password"
    private val clientId = "flais-keycloak-demo"
    private val orgAlias = "telemark"
    private val idpAlias = "entra-telemark"

    @Test
    fun `without identity cookie falls through to organization flow`(env: KcEnvironment) {
        openAuthUrl(env = env, clientId = clientId).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertEquals("flais-org-selector", kc.pageId)
        }
    }

    @Test
    fun `identity cookie with organization scope and allowed telemark org restores organization and returns code`(env: KcEnvironment) {
        val client = KcHttpClient.create(followRedirects = true)

        loginWithUser(env, clientId, orgAlias, idpAlias, email, password, client, scope = "organization").use { resp ->
            assertEquals(200, resp.code)

            openAuthUrl(env = env, clientId = clientId, httpClient = client, scope = "organization").use { resp ->
                assertEquals(200, resp.code)

                assertNotNull(resp.request.url.queryParameter("code"))
            }
        }
    }

    @Test
    fun `identity cookie with organization scope and client org access denied falls through to organization flow`(env: KcEnvironment) {
        val client = KcHttpClient.create(followRedirects = true)

        loginWithUser(env, clientId, orgAlias, idpAlias, email, password, client, scope = "organization").use { resp ->
            assertEquals(200, resp.code)

            openAuthUrl(
                env = env,
                clientId = "flais-keycloak-demo-not-telemark",
                httpClient = client,
                scope = "organization",
            ).use { resp ->
                assertEquals(200, resp.code)

                val html = resp.body.string()
                val kc = KcContextParser.parseKcContext(html)

                assertEquals("flais-org-selector", kc.pageId)
            }
        }
    }
}
