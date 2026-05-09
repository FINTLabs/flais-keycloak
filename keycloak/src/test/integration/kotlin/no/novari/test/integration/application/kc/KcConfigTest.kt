package no.novari.test.integration.application.kc

import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Clients
import no.novari.test.common.fixture.TestStrings.Idps
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Realms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvironmentExtension::class)
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
        assertEquals(Realms.EXTERNAL, kcConfig.realmName)
    }

    @Test
    fun `org aliases should include expected orgs`(kcConfig: KcConfig) {
        assertTrue(
            kcConfig
                .orgAliases()
                .containsAll(listOf(Orgs.IDPORTEN, Orgs.INNLANDET, Orgs.TELEMARK, Orgs.ROGALAND)),
        )
    }

    @Test
    fun `idp aliases should include expected idps`(kcConfig: KcConfig) {
        assertTrue(
            kcConfig
                .idpAliases()
                .containsAll(listOf(Idps.ENTRA_TELEMARK, Idps.ENTRA_TELEMARK_ALT, Idps.IDPORTEN, Idps.ENTRA_ROGALAND)),
        )
    }

    // --- Org → IdP mapping ----------------------------------------------------

    @Test
    fun `idp aliases for idporten org should be idporten only`(kcConfig: KcConfig) {
        assertEquals(listOf(Idps.IDPORTEN), kcConfig.idpAliasesForOrg(Orgs.IDPORTEN))
    }

    @Test
    fun `innlandet should have no idps`(kcConfig: KcConfig) {
        assertTrue(kcConfig.idpAliasesForOrg(Orgs.INNLANDET).isEmpty())
    }

    @Test
    fun `telemark should have expected idps and not idporten`(kcConfig: KcConfig) {
        assertTrue(kcConfig.orgHasIdp(Orgs.TELEMARK, Idps.ENTRA_TELEMARK))
        assertTrue(kcConfig.orgHasIdp(Orgs.TELEMARK, Idps.ENTRA_TELEMARK_ALT))
        assertFalse(kcConfig.orgHasIdp(Orgs.TELEMARK, Idps.IDPORTEN))
    }

    // --- Client ID set --------------------------------------------------------

    @Test
    fun `client ids should include all expected clients`(kcConfig: KcConfig) {
        assertTrue(
            kcConfig.clientIds().containsAll(
                listOf(
                    Clients.FLAIS_KEYCLOAK_DEMO,
                    Clients.FLAIS_KEYCLOAK_DEMO_TELEMARK,
                    Clients.FLAIS_KEYCLOAK_DEMO_IDPORTEN,
                    Clients.FLAIS_KEYCLOAK_DEMO_INVALID,
                    Clients.FLAIS_KEYCLOAK_DEMO_ENTRA,
                    Clients.FLAIS_KEYCLOAK_DEMO_NOT_TELEMARK,
                ),
            ),
        )
    }

    // --- Per-client flags and attributes -------------------------------------

    @Test
    fun `flais-keycloak-demo should have common flags and empty attributes`(kcConfig: KcConfig) {
        kcConfig.requireClient(Clients.FLAIS_KEYCLOAK_DEMO).also { c ->
            assertCommonFlags(c)
            assertEquals(emptyMap<String, String>(), c.attributes)
        }
    }

    @Test
    fun `flais-keycloak-demo-telemark should have common flags and telemark whitelisted`(kcConfig: KcConfig) {
        kcConfig.requireClient(Clients.FLAIS_KEYCLOAK_DEMO_TELEMARK).also { c ->
            assertCommonFlags(c)
            assertEquals(
                mapOf("permission.whitelisted.organizations" to Orgs.TELEMARK),
                c.attributes,
            )
        }
    }

    @Test
    fun `flais-keycloak-demo-idporten should have common flags and idporten whitelisted`(kcConfig: KcConfig) {
        kcConfig.requireClient(Clients.FLAIS_KEYCLOAK_DEMO_IDPORTEN).also { c ->
            assertCommonFlags(c)
            assertEquals(
                mapOf("permission.whitelisted.organizations" to Orgs.IDPORTEN),
                c.attributes,
            )
        }
    }

    @Test
    fun `flais-keycloak-demo-entra should have common flags and idporten blacklisted`(kcConfig: KcConfig) {
        kcConfig.requireClient(Clients.FLAIS_KEYCLOAK_DEMO_ENTRA).also { c ->
            assertCommonFlags(c)
            assertEquals(
                mapOf("permission.blacklisted.organizations" to Orgs.IDPORTEN),
                c.attributes,
            )
        }
    }

    @Test
    fun `flais-keycloak-demo-invalid should have common flags and invalid-org whitelist`(kcConfig: KcConfig) {
        kcConfig.requireClient(Clients.FLAIS_KEYCLOAK_DEMO_INVALID).also { c ->
            assertCommonFlags(c)
            assertEquals(
                mapOf("permission.whitelisted.organizations" to Orgs.INVALID),
                c.attributes,
            )
        }
    }
}
