package no.fintlabs.application.authenticator

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import jakarta.ws.rs.core.Response
import no.fintlabs.authenticator.OrgRedirectorAuthenticator
import org.junit.jupiter.api.Test
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.events.Details
import org.keycloak.forms.login.LoginFormsProvider
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.IdentityProviderStorageProvider
import org.keycloak.models.KeycloakSession
import org.keycloak.sessions.AuthenticationSessionModel

class OrgRedirectorAuthenticatorTest {
    val authenticator = spyk(OrgRedirectorAuthenticator(), recordPrivateCalls = true)

    private fun mockFailureFlow(
        context: AuthenticationFlowContext,
        formProvider: LoginFormsProvider,
        errorResponse: Response,
        expectedMessage: String,
    ) {
        every { context.form() } returns formProvider
        every { formProvider.setError(expectedMessage) } returns formProvider
        every { formProvider.createErrorPage(Response.Status.OK) } returns errorResponse
        every { context.challenge(errorResponse) } just Runs
    }

    @Test
    fun `authenticate - no identity provider selected should fail and return`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()
        val errorMessage = "No identity provider selected selected"

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.IDENTITY_PROVIDER) } returns null

        mockFailureFlow(
            context,
            formProvider,
            errorResponse,
            errorMessage,
        )

        authenticator.authenticate(context)

        verify(exactly = 1) { formProvider.setError(errorMessage) }
        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 0) { authenticator.doRedirect(any(), any()) }
        verify(exactly = 0) { context.session }
    }

    @Test
    fun `authenticate - idp not found should fail and return`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val session = mockk<KeycloakSession>()
        val idpStorageProvider = mockk<IdentityProviderStorageProvider>()
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()
        val idpId = "idp-1"
        val errorMessage = "Could not find identity provider"

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.IDENTITY_PROVIDER) } returns idpId
        every { context.session } returns session
        every { session.identityProviders() } returns idpStorageProvider
        every { idpStorageProvider.getById(idpId) } returns null

        mockFailureFlow(
            context,
            formProvider,
            errorResponse,
            errorMessage,
        )

        authenticator.authenticate(context)

        verify(exactly = 1) { idpStorageProvider.getById(idpId) }
        verify(exactly = 1) { formProvider.setError(errorMessage) }
        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 0) { authenticator.doRedirect(any(), any()) }
    }

    @Test
    fun `authenticate - idp found should redirect to idp alias`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val session = mockk<KeycloakSession>()
        val idpStorageProvider = mockk<IdentityProviderStorageProvider>()
        val idp = mockk<IdentityProviderModel>()
        val idpAlias = "idp-alias"
        val idpId = "idp-1"

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.IDENTITY_PROVIDER) } returns idpId
        every { context.session } returns session
        every { session.identityProviders() } returns idpStorageProvider
        every { idpStorageProvider.getById(idpId) } returns idp
        every { idp.alias } returns idpAlias
        every { authenticator.doRedirect(context, idpAlias) } just Runs

        authenticator.authenticate(context)

        verify(exactly = 1) { authenticator.doRedirect(context, idpAlias) }
        verify(exactly = 0) { context.form() }
        verify(exactly = 0) { context.challenge(any()) }
    }
}
