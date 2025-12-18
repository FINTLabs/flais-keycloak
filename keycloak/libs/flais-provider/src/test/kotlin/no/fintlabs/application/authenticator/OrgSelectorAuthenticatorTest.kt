package no.fintlabs.application.authenticator

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.Response
import no.fintlabs.access.ClientAccess.CLIENT_BLACKLIST_ORGANIZATIONS_ATTRIBUTE
import no.fintlabs.access.ClientAccess.CLIENT_WHITELIST_ORGANIZATIONS_ATTRIBUTE
import no.fintlabs.access.OrgAccess
import no.fintlabs.authenticator.OrgSelectorAuthenticator
import no.fintlabs.dtos.OrgDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.events.Details
import org.keycloak.forms.login.LoginFormsProvider
import org.keycloak.http.HttpRequest
import org.keycloak.models.ClientModel
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.organization.OrganizationProvider
import org.keycloak.sessions.AuthenticationSessionModel
import java.util.stream.Stream

class OrgSelectorAuthenticatorTest {
    private lateinit var authenticator: OrgSelectorAuthenticator

    @BeforeEach
    fun setUp() {
        authenticator = OrgSelectorAuthenticator()
    }

    private fun mockOrg(
        id: String,
        alias: String,
        name: String,
        logo: String? = null,
        enabledIdps: Boolean = true,
    ): OrganizationModel {
        val org = mockk<OrganizationModel>()
        val idp = mockk<IdentityProviderModel>()

        every { org.id } returns id
        every { org.alias } returns alias
        every { org.name } returns name
        every { idp.isEnabled } returns true

        val attrs: MutableMap<String, List<String>> = mutableMapOf()
        if (logo != null) attrs[OrgAccess.ORG_LOGO_ATTRIBUTE] = listOf(logo)
        every { org.attributes } returns attrs

        if (enabledIdps) {
            every { org.identityProviders } returns Stream.of(idp)
        } else {
            every { org.identityProviders } returns Stream.empty()
        }

        return org
    }

    private fun mockOrgProvider(
        context: AuthenticationFlowContext,
        session: KeycloakSession,
        orgProvider: OrganizationProvider,
        orgs: List<OrganizationModel>,
    ) {
        every { context.session } returns session
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
        every { orgProvider.allStream } returns orgs.stream()
    }

    private fun mockClientAttributes(
        authSession: AuthenticationSessionModel,
        client: ClientModel,
        attributes: Map<String, String>,
    ) {
        every { authSession.client } returns client
        every { client.attributes } returns attributes
    }

    private fun mockFormChallenge(
        context: AuthenticationFlowContext,
        formProvider: LoginFormsProvider,
        formResponse: Response,
    ) {
        every { context.form() } returns formProvider
        every { formProvider.setError(any()) } returns formProvider
        every { formProvider.setAttribute(any(), any()) } returns formProvider
        every { formProvider.createForm(any()) } returns formResponse
        every { context.challenge(formResponse) } just Runs
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
    fun `authenticate - already selected org exists in filtered list succeeds`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val client = mockk<ClientModel>()
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val org1 = mockOrg("id-1", "org-1", "Org 1")
        val org2 = mockOrg("id-2", "org-2", "Org 2")

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns org2.alias

        mockClientAttributes(authSession, client, emptyMap())
        mockOrgProvider(context, session, orgProvider, listOf(org1, org2))

        authenticator.authenticate(context)

        verify(exactly = 1) { context.success() }
        verify(exactly = 0) { context.challenge(any()) }
    }

    @Test
    fun `authenticate - exactly one org after filtering, sets note and succeeds`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val client = mockk<ClientModel>()
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val org = mockOrg("id-1", "org-1", "Org 1")

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns null

        mockClientAttributes(authSession, client, emptyMap())
        mockOrgProvider(context, session, orgProvider, listOf(org))

        authenticator.authenticate(context)

