package no.fintlabs.keycloak.scim.application.config

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import no.fintlabs.keycloak.scim.config.ConfigurationError
import no.fintlabs.keycloak.scim.config.OrganizationScimConfig
import no.fintlabs.keycloak.scim.config.ScimConfig
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.models.OrganizationModel

@ExtendWith(MockKExtension::class)
class OrganizationScimConfigTest {
    @MockK
    lateinit var organization: OrganizationModel

    private fun createConfig(attributes: Map<String, List<String>>): OrganizationScimConfig {
        every { organization.attributes } returns attributes
        return OrganizationScimConfig(organization)
    }

    @Test
    fun `authenticationMode defaults to KEYCLOAK when not set`() {
        val config = createConfig(emptyMap())

        assertEquals(ScimConfig.AuthenticationMode.KEYCLOAK, config.authenticationMode)
        assertDoesNotThrow { config.validateConfig() }
    }

    @Test
    fun `authenticationMode EXTERNAL requires all external fields`() {
        val baseAttrs =
            mapOf(
                "SCIM_AUTHENTICATION_MODE" to listOf("EXTERNAL"),
                "SCIM_EXTERNAL_ISSUER" to listOf("https://issuer.example.com"),
                "SCIM_EXTERNAL_JWKS_URI" to listOf("https://issuer.example.com/jwks"),
                "SCIM_EXTERNAL_AUDIENCE" to listOf("audience"),
            )

        val config = createConfig(baseAttrs)
        assertEquals(ScimConfig.AuthenticationMode.EXTERNAL, config.authenticationMode)
        assertEquals("https://issuer.example.com", config.externalIssuer)
        assertEquals("https://issuer.example.com/jwks", config.externalJwksUri)
        assertEquals("audience", config.externalAudience)

        assertDoesNotThrow { config.validateConfig() }
    }

    @Test
    fun `validateConfig throws when EXTERNAL and externalIssuer is missing`() {
        val attrs =
            mapOf(
                "SCIM_AUTHENTICATION_MODE" to listOf("EXTERNAL"),
                "SCIM_EXTERNAL_JWKS_URI" to listOf("https://issuer.example.com/jwks"),
                "SCIM_EXTERNAL_AUDIENCE" to listOf("audience"),
            )

        val ex =
            assertThrows<ConfigurationError> {
                createConfig(attrs).validateConfig()
            }
        assertEquals("SCIM_EXTERNAL_ISSUER is not set", ex.message)
    }

    @Test
    fun `validateConfig throws when EXTERNAL and externalJwksUri is missing`() {
        val attrs =
            mapOf(
                "SCIM_AUTHENTICATION_MODE" to listOf("EXTERNAL"),
                "SCIM_EXTERNAL_ISSUER" to listOf("https://issuer.example.com"),
                "SCIM_EXTERNAL_AUDIENCE" to listOf("audience"),
            )

        val ex =
            assertThrows<ConfigurationError> {
                createConfig(attrs).validateConfig()
            }
        assertEquals("SCIM_EXTERNAL_JWKS_URI is not set", ex.message)
    }

    @Test
    fun `validateConfig throws when EXTERNAL and externalAudience is missing`() {
        val attrs =
            mapOf(
                "SCIM_AUTHENTICATION_MODE" to listOf("EXTERNAL"),
                "SCIM_EXTERNAL_ISSUER" to listOf("https://issuer.example.com"),
                "SCIM_EXTERNAL_JWKS_URI" to listOf("https://issuer.example.com/jwks"),
            )

        val ex =
            assertThrows<ConfigurationError> {
                createConfig(attrs).validateConfig()
            }
        assertEquals("SCIM_EXTERNAL_AUDIENCE is not set", ex.message)
    }

    @Test
    fun `linkIdp is true only when SCIM_LINK_IDP equals true ignoring case`() {
        assertTrue(
            createConfig(
                mapOf("SCIM_LINK_IDP" to listOf("true")),
            ).linkIdp,
        )

        assertTrue(
            createConfig(
                mapOf("SCIM_LINK_IDP" to listOf("TRUE")),
            ).linkIdp,
        )

        assertFalse(
            createConfig(
                mapOf("SCIM_LINK_IDP" to listOf("false")),
            ).linkIdp,
        )

        assertFalse(createConfig(emptyMap()).linkIdp)
    }

    @Test
    fun `emailAsUsername is true only when SCIM_EMAIL_AS_USERNAME equals true ignoring case`() {
        assertTrue(
            createConfig(
                mapOf("SCIM_EMAIL_AS_USERNAME" to listOf("true")),
            ).emailAsUsername,
        )

        assertTrue(
            createConfig(
                mapOf("SCIM_EMAIL_AS_USERNAME" to listOf("TRUE")),
            ).emailAsUsername,
        )

        assertFalse(
            createConfig(
                mapOf("SCIM_EMAIL_AS_USERNAME" to listOf("false")),
            ).emailAsUsername,
        )

        assertFalse(createConfig(emptyMap()).emailAsUsername)
    }

    @Test
    fun `getAttribute uses first value from organization attributes`() {
        assertEquals(
            "first",
            createConfig(
                mapOf(
                    "SCIM_EXTERNAL_ISSUER" to listOf("first", "second"),
                ),
            ).externalIssuer,
        )
    }
}
