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
import org.keycloak.events.Details
import org.keycloak.forms.login.LoginFormsProvider
import org.keycloak.models.KeycloakContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.organization.OrganizationProvider
import org.keycloak.sessions.AuthenticationSessionModel

class OrgSessionCommitAuthenticatorTest {
    private lateinit var authenticator: OrgSessionCommitAuthenticator

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
        every { formProvider.createErrorPage(Response.Status.OK) } returns errorResponse
        every { context.challenge(errorResponse) } just Runs
    }

    private fun mockContext(relaxed: Boolean = false): ContextSetup {
        val context = mockk<AuthenticationFlowContext>(relaxed = relaxed)
        val authSession = mockk<AuthenticationSessionModel>(relaxed = relaxed)
        val session = mockk<KeycloakSession>(relaxed = relaxed)
        val keycloakContext = mockk<KeycloakContext>(relaxed = relaxed)
        val orgProvider = mockk<OrganizationProvider>()

        every { context.authenticationSession } returns authSession
        every { context.session } returns session
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
        every { session.context } returns keycloakContext

        return ContextSetup(
            context = context,
            authSession = authSession,
            session = session,
            keycloakContext = keycloakContext,
            orgProvider = orgProvider,
        )
    }

    private data class ContextSetup(
        val context: AuthenticationFlowContext,
        val authSession: AuthenticationSessionModel,
        val session: KeycloakSession,
        val keycloakContext: KeycloakContext,
        val orgProvider: OrganizationProvider,
    )

    @BeforeEach
    fun setUp() {
        authenticator = OrgSessionCommitAuthenticator()
    }

    @Test
    fun `authenticate - missing org id fails with bad request`() {
        val setup = mockContext()
        val context = setup.context
        val authSession = setup.authSession
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()

        every { authSession.getAuthNote(Details.ORG_ID) } returns null
        mockFailureChallenge(context, formProvider, errorResponse, "No organization selected")

        authenticator.authenticate(context)

        verify(exactly = 1) { formProvider.setError("No organization selected") }
        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - blank org id fails with bad request`() {
        val setup = mockContext()
        val context = setup.context
        val authSession = setup.authSession
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()

        every { authSession.getAuthNote(Details.ORG_ID) } returns "   "
        mockFailureChallenge(context, formProvider, errorResponse, "No organization selected")

        authenticator.authenticate(context)

        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - organization not found fails with bad request`() {
        val setup = mockContext()
        val context = setup.context
        val authSession = setup.authSession
        val orgProvider = setup.orgProvider
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()

        every { authSession.getAuthNote(Details.ORG_ID) } returns "id-1"
        every { orgProvider.getById("id-1") } returns null
        mockFailureChallenge(context, formProvider, errorResponse, "Selected organization is invalid")

        authenticator.authenticate(context)

        verify(exactly = 1) { orgProvider.getById("id-1") }
        verify(exactly = 1) { formProvider.setError("Selected organization is invalid") }
        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - disabled organization fails with bad request`() {
        val setup = mockContext()
        val context = setup.context
        val authSession = setup.authSession
        val orgProvider = setup.orgProvider
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()
        val organization = mockOrg(enabled = false)

        every { authSession.getAuthNote(Details.ORG_ID) } returns "id-1"
        every { orgProvider.getById("id-1") } returns organization
        mockFailureChallenge(context, formProvider, errorResponse, "Selected organization is invalid")

        authenticator.authenticate(context)

        verify(exactly = 1) { orgProvider.getById("id-1") }
        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - valid organization commits notes context and succeeds`() {
        val setup = mockContext(relaxed = true)
        val context = setup.context
        val authSession = setup.authSession
        val orgProvider = setup.orgProvider
        val keycloakContext = setup.keycloakContext
        val organization = mockOrg()

        every { authSession.getAuthNote(Details.ORG_ID) } returns "id-1"
        every { orgProvider.getById("id-1") } returns organization

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