        verify(exactly = 1) { authSession.setAuthNote(Details.ORG_ID, "id-1") }
        verify(exactly = 1) { context.success() }
        verify(exactly = 0) { context.challenge(any()) }
    }

    @Test
    fun `authenticate - multiple orgs challenges with selector form and organizations`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val client = mockk<ClientModel>()
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val formProvider = mockk<LoginFormsProvider>()
        val formResponse = mockk<Response>()
        val org1 = mockOrg("id-1", "org-1", "Org 1", logo = "logo1.png")
        val org2 = mockOrg("id-2", "org-2", "Org 2", logo = null)

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns null

        mockClientAttributes(authSession, client, emptyMap())
        mockOrgProvider(context, session, orgProvider, listOf(org1, org2))
        mockFormChallenge(context, formProvider, formResponse)

        authenticator.authenticate(context)

        verify(exactly = 1) { formProvider.createForm("flais-org-selector.ftl") }
        verify(exactly = 1) { context.challenge(formResponse) }
        verify(exactly = 1) {
            formProvider.setAttribute(
                "organizations",
                match<List<OrgDto>> { list ->
                    list.size == 2 &&
                        list[0].alias == "org-1" && list[0].name == "Org 1" && list[0].logo == "logo1.png" &&
                        list[1].alias == "org-2" && list[1].name == "Org 2" && list[1].logo == null
                },
            )
        }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `action - missing selected_org rerenders form with error`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val client = mockk<ClientModel>()
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val httpRequest = mockk<HttpRequest>()
        val formProvider = mockk<LoginFormsProvider>()
        val formResponse = mockk<Response>()
        val org1 = mockOrg("id-1", "org-1", "Org 1")
        val org2 = mockOrg("id-2", "org-2", "Org 2")

        every { context.authenticationSession } returns authSession
        every { context.httpRequest } returns httpRequest
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply {
            }

        mockClientAttributes(authSession, client, emptyMap())
        mockOrgProvider(context, session, orgProvider, listOf(org1, org2))
        mockFormChallenge(context, formProvider, formResponse)

        authenticator.action(context)

        verify(exactly = 1) { formProvider.setError("You must select a organization to continue") }
        verify(exactly = 1) { formProvider.createForm("flais-org-selector.ftl") }
        verify(exactly = 1) { context.challenge(formResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `action - valid selected_org and remember-me on sets notes and succeeds`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val client = mockk<ClientModel>()
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val httpRequest = mockk<HttpRequest>()
        val org = mockOrg("id-1", "org-1", "Org 1")

        every { context.authenticationSession } returns authSession
        every { context.httpRequest } returns httpRequest
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply {
                putSingle("selected_org", org.alias)
                putSingle(Details.REMEMBER_ME, "on")
            }

        mockClientAttributes(authSession, client, emptyMap())
        mockOrgProvider(context, session, orgProvider, listOf(org))

        authenticator.action(context)

        verify(exactly = 1) { authSession.setAuthNote(Details.ORG_ID, "id-1") }
        verify(exactly = 1) { authSession.setAuthNote(Details.REMEMBER_ME, "true") }
        verify(exactly = 1) { context.success() }
    }

    @Test
    fun `action - selected_org not permitted should fail`() {
        val context = mockk<AuthenticationFlowContext>()
        val authSession = mockk<AuthenticationSessionModel>()
        val client = mockk<ClientModel>()
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()
        val httpRequest = mockk<HttpRequest>()
        val formProvider = mockk<LoginFormsProvider>()
        val errorResponse = mockk<Response>()
        val errorMessage = "Selected organization does not have permission to login to this application"
        val org = mockOrg("id-1", "org-1", "Org 1")

        every { context.authenticationSession } returns authSession
        every { context.httpRequest } returns httpRequest
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply {
                putSingle("selected_org", "org-x")
            }

        mockClientAttributes(authSession, client, emptyMap())
        mockOrgProvider(context, session, orgProvider, listOf(org))
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
    fun `org filtering applies whitelist & blacklist and enabled idps`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val client = mockk<ClientModel>()
        val session = mockk<KeycloakSession>()
        val orgProvider = mockk<OrganizationProvider>()

        val whitelisted = mockOrg("id-1", "org-1", "Org 1")
        val blacklisted = mockOrg("id-2", "org-2", "Org 2")
        val noEnabledIdps = mockOrg("id-3", "org-3", "Org 3", enabledIdps = false)

        every { context.authenticationSession } returns authSession
        every { authSession.getAuthNote(Details.ORG_ID) } returns null

        mockClientAttributes(
            authSession,
            client,
            mapOf(
                CLIENT_WHITELIST_ORGANIZATIONS_ATTRIBUTE to "${whitelisted.alias},${noEnabledIdps.alias}",
                CLIENT_BLACKLIST_ORGANIZATIONS_ATTRIBUTE to blacklisted.alias,
            ),
        )
        mockOrgProvider(context, session, orgProvider, listOf(whitelisted, blacklisted, noEnabledIdps))

        authenticator.authenticate(context)

        verify(exactly = 1) { authSession.setAuthNote(Details.ORG_ID, "id-1") }
        verify(exactly = 1) { context.success() }
        verify(exactly = 0) { context.challenge(any()) }
    }
}
