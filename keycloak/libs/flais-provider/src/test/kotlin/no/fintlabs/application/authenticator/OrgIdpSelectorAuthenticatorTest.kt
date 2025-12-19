package no.fintlabs.application.authenticator

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.Response
import no.fintlabs.authenticator.OrgIdpSelectorAuthenticator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.events.Details
import org.keycloak.forms.login.LoginFormsProvider
import org.keycloak.http.HttpRequest
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.IdentityProviderStorageProvider
import org.keycloak.models.KeycloakSession
import org.keycloak.sessions.AuthenticationSessionModel
import java.util.stream.Stream

class OrgIdpSelectorAuthenticatorTest {
    private lateinit var authenticator: OrgIdpSelectorAuthenticator

    @BeforeEach
    fun setUp() {
        authenticator = OrgIdpSelectorAuthenticator()
    }

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
    fun `authenticate - no organization selected should fail`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()
        val errorMessage = "No organization selected"

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns null

        mockFailureFlow(context, formProvider, errorResponse, errorMessage)

        authenticator.authenticate(context)

        verify(exactly = 1) { authSession.getAuthNote(Details.ORG_ID) }
        verify(exactly = 1) { formProvider.setError(errorMessage) }
        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - selected org but no idps should fail`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val session = mockk<KeycloakSession>()
        val idpStorageProvider = mockk<IdentityProviderStorageProvider>()
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()
        val errorMessage = "The selected organization does not have any login options"

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns "org-1"
        every { context.session } returns session
        every { session.identityProviders() } returns idpStorageProvider
        every { idpStorageProvider.allStream } returns Stream.empty()

        mockFailureFlow(
            context,
            formProvider,
            errorResponse,
            errorMessage,
        )

        authenticator.authenticate(context)

        verify(exactly = 1) { formProvider.setError(errorMessage) }
        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - exactly one matching idp should set auth note and succeeds`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val session = mockk<KeycloakSession>()
        val idpStorageProvider = mockk<IdentityProviderStorageProvider>()
        val idp = mockk<IdentityProviderModel>()
        val orgId = "org-1"
        val idpId = "idp-1"

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns orgId
        every { context.session } returns session
        every { session.identityProviders() } returns idpStorageProvider
        every { idp.isEnabled } returns true
        every { idp.organizationId } returns orgId
        every { idp.alias } returns "idp-alias"
        every { idp.internalId } returns idpId
        every { idpStorageProvider.allStream } returns Stream.of(idp)

        authenticator.authenticate(context)

