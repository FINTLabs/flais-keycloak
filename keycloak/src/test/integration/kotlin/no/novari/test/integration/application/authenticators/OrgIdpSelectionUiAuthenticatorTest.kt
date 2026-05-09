package no.novari.test.integration.application.authenticators

import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Pages
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.integration.utils.KcContextParser
import no.novari.test.integration.utils.KcFlow.continueFromIdpSelector
import no.novari.test.integration.utils.KcFlow.selectOrgAndContinueToIdpSelector
import no.novari.test.integration.utils.KcHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvironmentExtension::class)
class OrgIdpSelectionUiAuthenticatorTest {
    @Test
    fun `org (telemark) login with unlinked idp returns error`(env: KcEnvironment) {
        val client = KcHttpClient.create()
        selectOrgAndContinueToIdpSelector(env, Clients.FLAIS_KEYCLOAK_DEMO, Orgs.TELEMARK, client).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromIdpSelector(kc.url.loginAction!!, Orgs.IDPORTEN, client).use { idpResponse ->
                val responseHtml = idpResponse.body.string()
                val responseKc = KcContextParser.parseKcContext(responseHtml)

                assertEquals(Pages.ERROR, responseKc.pageId)
                assertEquals("error", responseKc.message!!.type)
                assertEquals(
                    "The selected identity provider is not registered to the selected organization",
                    responseKc.message.summary,
                )
            }
        }
    }

    @Test
    fun `org (telemark) login with non existing idp returns error`(env: KcEnvironment) {
        val client = KcHttpClient.create()
        selectOrgAndContinueToIdpSelector(env, Clients.FLAIS_KEYCLOAK_DEMO, Orgs.TELEMARK, client).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromIdpSelector(kc.url.loginAction!!, Idps.NON_EXISTING, client).use { idpResponse ->
                val responseHtml = idpResponse.body.string()
                val responseKc = KcContextParser.parseKcContext(responseHtml)

                assertEquals(Pages.ERROR, responseKc.pageId)
                assertEquals("error", responseKc.message!!.type)
                assertEquals(
                    "Could not find the selected identity provider",
                    responseKc.message.summary,
                )
            }
        }
    }

    @Test
    fun `org (innlandet) with no IDPs returns error`(env: KcEnvironment) {
        selectOrgAndContinueToIdpSelector(env, Clients.FLAIS_KEYCLOAK_DEMO, Orgs.INNLANDET).use { resp ->
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

    @Test
    fun `org (idporten) with one IDP redirects to provider`(env: KcEnvironment) {
        selectOrgAndContinueToIdpSelector(env, Clients.FLAIS_KEYCLOAK_DEMO, Orgs.IDPORTEN).use { resp ->
            assertEquals(303, resp.code)

            val loc1 = resp.header("Location").orEmpty()
            val hitsBroker = Regex("""/realms/external/broker/idporten/""").containsMatchIn(loc1)

            assertTrue(hitsBroker)
        }
    }

    @Test
    fun `org (telemark) login with idp from org (rogaland) returns error`(env: KcEnvironment) {
        val client = KcHttpClient.create()
        selectOrgAndContinueToIdpSelector(env, Clients.FLAIS_KEYCLOAK_DEMO, Orgs.TELEMARK, client).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            continueFromIdpSelector(kc.url.loginAction!!, Idps.ENTRA_ROGALAND, client).use { idpResponse ->
                assertEquals(200, idpResponse.code)

                val responseHtml = idpResponse.body.string()
                val responseKc = KcContextParser.parseKcContext(responseHtml)

                assertEquals(Pages.ERROR, responseKc.pageId)
                assertEquals("error", responseKc.message!!.type)
                assertEquals(
                    "The selected identity provider is not registered to the selected organization",
                    responseKc.message.summary,
                )
            }
        }
    }

    @Test
    fun `org (telemark) with multiple IDPs returns IDPs`(env: KcEnvironment) {
        selectOrgAndContinueToIdpSelector(env, Clients.FLAIS_KEYCLOAK_DEMO, Orgs.TELEMARK).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertEquals(Pages.FLAIS_ORG_IDP_SELECTOR, kc.pageId)
            assertNotNull(kc.providers)
            assertTrue(kc.providers.size > 1)
            assertTrue(
                kc.providers
                    .stream()
                    .anyMatch { p: KcContextParser.Provider? -> Idps.ENTRA_TELEMARK == p!!.alias },
            )
            assertTrue(
                kc.providers
                    .stream()
                    .anyMatch { p: KcContextParser.Provider? -> Idps.ENTRA_TELEMARK_ALT == p!!.alias },
            )
        }
    }

    @Test
    fun `org (telemark) returns page flais-org-idp-selector`(env: KcEnvironment) {
        selectOrgAndContinueToIdpSelector(env, Clients.FLAIS_KEYCLOAK_DEMO, Orgs.TELEMARK).use { resp ->
            assertEquals(200, resp.code)

            val html = resp.body.string()
            val kc = KcContextParser.parseKcContext(html)

            assertEquals(Pages.FLAIS_ORG_IDP_SELECTOR, kc.pageId)
        }
    }
}
