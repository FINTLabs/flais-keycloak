package no.novari.application.provider

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.novari.attributes.ClientPermissionAttribute.PERMISSION_BLACKLISTED_ORGANIZATIONS
import no.novari.attributes.ClientPermissionAttribute.PERMISSION_WHITELISTED_ORGANIZATIONS
import no.novari.provider.ClientOrgAccessProvider
import no.novari.provider.DefaultClientOrgAccessProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.models.ClientModel
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.organization.OrganizationProvider
import org.keycloak.sessions.AuthenticationSessionModel
import java.util.stream.Stream

class ClientOrgAccessProviderTest {
    private lateinit var session: KeycloakSession
    private lateinit var orgProvider: OrganizationProvider
    private lateinit var context: AuthenticationFlowContext
    private lateinit var authSession: AuthenticationSessionModel
    private lateinit var client: ClientModel
    private lateinit var provider: ClientOrgAccessProvider

    private fun mockOrg(
        id: String,
        alias: String,
        enabled: Boolean,
        hasEnabledIdp: Boolean,
    ): OrganizationModel {
        val org = mockk<OrganizationModel>()
        val enabledIdp = mockk<IdentityProviderModel>()
        val disabledIdp = mockk<IdentityProviderModel>()

        every { org.id } returns id
        every { org.alias } returns alias
        every { org.isEnabled } returns enabled

        every { enabledIdp.isEnabled } returns true
        every { disabledIdp.isEnabled } returns false

        every { org.identityProviders } returns
            if (hasEnabledIdp) {
                Stream.of(enabledIdp, disabledIdp)
            } else {
                Stream.of(disabledIdp)
            }

        return org
    }

    @BeforeEach
    fun setUp() {
        session = mockk()
        orgProvider = mockk()
        context = mockk()
        authSession = mockk()
        client = mockk()

        every { context.authenticationSession } returns authSession
        every { authSession.client } returns client
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider

        provider = DefaultClientOrgAccessProvider(session)
    }

    @Test
    fun `getAllowedOrgs returns all enabled orgs with enabled idps when no whitelist or blacklist`() {
        val org1 = mockOrg(id = "id-1", alias = "org-1", enabled = true, hasEnabledIdp = true)
        val org2 = mockOrg(id = "id-2", alias = "org-2", enabled = true, hasEnabledIdp = true)
        val disabledOrg = mockOrg(id = "id-3", alias = "org-3", enabled = false, hasEnabledIdp = true)
        val noEnabledIdpOrg = mockOrg(id = "id-4", alias = "org-4", enabled = true, hasEnabledIdp = false)

        every { client.attributes } returns emptyMap()
        every { orgProvider.allStream } returns Stream.of(org1, org2, disabledOrg, noEnabledIdpOrg)

        val result = provider.getAllowedOrgs(context)

        assert(result.map { it.alias } == listOf("org-1", "org-2"))
        verify(exactly = 1) { orgProvider.allStream }
    }

    @Test
    fun `getAllowedOrgs applies whitelist`() {
        val org1 = mockOrg(id = "id-1", alias = "org-1", enabled = true, hasEnabledIdp = true)
        val org2 = mockOrg(id = "id-2", alias = "org-2", enabled = true, hasEnabledIdp = true)
        val org3 = mockOrg(id = "id-3", alias = "org-3", enabled = true, hasEnabledIdp = true)

        every { client.attributes } returns
            mapOf(
                PERMISSION_WHITELISTED_ORGANIZATIONS to "org-1, org-3",
            )
        every { orgProvider.allStream } returns Stream.of(org1, org2, org3)

        val result = provider.getAllowedOrgs(context)

        assert(result.map { it.alias } == listOf("org-1", "org-3"))
    }

    @Test
    fun `getAllowedOrgs applies blacklist`() {
        val org1 = mockOrg(id = "id-1", alias = "org-1", enabled = true, hasEnabledIdp = true)
        val org2 = mockOrg(id = "id-2", alias = "org-2", enabled = true, hasEnabledIdp = true)
        val org3 = mockOrg(id = "id-3", alias = "org-3", enabled = true, hasEnabledIdp = true)

        every { client.attributes } returns
            mapOf(
                PERMISSION_BLACKLISTED_ORGANIZATIONS to "org-2",
            )
        every { orgProvider.allStream } returns Stream.of(org1, org2, org3)

        val result = provider.getAllowedOrgs(context)

        assert(result.map { it.alias } == listOf("org-1", "org-3"))
    }

