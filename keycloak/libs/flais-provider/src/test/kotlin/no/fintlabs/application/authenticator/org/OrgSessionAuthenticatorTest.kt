package no.fintlabs.application.authenticator.org

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import jakarta.ws.rs.core.Response
import no.fintlabs.authenticator.org.OrgSessionCommitAuthenticator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.events.Details
import org.keycloak.forms.login.LoginFormsProvider
import org.keycloak.models.KeycloakContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.organization.OrganizationProvider
import org.keycloak.sessions.AuthenticationSessionModel

class OrgSessionAuthenticatorTest {
    private lateinit var authenticator: OrgSessionCommitAuthenticator

    @BeforeEach
    fun setUp() {
        authenticator = OrgSessionCommitAuthenticator()
    }

    private fun mockOrg(
        id: String = "id-1",
        alias: String = "org-1",
        enabled: Boolean = true,
    ): OrganizationModel {
        val organization = mockk<OrganizationModel>()
        every { organization.id } returns id
        every { organization.alias } returns alias
        every { organization.isEnabled } returns enabled
        return organization
    }

    private fun mockFailureChallenge(
        context: AuthenticationFlowContext,
        formProvider: LoginFormsProvider,
        errorResponse: Response,
        expectedMessage: String,
    ) {
        every { context.form() } returns formProvider
        every { formProvider.setError(expectedMessage) } returns formProvider
        every { formProvider.createErrorPage(Response.Status.BAD_REQUEST) } returns errorResponse
        every {
            context.failureChallenge(
                AuthenticationFlowError.INTERNAL_ERROR,
                errorResponse,
            )
        } just Runs
    }

    @Test
    fun `authenticate - missing org id fails with bad request`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns null

        mockFailureChallenge(
            context,
            formProvider,
            errorResponse,
            "No organization selected",
        )

        authenticator.authenticate(context)

        verify(exactly = 1) {
            formProvider.setError("No organization selected")
        }
        verify(exactly = 1) {
            formProvider.createErrorPage(Response.Status.BAD_REQUEST)
        }
        verify(exactly = 1) {
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, errorResponse)
        }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - blank org id fails with bad request`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns "   "

        mockFailureChallenge(
            context,
            formProvider,
            errorResponse,
            "No organization selected",
        )

        authenticator.authenticate(context)

        verify(exactly = 1) {
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, errorResponse)
        }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - organization not found fails with bad request`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()

        every { context.authenticationSession } returns authSession
        every { context.session } returns session
        every { authSession.getAuthNote(Details.ORG_ID) } returns "id-1"
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
        every { orgProvider.getById("id-1") } returns null

        mockFailureChallenge(
            context,
            formProvider,
            errorResponse,
            "Selected organization is invalid",
        )

        authenticator.authenticate(context)

        verify(exactly = 1) { orgProvider.getById("id-1") }
        verify(exactly = 1) {
            formProvider.setError("Selected organization is invalid")
        }
        verify(exactly = 1) {
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, errorResponse)
        }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - disabled organization fails with bad request`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()
        val organization =
            mockOrg()

        every { context.authenticationSession } returns authSession
        every { context.session } returns session
        every { authSession.getAuthNote(Details.ORG_ID) } returns "id-1"
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
        every { orgProvider.getById("id-1") } returns organization

        mockFailureChallenge(
            context,
            formProvider,
            errorResponse,
            "Selected organization is invalid",
        )

        authenticator.authenticate(context)

        verify(exactly = 1) { orgProvider.getById("id-1") }
        verify(exactly = 1) {
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, errorResponse)
        }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - valid organization commits notes context and succeeds`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val session = mockk<KeycloakSession>(relaxed = true)
        val keycloakContext = mockk<KeycloakContext>(relaxed = true)
        val orgProvider = mockk<OrganizationProvider>()
        val organization =
            mockOrg()

        every { context.authenticationSession } returns authSession
        every { context.session } returns session
        every { authSession.getAuthNote(Details.ORG_ID) } returns "id-1"
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
        every { orgProvider.getById("id-1") } returns organization
        every { session.context } returns keycloakContext

        authenticator.authenticate(context)

        verify(exactly = 1) {
            authSession.setAuthNote(OrganizationModel.ORGANIZATION_ATTRIBUTE, "id-1")
        }
        verify(exactly = 1) {
            authSession.setClientNote(OrganizationModel.ORGANIZATION_ATTRIBUTE, "id-1")
        }
        verify(exactly = 1) {
            authSession.setUserSessionNote(OrganizationModel.ORGANIZATION_ATTRIBUTE, "id-1")
        }
        verify(exactly = 1) {
            keycloakContext.organization = organization
        }
        verify(exactly = 1) { context.success() }
        verify(exactly = 0) { context.failureChallenge(any(), any()) }
    }
}
