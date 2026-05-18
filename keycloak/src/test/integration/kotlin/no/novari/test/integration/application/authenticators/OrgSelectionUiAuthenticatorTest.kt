package no.novari.test.integration.application.authenticators

import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Pages
import no.novari.test.integration.utils.KcContextParser
import no.novari.test.integration.utils.KcFlow.continueFromOrgSelector
import no.novari.test.integration.utils.KcFlow.openAuthUrl
import no.novari.test.integration.utils.KcHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvironmentExtension::class)
class OrgSelectionUiAuthenticatorTest {
    @Test
    fun `client returns page flais-org-selector`(env: KcEnvironment) {
        openAuthUrl(env = env, clientId = Clients.FLAIS_KEYCLOAK_DEMO).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertEquals(Pages.FLAIS_ORG_SELECTOR, kc.pageId)
        }
    }

    @Test
    fun `selecting non existing organization returns error`(env: KcEnvironment) {
        val client = KcHttpClient.create()
        openAuthUrl(env = env, clientId = Clients.FLAIS_KEYCLOAK_DEMO, httpClient = client).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromOrgSelector(kc.url.loginAction!!, Orgs.NON_EXISTING, client).use { resp ->
                assertEquals(200, resp.code)

                val html = resp.body.string()
                val kc = KcContextParser.parseKcContext(html)

                assertEquals(Pages.ERROR, kc.pageId)
                assertEquals("error", kc.message!!.type)
                assertEquals(
                    "Selected organization does not have permission to login to this application",
                    kc.message.summary,
                )
            }
        }
    }

    @Test
    fun `valid org (telemark) advances to next step`(env: KcEnvironment) {
        val client = KcHttpClient.create()
        openAuthUrl(env = env, clientId = Clients.FLAIS_KEYCLOAK_DEMO, httpClient = client).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromOrgSelector(kc.url.loginAction!!, Orgs.TELEMARK, client).use { resp ->
                assertEquals(200, resp.code)

                val html = resp.body.string()
                val kc = KcContextParser.parseKcContext(html)

                assertEquals(Pages.FLAIS_ORG_IDP_SELECTOR, kc.pageId)
            }
        }
    }

    @Test
    fun `client  with 1 org directly to flais-org-idp-selector`(env: KcEnvironment) {
        openAuthUrl(env = env, clientId = Clients.FLAIS_KEYCLOAK_DEMO_TELEMARK).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertEquals(Pages.FLAIS_ORG_IDP_SELECTOR, kc.pageId)
        }
    }

    @Test
    fun `client dont return org idporten`(env: KcEnvironment) {
        openAuthUrl(env = env, clientId = Clients.FLAIS_KEYCLOAK_DEMO_ENTRA).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertFalse(kc.organizations!!.map { it.alias }.contains(Orgs.IDPORTEN))
        }
    }
}
