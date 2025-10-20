package no.fintlabs.application.authenticators

import no.fintlabs.extensions.KcEnvExtension
import no.fintlabs.utils.KcComposeEnvironment
import no.fintlabs.utils.KcContextParser
import no.fintlabs.utils.KcFlow.continueFromIdpSelector
import no.fintlabs.utils.KcFlow.selectOrgAndContinueToIdpSelector
import no.fintlabs.utils.KcHttpClient
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class OrgIdpSelectorTest {
    @Test
    fun `org (telemark) login with unlinked idp returns error`(env: KcComposeEnvironment) {
        val client = KcHttpClient.create()
        selectOrgAndContinueToIdpSelector(env, "flais-keycloak-demo", "telemark", client).use { resp ->
            Assertions.assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromIdpSelector(kc.url.loginAction!!, "idporten", client).use { idpResponse ->
                val responseHtml = idpResponse.body.string()
                val responseKc = KcContextParser.parseKcContext(responseHtml)

                Assertions.assertEquals("error", responseKc.pageId)
                Assertions.assertEquals("error", responseKc.message!!.type)
                Assertions.assertEquals(
                    "The selected identity provider is not registered to the selected organization",
                    responseKc.message.summary,
                )
            }
        }
    }

    @Test
    fun `org (telemark) login with non existing idp returns error`(env: KcComposeEnvironment) {
        val client = KcHttpClient.create()
        selectOrgAndContinueToIdpSelector(env, "flais-keycloak-demo", "telemark", client).use { resp ->
            Assertions.assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromIdpSelector(kc.url.loginAction!!, "non-existing-idp", client).use { idpResponse ->
                val responseHtml = idpResponse.body.string()
                val responseKc = KcContextParser.parseKcContext(responseHtml)

                Assertions.assertEquals("error", responseKc.pageId)
                Assertions.assertEquals("error", responseKc.message!!.type)
                Assertions.assertEquals("Could not find the selected identity provider", responseKc.message.summary)
            }
        }
    }

    @Test
    fun `org (innlandet) with no IDPs returns error`(env: KcComposeEnvironment) {
        selectOrgAndContinueToIdpSelector(env, "flais-keycloak-demo", "innlandet").use { resp ->
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

    @Test
    fun `org (idporten) with one IDP redirects to provider`(env: KcComposeEnvironment) {
        selectOrgAndContinueToIdpSelector(env, "flais-keycloak-demo", "idporten").use { resp ->
            Assertions.assertEquals(303, resp.code)

            val loc1 = resp.header("Location").orEmpty()
            val hitsBroker = Regex("""/realms/external/broker/idporten/""").containsMatchIn(loc1)

            Assertions.assertTrue(hitsBroker)
        }
    }

    @Test
    fun `org (telemark) login with idp from org (rogaland) returns error`(env: KcComposeEnvironment) {
        val client = KcHttpClient.create()
        selectOrgAndContinueToIdpSelector(env, "flais-keycloak-demo", "telemark", client).use { resp ->
            Assertions.assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromIdpSelector(kc.url.loginAction!!, "entra-rogaland", client).use { idpResponse ->
                Assertions.assertEquals(200, idpResponse.code)

                val responseHtml = idpResponse.body.string()
                val responseKc = KcContextParser.parseKcContext(responseHtml)

                Assertions.assertEquals("error", responseKc.pageId)
                Assertions.assertEquals("error", responseKc.message!!.type)
                Assertions.assertEquals(
                    "The selected identity provider is not registered to the selected organization",
                    responseKc.message.summary,
                )
            }
        }
    }

    @Test
    fun `org (telemark) with multiple IDPs returns IDPs`(env: KcComposeEnvironment) {
        selectOrgAndContinueToIdpSelector(env, "flais-keycloak-demo", "telemark").use { resp ->
            Assertions.assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            Assertions.assertEquals("flais-org-idp-selector", kc.pageId)
            Assertions.assertNotNull(kc.providers)
            Assertions.assertTrue(kc.providers?.size!! > 1)
            Assertions.assertTrue(
                kc.providers.stream().anyMatch { p: KcContextParser.Provider? -> "entra-telemark" == p!!.alias },
            )
            Assertions.assertTrue(
                kc.providers.stream().anyMatch { p: KcContextParser.Provider? -> "entra-telemark" == p!!.alias },
            )
        }
    }

    @Test
    fun `org (telemark) returns page flais-org-idp-selector`(env: KcComposeEnvironment) {
        selectOrgAndContinueToIdpSelector(env, "flais-keycloak-demo", "telemark").use { resp ->
            Assertions.assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            Assertions.assertEquals("flais-org-idp-selector", kc.pageId)
        }
    }
}
