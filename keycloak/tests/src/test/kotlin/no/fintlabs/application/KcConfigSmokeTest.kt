package no.fintlabs.application

import no.fintlabs.config.KcTestConfig
import no.fintlabs.extensions.KcEnvExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(KcEnvExtension::class)
class KcConfigSmokeTest {

    @Test
    fun `keycloak config should match test scenarios`(kcConfig: KcTestConfig) {
        assertEquals("external", kcConfig.realmName)
        assertTrue(kcConfig.orgAliases().containsAll(listOf("idporten", "innlandet", "telemark")))
        assertTrue(kcConfig.idpAliases().containsAll(listOf("entra-telemark", "entra-telemark-alt", "idporten")))
        assertTrue(kcConfig.clientIds().contains("flais-keycloak-demo"))

        assertEquals(listOf("idporten"), kcConfig.idpAliasesForOrg("idporten"))
        assertTrue(kcConfig.idpAliasesForOrg("innlandet").isEmpty())
        assertTrue(kcConfig.orgHasIdp("telemark", "entra-telemark"))
        assertTrue(kcConfig.orgHasIdp("telemark", "entra-telemark-alt"))
        assertFalse(kcConfig.orgHasIdp("telemark", "idporten"))
    }
}