package no.novari.test.integration.application.authenticators

import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Scopes
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Pages
import no.novari.test.common.fixture.TestStrings.Users
import no.novari.test.integration.utils.KcContextParser
import no.novari.test.integration.utils.KcFlow.loginWithUser
import no.novari.test.integration.utils.KcFlow.openAuthUrl
import no.novari.test.integration.utils.KcHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvironmentExtension::class)
class OrgCookieAuthenticatorTest {
    private val username = Users.ALICE_TELEMARK
    private val password = Users.PASSWORD
    private val clientId = Clients.FLAIS_KEYCLOAK_DEMO
    private val orgAlias = Orgs.TELEMARK
    private val idpAlias = Idps.ENTRA_TELEMARK

    @Test
    fun `without identity cookie falls through to organization flow`(env: KcEnvironment) {
        openAuthUrl(env = env, clientId = clientId).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertEquals(Pages.FLAIS_ORG_SELECTOR, kc.pageId)
        }
    }

    @Test
    fun `identity cookie with organization scope and allowed telemark org restores organization and returns code`(env: KcEnvironment) {
        val client = KcHttpClient.create(followRedirects = true)

        loginWithUser(env, clientId, orgAlias, idpAlias, username, password, client, scope = Scopes.ORGANIZATION).use { resp ->
            assertEquals(200, resp.code)

            openAuthUrl(env = env, clientId = clientId, httpClient = client, scope = Scopes.ORGANIZATION).use { resp ->
                assertEquals(200, resp.code)

                assertNotNull(resp.request.url.queryParameter("code"))
            }
        }
    }

    @Test
    fun `identity cookie with organization scope and client org access denied falls through to organization flow`(env: KcEnvironment) {
        val client = KcHttpClient.create(followRedirects = true)

        loginWithUser(env, clientId, orgAlias, idpAlias, username, password, client, scope = Scopes.ORGANIZATION).use { resp ->
            assertEquals(200, resp.code)

            openAuthUrl(
                env = env,
                clientId = Clients.FLAIS_KEYCLOAK_DEMO_NOT_TELEMARK,
                httpClient = client,
                scope = Scopes.ORGANIZATION,
            ).use { resp ->
                assertEquals(200, resp.code)

                val html = resp.body.string()
                val kc = KcContextParser.parseKcContext(html)

                assertEquals(Pages.FLAIS_ORG_SELECTOR, kc.pageId)
            }
        }
    }
}