    @Test
    fun `getAllowedOrgs applies whitelist and blacklist together`() {
        val org1 = mockOrg(id = "id-1", alias = "org-1", enabled = true, hasEnabledIdp = true)
        val org2 = mockOrg(id = "id-2", alias = "org-2", enabled = true, hasEnabledIdp = true)
        val org3 = mockOrg(id = "id-3", alias = "org-3", enabled = true, hasEnabledIdp = true)

        every { client.attributes } returns
            mapOf(
                PERMISSION_WHITELISTED_ORGANIZATIONS to "org-1, org-2, org-3",
                PERMISSION_BLACKLISTED_ORGANIZATIONS to "org-2",
            )
        every { orgProvider.allStream } returns Stream.of(org1, org2, org3)

        val result = provider.getAllowedOrgs(context)

        assert(result.map { it.alias } == listOf("org-1", "org-3"))
    }

    @Test
    fun `getAllowedOrgs ignores blank values in whitelist and blacklist`() {
        val org1 = mockOrg(id = "id-1", alias = "org-1", enabled = true, hasEnabledIdp = true)
        val org2 = mockOrg(id = "id-2", alias = "org-2", enabled = true, hasEnabledIdp = true)

        every { client.attributes } returns
            mapOf(
                PERMISSION_WHITELISTED_ORGANIZATIONS to " org-1, ,  ",
                PERMISSION_BLACKLISTED_ORGANIZATIONS to "   ",
            )
        every { orgProvider.allStream } returns Stream.of(org1, org2)

        val result = provider.getAllowedOrgs(context)

        assert(result.map { it.alias } == listOf("org-1"))
    }

    @Test
    fun `isOrgAllowed returns false when organization is disabled`() {
        val org = mockOrg(id = "id-1", alias = "org-1", enabled = false, hasEnabledIdp = true)

        every { client.attributes } returns emptyMap()

        val result = provider.isOrgAllowed(context, org)

        assert(!result)
    }

    @Test
    fun `isOrgAllowed returns true when whitelist is empty and org is not blacklisted`() {
        val org = mockOrg(id = "id-1", alias = "org-1", enabled = true, hasEnabledIdp = true)

        every { client.attributes } returns emptyMap()

        val result = provider.isOrgAllowed(context, org)

        assert(result)
    }

    @Test
    fun `isOrgAllowed returns true when org is whitelisted`() {
        val org = mockOrg(id = "id-1", alias = "org-1", enabled = true, hasEnabledIdp = true)

        every { client.attributes } returns
            mapOf(
                PERMISSION_WHITELISTED_ORGANIZATIONS to "org-1, org-2",
            )

        val result = provider.isOrgAllowed(context, org)

        assert(result)
    }

    @Test
    fun `isOrgAllowed returns false when org is not whitelisted`() {
        val org = mockOrg(id = "id-1", alias = "org-1", enabled = true, hasEnabledIdp = true)

        every { client.attributes } returns
            mapOf(
                PERMISSION_WHITELISTED_ORGANIZATIONS to "org-2, org-3",
            )

        val result = provider.isOrgAllowed(context, org)

        assert(!result)
    }

    @Test
    fun `isOrgAllowed returns false when org is blacklisted`() {
        val org = mockOrg(id = "id-1", alias = "org-1", enabled = true, hasEnabledIdp = true)

        every { client.attributes } returns
            mapOf(
                PERMISSION_BLACKLISTED_ORGANIZATIONS to "org-1",
            )

        val result = provider.isOrgAllowed(context, org)

        assert(!result)
    }

    @Test
    fun `getOrganizationModel returns organization by id`() {
        val org = mockOrg(id = "id-1", alias = "org-1", enabled = true, hasEnabledIdp = true)

        every { orgProvider.getById("id-1") } returns org

        val result = provider.getOrganizationModel("id-1")

        assert(result == org)
        verify(exactly = 1) { orgProvider.getById("id-1") }
    }
}
