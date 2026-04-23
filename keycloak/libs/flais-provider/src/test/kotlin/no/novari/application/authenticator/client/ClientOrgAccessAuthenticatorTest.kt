package no.novari.application.authenticator.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import no.novari.authenticator.client.ClientOrgAccessAuthenticator
import no.novari.provider.ClientOrgAccessProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.authenticators.broker.util.PostBrokerLoginConstants
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext
import org.keycloak.models.ClientModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.organization.OrganizationProvider
import org.keycloak.sessions.AuthenticationSessionModel
import java.util.stream.Stream

class ClientOrgAccessAuthenticatorTest {
    private lateinit var clientOrgAccessProvider: ClientOrgAccessProvider
    private lateinit var authenticator: ClientOrgAccessAuthenticator

    private fun mockContextWithClient(clientId: String = "test-client"): AuthenticationFlowContext {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<AuthenticationSessionModel>()
        val client = mockk<ClientModel>()

        every { context.authenticationSession } returns authSession
        every { authSession.client } returns client
        every { client.clientId } returns clientId

        return context
    }

    private fun mockBrokeredIdentityContext(
        authSession: AuthenticationSessionModel,
        serializedCtx: SerializedBrokeredIdentityContext?,
    ) {
        every {
            SerializedBrokeredIdentityContext.readFromAuthenticationSession(
                authSession,
                PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT,
            )
        } returns serializedCtx
    }

    private fun mockSessionProviders(
        context: AuthenticationFlowContext,
        session: KeycloakSession,
        orgProvider: OrganizationProvider,
    ) {
        every { context.session } returns session
        every { session.getProvider(ClientOrgAccessProvider::class.java) } returns clientOrgAccessProvider
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
    }

    private fun mockOrganizationWithIdp(
        orgAlias: String = "org-1",
        idpAlias: String = "idp-1",
    ): OrganizationModel {
        val organization = mockk<OrganizationModel>()
        val idp = mockk<org.keycloak.models.IdentityProviderModel>()

        every { organization.alias } returns orgAlias
        every { organization.identityProviders } returns Stream.of(idp)
        every { idp.alias } returns idpAlias

        return organization
    }

    @BeforeEach
    fun setUp() {
        clientOrgAccessProvider = mockk()
        authenticator = ClientOrgAccessAuthenticator()
        mockkStatic(SerializedBrokeredIdentityContext::class)
    }

    @Test
    fun `authenticate - missing brokered identity context should fail`() {
        val context = mockContextWithClient()
        val authSession = context.authenticationSession

        mockBrokeredIdentityContext(authSession, null)

        authenticator.authenticate(context)

        verify(exactly = 1) {
            SerializedBrokeredIdentityContext.readFromAuthenticationSession(
                authSession,
                PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT,
            )
        }
        verify(exactly = 0) { clientOrgAccessProvider.isOrgAllowed(any(), any()) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - missing identity provider alias should fail`() {
        val context = mockContextWithClient()
        val authSession = context.authenticationSession
        val serializedCtx = mockk<SerializedBrokeredIdentityContext>()

        mockBrokeredIdentityContext(authSession, serializedCtx)
        every { serializedCtx.identityProviderId } returns ""

        authenticator.authenticate(context)

        verify(exactly = 1) { serializedCtx.identityProviderId }
        verify(exactly = 0) { clientOrgAccessProvider.isOrgAllowed(any(), any()) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - organization not found for idp should fail`() {
        val context = mockContextWithClient()
        val authSession = context.authenticationSession
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val serializedCtx = mockk<SerializedBrokeredIdentityContext>()

        mockSessionProviders(context, session, orgProvider)
        every { orgProvider.allStream } returns Stream.empty()

        mockBrokeredIdentityContext(authSession, serializedCtx)
        every { serializedCtx.identityProviderId } returns "idp-1"

        authenticator.authenticate(context)

        verify(exactly = 1) { session.getProvider(OrganizationProvider::class.java) }
        verify(exactly = 0) { clientOrgAccessProvider.isOrgAllowed(any(), any()) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - organization not allowed for client should fail`() {
        val context = mockContextWithClient()
        val authSession = context.authenticationSession
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val serializedCtx = mockk<SerializedBrokeredIdentityContext>()
        val organization = mockOrganizationWithIdp()

        mockSessionProviders(context, session, orgProvider)
        every { orgProvider.allStream } returns Stream.of(organization)

        mockBrokeredIdentityContext(authSession, serializedCtx)
        every { serializedCtx.identityProviderId } returns "idp-1"

        every { clientOrgAccessProvider.isOrgAllowed(context, organization) } returns false

        authenticator.authenticate(context)

        verify(exactly = 1) { clientOrgAccessProvider.isOrgAllowed(context, organization) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - allowed organization should succeed`() {
        val context = mockContextWithClient()
        val authSession = context.authenticationSession
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val serializedCtx = mockk<SerializedBrokeredIdentityContext>()
        val organization = mockOrganizationWithIdp()

        mockSessionProviders(context, session, orgProvider)
        every { orgProvider.allStream } returns Stream.of(organization)

        mockBrokeredIdentityContext(authSession, serializedCtx)
        every { serializedCtx.identityProviderId } returns "idp-1"

        every { clientOrgAccessProvider.isOrgAllowed(context, organization) } returns true

        authenticator.authenticate(context)

        verify(exactly = 1) { clientOrgAccessProvider.isOrgAllowed(context, organization) }
        verify(exactly = 1) { context.success() }
    }
}
