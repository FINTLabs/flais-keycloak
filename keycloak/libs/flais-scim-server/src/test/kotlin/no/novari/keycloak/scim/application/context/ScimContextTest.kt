package no.novari.keycloak.scim.application.context

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import jakarta.ws.rs.NotFoundException
import no.novari.keycloak.scim.config.OrganizationScimConfig
import no.novari.keycloak.scim.config.ScimConfig
import no.novari.keycloak.scim.context.createScimContext
import no.novari.keycloak.scim.utils.TestUriInfo
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
import java.net.URI

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
        val uriInfo = TestUriInfo(URI("http://localhost/realms/test/scim/v2/any-org"))

        every { session.context } returns kcContext
        every { kcContext.realm } returns realm

        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
        every { orgProvider.getById(orgId) } returns organization

        every { organization.attributes } returns emptyMap()

        val scimContext = createScimContext(session, orgId, uriInfo)

        assertSame(session, scimContext.session)
        assertEquals(uriInfo.baseUri, scimContext.baseUri)
        assertSame(uriInfo, scimContext.uriInfo)
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
        val uriInfo = TestUriInfo(URI("http://localhost/realms/test/scim/v2/any-org"))

        every { session.context } returns null

        val ex =
            assertThrows<IllegalArgumentException> {
                createScimContext(session, orgId, uriInfo)
            }
        assertEquals("Keycloak context is not set", ex.message)
    }

    @Test
    fun `createScimContext throws NotFoundException when organization is not found`() {
        val orgId = "missing-org"
        val uriInfo = TestUriInfo(URI("http://localhost/realms/test/scim/v2/missing-org"))

        every { session.context } returns kcContext
        every { kcContext.realm } returns realm

        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
        every { orgProvider.getById(orgId) } returns null

        val ex =
            assertThrows<NotFoundException> {
                createScimContext(session, orgId, uriInfo)
            }
        assertEquals("Organization not found", ex.message)
    }
}
