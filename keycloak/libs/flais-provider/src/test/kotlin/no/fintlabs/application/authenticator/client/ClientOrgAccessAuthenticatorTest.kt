package no.fintlabs.application.authenticator.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import no.fintlabs.authenticator.client.ClientOrgAccessAuthenticator
import no.fintlabs.service.ClientOrgAccessService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.authenticators.broker.util.PostBrokerLoginConstants
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext
import org.keycloak.models.AuthenticatedClientSessionModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.organization.OrganizationProvider
import java.util.stream.Stream

class ClientOrgAccessAuthenticatorTest {
    private lateinit var clientOrgAccessService: ClientOrgAccessService
    private lateinit var authenticator: ClientOrgAccessAuthenticator

    @BeforeEach
    fun setUp() {
        clientOrgAccessService = mockk()
        authenticator = ClientOrgAccessAuthenticator(clientOrgAccessService)

        mockkStatic(SerializedBrokeredIdentityContext::class)
    }

    @Test
    fun `authenticate - missing brokered identity context should fail`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<org.keycloak.sessions.AuthenticationSessionModel>()
        mockk<AuthenticatedClientSessionModel>(relaxed = true)

        every { context.authenticationSession } returns authSession
        every { authSession.client } returns
            mockk {
                every { clientId } returns "test-client"
            }
        every {
            SerializedBrokeredIdentityContext.readFromAuthenticationSession(
                authSession,
                PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT,
            )
        } returns null

        authenticator.authenticate(context)

        verify(exactly = 1) {
            SerializedBrokeredIdentityContext.readFromAuthenticationSession(
                authSession,
                PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT,
            )
        }
        verify(exactly = 0) { clientOrgAccessService.isOrgAllowed(any(), any()) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - missing identity provider alias should fail`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<org.keycloak.sessions.AuthenticationSessionModel>()
        val serializedCtx = mockk<SerializedBrokeredIdentityContext>()

        every { context.authenticationSession } returns authSession
        every { authSession.client } returns
            mockk {
                every { clientId } returns "test-client"
            }
        every {
            SerializedBrokeredIdentityContext.readFromAuthenticationSession(
                authSession,
                PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT,
            )
        } returns serializedCtx
        every { serializedCtx.identityProviderId } returns ""

        authenticator.authenticate(context)

        verify(exactly = 1) { serializedCtx.identityProviderId }
        verify(exactly = 0) { clientOrgAccessService.isOrgAllowed(any(), any()) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - organization not found for idp should fail`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<org.keycloak.sessions.AuthenticationSessionModel>()
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val serializedCtx = mockk<SerializedBrokeredIdentityContext>()

        every { context.authenticationSession } returns authSession
        every { authSession.client } returns
            mockk {
                every { clientId } returns "test-client"
            }
        every { context.session } returns session
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
        every { orgProvider.allStream } returns Stream.empty()

        every {
            SerializedBrokeredIdentityContext.readFromAuthenticationSession(
                authSession,
                PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT,
            )
        } returns serializedCtx
        every { serializedCtx.identityProviderId } returns "idp-alias"

        authenticator.authenticate(context)

        verify(exactly = 1) { session.getProvider(OrganizationProvider::class.java) }
        verify(exactly = 0) { clientOrgAccessService.isOrgAllowed(any(), any()) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - organization not allowed for client should fail`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<org.keycloak.sessions.AuthenticationSessionModel>()
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val serializedCtx = mockk<SerializedBrokeredIdentityContext>()
        val organization = mockk<OrganizationModel>()
        val idp = mockk<org.keycloak.models.IdentityProviderModel>()

        every { context.authenticationSession } returns authSession
        every { authSession.client } returns
            mockk {
                every { clientId } returns "test-client"
            }
        every { context.session } returns session
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider

        every {
            SerializedBrokeredIdentityContext.readFromAuthenticationSession(
                authSession,
                PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT,
            )
        } returns serializedCtx
        every { serializedCtx.identityProviderId } returns "idp-alias"

        every { organization.identityProviders } returns Stream.of(idp)
        every { organization.alias } returns "org-alias"
        every { idp.alias } returns "idp-alias"
        every { orgProvider.allStream } returns Stream.of(organization)

        every { clientOrgAccessService.isOrgAllowed(context, organization) } returns false

        authenticator.authenticate(context)

        verify(exactly = 1) { clientOrgAccessService.isOrgAllowed(context, organization) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - allowed organization should succeed`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<org.keycloak.sessions.AuthenticationSessionModel>()
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val serializedCtx = mockk<SerializedBrokeredIdentityContext>()
        val organization = mockk<OrganizationModel>()
        val idp = mockk<org.keycloak.models.IdentityProviderModel>()

        every { context.authenticationSession } returns authSession
        every { authSession.client } returns
            mockk {
                every { clientId } returns "test-client"
            }
        every { context.session } returns session
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider

        every {
            SerializedBrokeredIdentityContext.readFromAuthenticationSession(
                authSession,
                PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT,
            )
        } returns serializedCtx
        every { serializedCtx.identityProviderId } returns "idp-alias"

        every { organization.identityProviders } returns Stream.of(idp)
        every { organization.alias } returns "org-alias"
        every { idp.alias } returns "idp-alias"
        every { orgProvider.allStream } returns Stream.of(organization)

        every { clientOrgAccessService.isOrgAllowed(context, organization) } returns true

        authenticator.authenticate(context)

        verify(exactly = 1) { clientOrgAccessService.isOrgAllowed(context, organization) }
        verify(exactly = 1) { context.success() }
    }
}
