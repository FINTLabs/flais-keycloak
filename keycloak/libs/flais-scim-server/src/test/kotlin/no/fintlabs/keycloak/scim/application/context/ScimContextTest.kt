package no.fintlabs.keycloak.scim.application.context

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import jakarta.ws.rs.NotFoundException
import no.fintlabs.keycloak.scim.config.OrganizationScimConfig
import no.fintlabs.keycloak.scim.config.ScimConfig
import no.fintlabs.keycloak.scim.context.createScimContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.models.KeycloakContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.models.RealmModel
import org.keycloak.organization.OrganizationProvider

@ExtendWith(MockKExtension::class)
class ScimContextTest {
    @MockK
    lateinit var session: KeycloakSession

    @MockK
    lateinit var kcContext: KeycloakContext

    @MockK
    lateinit var orgProvider: OrganizationProvider

    @MockK
    lateinit var organization: OrganizationModel

    @MockK
    lateinit var realm: RealmModel

    @Test
    fun `createScimContext returns ScimContext when organization exists and config is valid`() {
        val orgId = "any-org"

        every { session.context } returns kcContext
        every { kcContext.realm } returns realm

        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
        every { orgProvider.getById(orgId) } returns organization

        every { organization.attributes } returns emptyMap()

        val scimContext = createScimContext(session, orgId)

        assertSame(session, scimContext.session)
        assertSame(realm, scimContext.realm)
        assertSame(orgProvider, scimContext.orgProvider)
        assertSame(organization, scimContext.organization)

        assertTrue(scimContext.config is OrganizationScimConfig)

        val cfg = scimContext.config as OrganizationScimConfig
        assertEquals(ScimConfig.AuthenticationMode.KEYCLOAK, cfg.authenticationMode)
    }

    @Test
    fun `createScimContext throws IllegalArgumentException when Keycloak context is null`() {
        val orgId = "any-org"

        every { session.context } returns null

        val ex =
            assertThrows<IllegalArgumentException> {
                createScimContext(session, orgId)
            }
        assertEquals("Keycloak context is not set", ex.message)
    }

    @Test
    fun `createScimContext throws NotFoundException when organization is not found`() {
        val orgId = "missing-org"

        every { session.context } returns kcContext
        every { kcContext.realm } returns realm

        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
        every { orgProvider.getById(orgId) } returns null

        val ex =
            assertThrows<NotFoundException> {
                createScimContext(session, orgId)
            }
        assertEquals("Organization not found", ex.message)
    }
}
