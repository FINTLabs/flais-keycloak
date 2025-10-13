package no.fintlabs.application

import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.KcContextParser
import no.fintlabs.utils.KcFlowUtils.continueFromOrgSelector
import no.fintlabs.utils.KcFlowUtils.openOrgSelector
import no.fintlabs.utils.KcHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class OrgSelectorTest {

    @Test
    fun `client (flais-keycloak-demo) returns page flais-org-selector`(env: KcComposeEnvironment) {
        openOrgSelector(env, "flais-keycloak-demo").use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertEquals("flais-org-selector", kc.pageId)
        }
    }

    @Test
    fun `selecting non existing organization returns error`(env: KcComposeEnvironment) {
        val client = KcHttpClient.create()
        openOrgSelector(env, "flais-keycloak-demo", client).use { resp ->
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
                    kc.message.summary
                )
            }
        }
    }

    @Test
    fun `valid org (telemark) advances to next step`(env: KcComposeEnvironment) {
        val client = KcHttpClient.create()
        openOrgSelector(env, "flais-keycloak-demo", client).use { resp ->
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
    fun `client (flais-keycloak-demo-telemark) with 1 org directly to flais-org-idp-selector`(env: KcComposeEnvironment) {
        openOrgSelector(env, "flais-keycloak-demo-telemark").use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertEquals("flais-org-idp-selector", kc.pageId)
        }
    }

    @Test
    fun `client (flais-keycloak-demo-entra) dont return org idporten`(env: KcComposeEnvironment) {
        openOrgSelector(env, "flais-keycloak-demo-entra").use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertFalse(kc.organizations!!.map { it.alias }.contains("idporten"))
        }
    }
}