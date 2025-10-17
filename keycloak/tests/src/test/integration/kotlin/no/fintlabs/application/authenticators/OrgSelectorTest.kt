package no.fintlabs.application.authenticators

import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.KcContextParser
import no.fintlabs.utils.KcFlow.continueFromOrgSelector
import no.fintlabs.utils.KcFlow.openAuthUrl
import no.fintlabs.utils.KcHttpClient
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class OrgSelectorTest {
    @Test
    fun `client (flais-keycloak-demo) returns page flais-org-selector`(env: KcComposeEnvironment) {
        openAuthUrl(env = env, clientId = "flais-keycloak-demo").use { resp ->
            Assertions.assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            Assertions.assertEquals("flais-org-selector", kc.pageId)
        }
    }

    @Test
    fun `selecting non existing organization returns error`(env: KcComposeEnvironment) {
        val client = KcHttpClient.create()
        openAuthUrl(env = env, clientId = "flais-keycloak-demo", httpClient = client).use { resp ->
            Assertions.assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromOrgSelector(kc.url.loginAction!!, "nonExistingOrg", client).use { resp ->
                Assertions.assertEquals(200, resp.code)

                val html = resp.body.string()
                val kc = KcContextParser.parseKcContext(html)

                Assertions.assertEquals("error", kc.pageId)
                Assertions.assertEquals("error", kc.message!!.type)
                Assertions.assertEquals(
                    "Selected organization does not have permission to login to this application",
                    kc.message.summary,
                )
            }
        }
    }

    @Test
    fun `valid org (telemark) advances to next step`(env: KcComposeEnvironment) {
        val client = KcHttpClient.create()
        openAuthUrl(env = env, clientId = "flais-keycloak-demo", httpClient = client).use { resp ->
            Assertions.assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromOrgSelector(kc.url.loginAction!!, "telemark", client).use { resp ->
                Assertions.assertEquals(200, resp.code)

                val html = resp.body.string()
                val kc = KcContextParser.parseKcContext(html)

                Assertions.assertEquals("flais-org-idp-selector", kc.pageId)
            }
        }
    }

    @Test
    fun `client (flais-keycloak-demo-telemark) with 1 org directly to flais-org-idp-selector`(
        env: KcComposeEnvironment,
    ) {
        openAuthUrl(env = env, clientId = "flais-keycloak-demo-telemark").use { resp ->
            Assertions.assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            Assertions.assertEquals("flais-org-idp-selector", kc.pageId)
        }
    }

    @Test
    fun `client (flais-keycloak-demo-entra) dont return org idporten`(env: KcComposeEnvironment) {
        openAuthUrl(env = env, clientId = "flais-keycloak-demo-entra").use { resp ->
            Assertions.assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            Assertions.assertFalse(kc.organizations!!.map { it.alias }.contains("idporten"))
        }
    }
}