        verify(exactly = 1) {
            authSession.setAuthNote(Details.IDENTITY_PROVIDER, idpId)
        }
        verify(exactly = 1) { context.success() }
        verify(exactly = 0) { context.form() }
    }

    @Test
    fun `authenticate - multiple idps should challenges with idp selector form`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val session = mockk<KeycloakSession>()
        val idpStorageProvider = mockk<IdentityProviderStorageProvider>()
        val idp1 = mockk<IdentityProviderModel>()
        val idp2 = mockk<IdentityProviderModel>()
        val idps =
            listOf(
                idp1 to Triple("idp1", "IDP 1", "org-1"),
                idp2 to Triple("idp2", "IDP 2", "org-1"),
            )
        val orgId = "org-1"
        val formProvider = mockk<LoginFormsProvider>()
        val formResponse = mockk<Response>()
        val pageId = "flais-org-idp-selector.ftl"

        for ((idp, data) in idps) {
            val (alias, displayName, orgId) = data

            every { idp.isEnabled } returns true
            every { idp.organizationId } returns orgId
            every { idp.alias } returns alias
            every { idp.displayName } returns displayName
        }

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns orgId
        every { context.session } returns session
        every { session.identityProviders() } returns idpStorageProvider
        every { idpStorageProvider.allStream } returns Stream.of(idp1, idp2)
        every { context.form() } returns formProvider
        every { formProvider.setAttribute(eq("providers"), any()) } returns formProvider
        every { formProvider.createForm(pageId) } returns formResponse
        every { context.challenge(formResponse) } just Runs

        authenticator.authenticate(context)

        verify(exactly = 1) { formProvider.createForm(pageId) }
        verify(exactly = 1) { context.challenge(formResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `action - no identity provider selected should fail`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val httpRequest = mockk<HttpRequest>()
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()
        val errorMessage = "No identity provider selected"

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns "org-1"
        every { context.httpRequest } returns httpRequest
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply {
            }

        mockFailureFlow(context, formProvider, errorResponse, errorMessage)

        authenticator.action(context)

        verify(exactly = 1) { formProvider.setError(errorMessage) }
        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `action - selecting missing idp should fail`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val httpRequest = mockk<HttpRequest>()
        val session = mockk<KeycloakSession>()
        val idpStorageProvider = mockk<IdentityProviderStorageProvider>()
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()
        val idpAlias = "missing-idp"
        val errorMessage = "Could not find the selected identity provider"

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns "org-1"
        every { context.httpRequest } returns httpRequest
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply {
                putSingle(Details.IDENTITY_PROVIDER, idpAlias)
            }
        every { context.session } returns session
        every { session.identityProviders() } returns idpStorageProvider
        every { idpStorageProvider.getByAlias(idpAlias) } returns null

        mockFailureFlow(context, formProvider, errorResponse, errorMessage)

        authenticator.action(context)

        verify(exactly = 1) { formProvider.setError(errorMessage) }
        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `action - selecting disabled idp should fail`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val httpRequest = mockk<HttpRequest>()
        val session = mockk<KeycloakSession>()
        val idpStorageProvider = mockk<IdentityProviderStorageProvider>()
        val idp = mockk<IdentityProviderModel>()
        val idpAlias = "idp-1"
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()
        val errorMessage = "The selected identity provider is not enabled"

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns "org-1"
        every { context.httpRequest } returns httpRequest
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply {
                putSingle(Details.IDENTITY_PROVIDER, idpAlias)
            }
        every { context.session } returns session
        every { session.identityProviders() } returns idpStorageProvider
        every { idpStorageProvider.getByAlias(idpAlias) } returns idp
        every { idp.isEnabled } returns false

        mockFailureFlow(context, formProvider, errorResponse, errorMessage)

        authenticator.action(context)

        verify(exactly = 1) { formProvider.setError(errorMessage) }
        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `action - selected idp not registered to selected org should fail`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val httpRequest = mockk<HttpRequest>()
        val session = mockk<KeycloakSession>()
        val idpStorageProvider = mockk<IdentityProviderStorageProvider>()
        val idp = mockk<IdentityProviderModel>()
        val idpAlias = "idp-1"
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()
        val errorMessage = "The selected identity provider is not registered to the selected organization"

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns "org-1"
        every { context.httpRequest } returns httpRequest
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply {
                putSingle(Details.IDENTITY_PROVIDER, idpAlias)
            }
        every { context.session } returns session
        every { session.identityProviders() } returns idpStorageProvider
        every { idpStorageProvider.getByAlias(idpAlias) } returns idp
        every { idp.isEnabled } returns true
        every { idp.organizationId } returns "org-2"

        mockFailureFlow(
            context,
            formProvider,
            errorResponse,
            errorMessage,
        )

        authenticator.action(context)

        verify(exactly = 1) {
            formProvider.setError(errorMessage)
        }
        verify(exactly = 1) { context.challenge(errorResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `action - valid selection should set auth note and succeeds`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val httpRequest = mockk<HttpRequest>()
        val session = mockk<KeycloakSession>()
        val idpStorageProvider = mockk<IdentityProviderStorageProvider>()
        val idp = mockk<IdentityProviderModel>()
        val idpAlias = "idp-alias"
        val idpId = "idp-1"
        val orgId = "org-1"

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns orgId
        every { context.httpRequest } returns httpRequest
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply {
                putSingle(Details.IDENTITY_PROVIDER, idpAlias)
            }
        every { context.session } returns session
        every { session.identityProviders() } returns idpStorageProvider
        every { idpStorageProvider.getByAlias(idpAlias) } returns idp
        every { idp.isEnabled } returns true
        every { idp.organizationId } returns orgId
        every { idp.internalId } returns idpId

        authenticator.action(context)

        verify(exactly = 1) { authSession.setAuthNote(Details.IDENTITY_PROVIDER, idpId) }
        verify(exactly = 1) { context.success() }
    }
}
