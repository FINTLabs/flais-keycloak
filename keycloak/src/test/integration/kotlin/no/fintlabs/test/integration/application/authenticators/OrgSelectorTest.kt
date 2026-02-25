package no.fintlabs.test.integration.application.authenticators

import no.fintlabs.test.common.extensions.kc.KcEnvExtension
import no.fintlabs.test.common.utils.kc.KcEnvironment
import no.fintlabs.test.integration.utils.KcContextParser
import no.fintlabs.test.integration.utils.KcFlow.continueFromOrgSelector
import no.fintlabs.test.integration.utils.KcFlow.openAuthUrl
import no.fintlabs.test.integration.utils.KcHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class OrgSelectorTest {
    @Test
    fun `client (flais-keycloak-demo) returns page flais-org-selector`(env: KcEnvironment) {
        openAuthUrl(env = env, clientId = "flais-keycloak-demo").use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertEquals("flais-org-selector", kc.pageId)
        }
    }

    @Test
    fun `selecting non existing organization returns error`(env: KcEnvironment) {
        val client = KcHttpClient.create()
        openAuthUrl(env = env, clientId = "flais-keycloak-demo", httpClient = client).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromOrgSelector(kc.url.loginAction!!, "nonExistingOrg", client).use { resp ->
                assertEquals(200, resp.code)

                val html = resp.body.string()
                val kc = KcContextParser.parseKcContext(html)

                assertEquals("error", kc.pageId)
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
        openAuthUrl(env = env, clientId = "flais-keycloak-demo", httpClient = client).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromOrgSelector(kc.url.loginAction!!, "telemark", client).use { resp ->
                assertEquals(200, resp.code)

                val html = resp.body.string()
                val kc = KcContextParser.parseKcContext(html)

                assertEquals("flais-org-idp-selector", kc.pageId)
            }
        }
    }

    @Test
    fun `client (flais-keycloak-demo-telemark) with 1 org directly to flais-org-idp-selector`(env: KcEnvironment) {
        openAuthUrl(env = env, clientId = "flais-keycloak-demo-telemark").use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertEquals("flais-org-idp-selector", kc.pageId)
        }
    }

    @Test
    fun `client (flais-keycloak-demo-entra) dont return org idporten`(env: KcEnvironment) {
        openAuthUrl(env = env, clientId = "flais-keycloak-demo-entra").use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertFalse(kc.organizations!!.map { it.alias }.contains("idporten"))
        }
    }
}
