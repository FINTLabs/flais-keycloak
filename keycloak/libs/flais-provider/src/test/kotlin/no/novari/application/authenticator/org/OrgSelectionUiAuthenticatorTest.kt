package no.novari.application.authenticator.org

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.Response
import no.novari.attributes.OrgAttribute
import no.novari.authenticator.org.OrgSelectionUiAuthenticator
import no.novari.dtos.OrgDto
import no.novari.provider.ClientOrgAccessProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.events.Details
import org.keycloak.forms.login.LoginFormsProvider
import org.keycloak.http.HttpRequest
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.sessions.AuthenticationSessionModel
import java.util.stream.Stream

class OrgSelectionUiAuthenticatorTest {
    private lateinit var clientOrgAccessProvider: ClientOrgAccessProvider
    private lateinit var authenticator: OrgSelectionUiAuthenticator

    private fun mockContext(relaxed: Boolean = false): AuthenticationFlowContext {
        val context = mockk<AuthenticationFlowContext>(relaxed = relaxed)
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val session = mockk<KeycloakSession>()

        every { context.authenticationSession } returns authSession
        every { context.session } returns session
        every { session.getProvider(ClientOrgAccessProvider::class.java) } returns clientOrgAccessProvider

        return context
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

        val attrs = mutableMapOf<String, List<String>>()
        if (logo != null) {
            attrs[OrgAttribute.ORGANIZATION_LOGO] = listOf(logo)
        }
        every { org.attributes } returns attrs
        every { org.identityProviders } returns if (enabledIdps) Stream.of(idp) else Stream.empty()

        return org
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

    @BeforeEach
    fun setUp() {
        clientOrgAccessProvider = mockk()
        authenticator = OrgSelectionUiAuthenticator()
    }

    @Test
    fun `authenticate - already selected org exists in allowed list succeeds`() {
        val context = mockContext(relaxed = true)
        val authSession = context.authenticationSession
        val org1 = mockOrg("id-1", "org-1", "Org 1")
        val org2 = mockOrg("id-2", "org-2", "Org 2")

        every { authSession.getAuthNote(Details.ORG_ID) } returns org2.id
        every { clientOrgAccessProvider.getAllowedOrgs(context) } returns listOf(org1, org2)

        authenticator.authenticate(context)

        verify(exactly = 1) { clientOrgAccessProvider.getAllowedOrgs(context) }
        verify(exactly = 1) { context.success() }
        verify(exactly = 0) { context.challenge(any()) }
    }

    @Test
    fun `authenticate - exactly one allowed org sets note and succeeds`() {
        val context = mockContext(relaxed = true)
        val authSession = context.authenticationSession
        val org = mockOrg("id-1", "org-1", "Org 1")

        every { authSession.getAuthNote(Details.ORG_ID) } returns null
        every { clientOrgAccessProvider.getAllowedOrgs(context) } returns listOf(org)

        authenticator.authenticate(context)

        verify(exactly = 1) { clientOrgAccessProvider.getAllowedOrgs(context) }
        verify(exactly = 1) { authSession.setAuthNote(Details.ORG_ID, "id-1") }
        verify(exactly = 1) { context.success() }
        verify(exactly = 0) { context.challenge(any()) }
    }

    @Test
    fun `authenticate - multiple orgs challenges with selector form and organizations`() {
        val context = mockContext()
        val authSession = context.authenticationSession
        val formProvider = mockk<LoginFormsProvider>()
        val formResponse = mockk<Response>()
        val org1 = mockOrg("id-1", "org-1", "Org 1", logo = "logo1.png")
        val org2 = mockOrg("id-2", "org-2", "Org 2")

        every { authSession.getAuthNote(Details.ORG_ID) } returns null
        every { clientOrgAccessProvider.getAllowedOrgs(context) } returns listOf(org1, org2)

        mockFormChallenge(context, formProvider, formResponse)

        authenticator.authenticate(context)

        verify(exactly = 1) { clientOrgAccessProvider.getAllowedOrgs(context) }
        verify(exactly = 1) { formProvider.createForm("flais-org-selector.ftl") }
        verify(exactly = 1) { context.challenge(formResponse) }
        verify(exactly = 1) {
            formProvider.setAttribute(
                "organizations",
                match<List<OrgDto>> { list ->
                    list.size == 2 &&
                        list[0].alias == "org-1" &&
                        list[0].name == "Org 1" &&
                        list[0].logo == "logo1.png" &&
                        list[1].alias == "org-2" &&
                        list[1].name == "Org 2" &&
                        list[1].logo == null
                },
            )
        }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `action - missing selected_org rerenders form with error`() {
        val context = mockContext()
        val httpRequest = mockk<HttpRequest>()
        val formProvider = mockk<LoginFormsProvider>()
        val formResponse = mockk<Response>()
        val org1 = mockOrg("id-1", "org-1", "Org 1")
        val org2 = mockOrg("id-2", "org-2", "Org 2")

        every { context.httpRequest } returns httpRequest
        every { httpRequest.decodedFormParameters } returns MultivaluedHashMap<String, String>()
        every { clientOrgAccessProvider.getAllowedOrgs(context) } returns listOf(org1, org2)

        mockFormChallenge(context, formProvider, formResponse)

        authenticator.action(context)

        verify(exactly = 1) { clientOrgAccessProvider.getAllowedOrgs(context) }
        verify(exactly = 1) { formProvider.setError("You must select an organization to continue") }
        verify(exactly = 1) { formProvider.createForm("flais-org-selector.ftl") }
        verify(exactly = 1) { context.challenge(formResponse) }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `action - valid selected_org and remember-me on sets notes and succeeds`() {
        val context = mockContext(relaxed = true)
        val authSession = context.authenticationSession
        val httpRequest = mockk<HttpRequest>()
        val org = mockOrg("id-1", "org-1", "Org 1")

        every { context.httpRequest } returns httpRequest
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply {
                putSingle("selected_org", org.alias)
                putSingle(Details.REMEMBER_ME, "on")
            }
        every { clientOrgAccessProvider.getAllowedOrgs(context) } returns listOf(org)

        authenticator.action(context)

        verify(exactly = 1) { clientOrgAccessProvider.getAllowedOrgs(context) }
        verify(exactly = 1) { authSession.setAuthNote(Details.ORG_ID, "id-1") }
        verify(exactly = 1) { authSession.setAuthNote(Details.REMEMBER_ME, "true") }
        verify(exactly = 1) { context.success() }
    }

    @Test
    fun `action - selected_org not permitted should fail`() {
        val context = mockContext(relaxed = true)
        val httpRequest = mockk<HttpRequest>()
        val org = mockOrg("id-1", "org-1", "Org 1")

        every { context.httpRequest } returns httpRequest
        every { httpRequest.decodedFormParameters } returns
            MultivaluedHashMap<String, String>().apply {
                putSingle("selected_org", "org-x")
            }
        every { clientOrgAccessProvider.getAllowedOrgs(context) } returns listOf(org)

        authenticator.action(context)

        verify(exactly = 1) { clientOrgAccessProvider.getAllowedOrgs(context) }
        verify(exactly = 0) { context.success() }
    }
}
