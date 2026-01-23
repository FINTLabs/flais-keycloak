package no.fintlabs.application.kc

import no.fintlabs.config.KcConfig
import no.fintlabs.extensions.KcEnvExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class KcConfigTest {
    private val commonRedirectUris = listOf("http://localhost*")
    private val commonWebOrigins = listOf("*")

    private fun assertCommonFlags(c: KcConfig.Client) {
        assertTrue(c.enabled)
        assertTrue(c.publicClient)
        assertEquals("openid-connect", c.protocol)
        assertTrue(c.standardFlowEnabled)
        assertEquals(commonRedirectUris, c.redirectUris)
        assertEquals(commonWebOrigins, c.webOrigins)
    }

    // --- Realm & aliases ------------------------------------------------------

    @Test
    fun `realm name should be external`(kcConfig: KcConfig) {
        assertEquals("external", kcConfig.realmName)
    }

    @Test
    fun `org aliases should include expected orgs`(kcConfig: KcConfig) {
        assertTrue(
            kcConfig
                .orgAliases()
                .containsAll(listOf("idporten", "innlandet", "telemark", "rogaland")),
        )
    }

    @Test
    fun `idp aliases should include expected idps`(kcConfig: KcConfig) {
        assertTrue(
            kcConfig
                .idpAliases()
                .containsAll(listOf("entra-telemark", "entra-telemark-alt", "idporten", "entra-rogaland")),
        )
    }

    // --- Org → IdP mapping ----------------------------------------------------

    @Test
    fun `idp aliases for idporten org should be idporten only`(kcConfig: KcConfig) {
        assertEquals(listOf("idporten"), kcConfig.idpAliasesForOrg("idporten"))
    }

    @Test
    fun `innlandet should have no idps`(kcConfig: KcConfig) {
        assertTrue(kcConfig.idpAliasesForOrg("innlandet").isEmpty())
    }

    @Test
    fun `telemark should have expected idps and not idporten`(kcConfig: KcConfig) {
        assertTrue(kcConfig.orgHasIdp("telemark", "entra-telemark"))
        assertTrue(kcConfig.orgHasIdp("telemark", "entra-telemark-alt"))
        assertFalse(kcConfig.orgHasIdp("telemark", "idporten"))
    }

    // --- Client ID set --------------------------------------------------------

    @Test
    fun `client ids should include all expected clients`(kcConfig: KcConfig) {
        assertTrue(
            kcConfig.clientIds().containsAll(
                listOf(
                    "flais-keycloak-demo",
                    "flais-keycloak-demo-telemark",
                    "flais-keycloak-demo-idporten",
                    "flais-keycloak-demo-invalid",
                    "flais-keycloak-demo-entra",
                ),
            ),
        )
    }

    // --- Per-client flags and attributes -------------------------------------

    @Test
    fun `flais-keycloak-demo should have common flags and empty attributes`(kcConfig: KcConfig) {
        kcConfig.requireClient("flais-keycloak-demo").also { c ->
            assertCommonFlags(c)
            assertEquals(emptyMap<String, String>(), c.attributes)
        }
    }

    @Test
    fun `flais-keycloak-demo-telemark should have common flags and telemark whitelisted`(kcConfig: KcConfig) {
        kcConfig.requireClient("flais-keycloak-demo-telemark").also { c ->
            assertCommonFlags(c)
            assertEquals(
                mapOf("permission.whitelisted.organizations" to "telemark"),
                c.attributes,
            )
        }
    }

    @Test
    fun `flais-keycloak-demo-idporten should have common flags and idporten whitelisted`(kcConfig: KcConfig) {
        kcConfig.requireClient("flais-keycloak-demo-idporten").also { c ->
            assertCommonFlags(c)
            assertEquals(
                mapOf("permission.whitelisted.organizations" to "idporten"),
                c.attributes,
            )
        }
    }

    @Test
    fun `flais-keycloak-demo-entra should have common flags and idporten blacklisted`(kcConfig: KcConfig) {
        kcConfig.requireClient("flais-keycloak-demo-entra").also { c ->
            assertCommonFlags(c)
            assertEquals(
                mapOf("permission.blacklisted.organizations" to "idporten"),
                c.attributes,
            )
        }
    }

    @Test
    fun `flais-keycloak-demo-invalid should have common flags and invalid-org whitelist`(kcConfig: KcConfig) {
        kcConfig.requireClient("flais-keycloak-demo-invalid").also { c ->
            assertCommonFlags(c)
            assertEquals(
                mapOf("permission.whitelisted.organizations" to "invalid-org"),
                c.attributes,
            )
        }
    }
}
