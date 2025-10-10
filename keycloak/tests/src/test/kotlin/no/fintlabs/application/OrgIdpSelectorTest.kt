package no.fintlabs.application

import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcContextParser
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.KcFlowUtils.selectOrgAndContinueToIdpSelector
import no.fintlabs.utils.KcFlowUtils.continueFromIdpSelector
import no.fintlabs.utils.KcHttpClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class OrgIdpSelectorTest {

    @Test
    fun `org (telemark) login with unlinked idp returns error`(env: KcComposeEnvironment) {
        val client = KcHttpClient.create()
        selectOrgAndContinueToIdpSelector(env, "telemark", client).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromIdpSelector(kc.url.loginAction!!, "idporten", client).use { idpResponse ->
                val responseHtml = idpResponse.body.string()
                val responseKc = KcContextParser.parseKcContext(responseHtml)

                assertEquals("error", responseKc.pageId)
                assertEquals("error", responseKc.message!!.type)
                assertEquals(
                    "The selected identity provider is not registered to the selected organization",
                    responseKc.message.summary
                )
            }
        }
    }

    @Test
    fun `org (telemark) login with wrong non existing idp returns error`(env: KcComposeEnvironment) {
        val client = KcHttpClient.create()
        selectOrgAndContinueToIdpSelector(env, "telemark", client).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromIdpSelector(kc.url.loginAction!!, "non-existing-idp", client).use { idpResponse ->
                val responseHtml = idpResponse.body.string()
                val responseKc = KcContextParser.parseKcContext(responseHtml)

                assertEquals("error", responseKc.pageId)
                assertEquals("error", responseKc.message!!.type)
                assertEquals("Could not find the selected identity provider", responseKc.message.summary)
            }
        }
    }

    @Test
    fun `org (innlandet) with no IDPs returns error`(env: KcComposeEnvironment) {
        selectOrgAndContinueToIdpSelector(env, "innlandet").use { resp ->
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

    @Test
    fun `org (idporten) with one IDP redirects to provider`(env: KcComposeEnvironment) {
        selectOrgAndContinueToIdpSelector(env, "idporten").use { resp ->
            assertEquals(303, resp.code)

            val loc1 = resp.header("Location").orEmpty()
            val hitsBroker = Regex("""/realms/external/broker/idporten/""").containsMatchIn(loc1)

            assertTrue(hitsBroker)
        }
    }

    @Test
    fun `org (telemark) with multiple IDPs returns IDPs`(env: KcComposeEnvironment) {
        selectOrgAndContinueToIdpSelector(env, "telemark").use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertEquals("flais-org-idp-selector", kc.pageId)
            assertNotNull(kc.providers)
            assertTrue(kc.providers?.size!! > 1)
            assertTrue(
                kc.providers.stream().anyMatch { p: KcContextParser.Provider? -> "entra-telemark" == p!!.alias }
            )
            assertTrue(
                kc.providers.stream().anyMatch { p: KcContextParser.Provider? -> "entra-telemark" == p!!.alias }
            )
        }
    }

    @Test
    fun `org (telemark) returns page flais-org-idp-selector`(env: KcComposeEnvironment) {
        selectOrgAndContinueToIdpSelector(env, "telemark").use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertEquals("flais-org-idp-selector", kc.pageId)
        }
    }
}
