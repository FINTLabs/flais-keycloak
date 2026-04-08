package no.fintlabs.application.authenticator.org

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.verify
import no.fintlabs.authenticator.org.OrgCookieAuthenticator
import no.fintlabs.service.ClientOrgAccessService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticatorUtil
import org.keycloak.authentication.authenticators.util.AcrStore
import org.keycloak.authentication.authenticators.util.AuthenticatorUtils
import org.keycloak.models.AuthenticationFlowModel
import org.keycloak.models.Constants
import org.keycloak.models.KeycloakContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.keycloak.models.UserSessionModel
import org.keycloak.organization.OrganizationProvider
import org.keycloak.organization.protocol.mappers.oidc.OrganizationScope
import org.keycloak.organization.utils.Organizations
import org.keycloak.protocol.LoginProtocol
import org.keycloak.services.managers.AuthenticationManager
import org.keycloak.services.messages.Messages
import org.keycloak.sessions.AuthenticationSessionModel

class OrgCookieAuthenticatorTest {
    private lateinit var authenticator: OrgCookieAuthenticator

    @BeforeEach
    fun setUp() {
        authenticator = OrgCookieAuthenticator(ClientOrgAccessService())
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

    @Test
    fun `authenticate - no identity cookie attempts`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val session = mockk<KeycloakSession>()
        val realm = mockk<RealmModel>()

        every { context.session } returns session
        every { context.realm } returns realm

        mockkStatic(AuthenticationManager::class)
        every {
            AuthenticationManager.authenticateIdentityCookie(session, realm, true)
        } returns null

        authenticator.authenticate(context)

        verify(exactly = 1) { context.attempted() }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - reauthentication required sets notes message and attempts`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val session = mockk<KeycloakSession>()
        val realm = mockk<RealmModel>()
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val protocol = mockk<LoginProtocol>()
        val authResult = mockk<AuthenticationManager.AuthResult>()
        val userSession = mockk<UserSessionModel>()
        val user = mockk<UserModel>()

        every { context.session } returns session
        every { context.realm } returns realm
        every { context.authenticationSession } returns authSession
        every { authSession.protocol } returns "openid-connect"

        every { authResult.session() } returns userSession
        every { authResult.user() } returns user
        every { userSession.getNote(Constants.LOA_MAP) } returns "loa-map"

        every { session.getProvider(LoginProtocol::class.java, "openid-connect") } returns protocol
        every { protocol.requireReauthentication(userSession, authSession) } returns true

        mockkStatic(AuthenticationManager::class)
        every {
            AuthenticationManager.authenticateIdentityCookie(session, realm, true)
        } returns authResult

        every { context.user = user } just Runs

        mockkConstructor(AcrStore::class)
        every { anyConstructed<AcrStore>().setLevelAuthenticatedToCurrentRequest(Constants.NO_LOA) } just Runs

        authenticator.authenticate(context)

        verify(exactly = 1) { authSession.setAuthNote(Constants.LOA_MAP, "loa-map") }
        verify(exactly = 1) { context.user = user }
        verify(exactly = 1) {
            anyConstructed<AcrStore>().setLevelAuthenticatedToCurrentRequest(Constants.NO_LOA)
        }
        verify(exactly = 1) {
            authSession.setAuthNote(AuthenticationManager.FORCED_REAUTHENTICATION, "true")
        }
        verify(exactly = 1) { context.setForwardedInfoMessage(Messages.REAUTHENTICATE) }
        verify(exactly = 1) { context.attempted() }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - forked flow attempts`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val session = mockk<KeycloakSession>()
        val realm = mockk<RealmModel>()
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val protocol = mockk<LoginProtocol>()
        val authResult = mockk<AuthenticationManager.AuthResult>()
        val userSession = mockk<UserSessionModel>()
        val user = mockk<UserModel>()

        every { context.session } returns session
        every { context.realm } returns realm
        every { context.authenticationSession } returns authSession
        every { authSession.protocol } returns "openid-connect"

        every { authResult.session() } returns userSession
        every { authResult.user() } returns user
        every { userSession.getNote(Constants.LOA_MAP) } returns "loa-map"

        every { session.getProvider(LoginProtocol::class.java, "openid-connect") } returns protocol
        every { protocol.requireReauthentication(userSession, authSession) } returns false
        every { context.user = user } just Runs

        mockkStatic(AuthenticationManager::class)
        every {
            AuthenticationManager.authenticateIdentityCookie(session, realm, true)
        } returns authResult

        mockkStatic(AuthenticatorUtil::class)
        every { AuthenticatorUtil.isForkedFlow(authSession) } returns true

        mockkConstructor(AcrStore::class)

        authenticator.authenticate(context)

        verify(exactly = 1) { context.attempted() }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - higher loa requested with kc action sets message and attempts`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val session = mockk<KeycloakSession>()
        val realm = mockk<RealmModel>()
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val protocol = mockk<LoginProtocol>()
        val authResult = mockk<AuthenticationManager.AuthResult>()
        val userSession = mockk<UserSessionModel>()
        val user = mockk<UserModel>()
        val topLevelFlow = mockk<AuthenticationFlowModel>()
        val executionModel = mockk<org.keycloak.models.AuthenticationExecutionModel>()

        every { context.session } returns session
        every { context.realm } returns realm
        every { context.authenticationSession } returns authSession
        every { context.topLevelFlow } returns topLevelFlow
        every { context.execution } returns executionModel
        every { topLevelFlow.id } returns "top-flow"
        every { executionModel.id } returns "exec-id"
        every { authSession.protocol } returns "openid-connect"
        every { authSession.getClientNote(Constants.KC_ACTION) } returns "some-action"

        every { authResult.session() } returns userSession
        every { authResult.user() } returns user
        every { userSession.getNote(Constants.LOA_MAP) } returns "loa-map"

        every { session.getProvider(LoginProtocol::class.java, "openid-connect") } returns protocol
        every { protocol.requireReauthentication(userSession, authSession) } returns false
        every { context.user = user } just Runs

        mockkStatic(AuthenticationManager::class)
        every {
            AuthenticationManager.authenticateIdentityCookie(session, realm, true)
        } returns authResult

        mockkStatic(AuthenticatorUtil::class)
        every { AuthenticatorUtil.isForkedFlow(authSession) } returns false

        mockkConstructor(AcrStore::class)
        every {
            anyConstructed<AcrStore>().getHighestAuthenticatedLevelFromPreviousAuthentication("top-flow")
        } returns 1
        every {
            anyConstructed<AcrStore>().getRequestedLevelOfAuthentication(topLevelFlow)
        } returns 2
        every {
            anyConstructed<AcrStore>().setLevelAuthenticatedToCurrentRequest(1)
        } just Runs

        mockkStatic(AuthenticatorUtils::class)
        every {
            AuthenticatorUtils.updateCompletedExecutions(authSession, userSession, "exec-id")
        } just Runs

        authenticator.authenticate(context)

        verify(exactly = 1) {
            AuthenticatorUtils.updateCompletedExecutions(authSession, userSession, "exec-id")
        }
        verify(exactly = 1) {
            anyConstructed<AcrStore>().setLevelAuthenticatedToCurrentRequest(1)
        }
        verify(exactly = 1) { context.setForwardedInfoMessage(Messages.AUTHENTICATE_STRONG) }
        verify(exactly = 1) { context.attempted() }
        verify(exactly = 0) { context.success() }
    }

    @Test
    fun `authenticate - non organization context attaches session and succeeds`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val session = mockk<KeycloakSession>()
        val realm = mockk<RealmModel>()
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val protocol = mockk<LoginProtocol>()
        val authResult = mockk<AuthenticationManager.AuthResult>()
        val userSession = mockk<UserSessionModel>()
        val user = mockk<UserModel>()
        val topLevelFlow = mockk<AuthenticationFlowModel>()
        val executionModel = mockk<org.keycloak.models.AuthenticationExecutionModel>()

        every { context.session } returns session
        every { context.realm } returns realm
        every { context.authenticationSession } returns authSession
        every { context.topLevelFlow } returns topLevelFlow
        every { context.execution } returns executionModel
        every { topLevelFlow.id } returns "top-flow"
        every { executionModel.id } returns "exec-id"
        every { authSession.protocol } returns "openid-connect"

        every { authResult.session() } returns userSession
        every { authResult.user() } returns user
        every { userSession.getNote(Constants.LOA_MAP) } returns "loa-map"

        every { session.getProvider(LoginProtocol::class.java, "openid-connect") } returns protocol
        every { protocol.requireReauthentication(userSession, authSession) } returns false
        every { context.user = user } just Runs
        every { context.attachUserSession(userSession) } just Runs

        mockkStatic(AuthenticationManager::class)
        every {
            AuthenticationManager.authenticateIdentityCookie(session, realm, true)
        } returns authResult

        mockkStatic(AuthenticatorUtil::class)
        every { AuthenticatorUtil.isForkedFlow(authSession) } returns false

        mockkConstructor(AcrStore::class)
        every {
            anyConstructed<AcrStore>().getHighestAuthenticatedLevelFromPreviousAuthentication("top-flow")
        } returns 2
        every {
            anyConstructed<AcrStore>().getRequestedLevelOfAuthentication(topLevelFlow)
        } returns 2
        every {
            anyConstructed<AcrStore>().setLevelAuthenticatedToCurrentRequest(2)
        } just Runs

        mockkStatic(AuthenticatorUtils::class)
        every {
            AuthenticatorUtils.updateCompletedExecutions(authSession, userSession, "exec-id")
        } just Runs

        mockkStatic(Organizations::class)
        mockkStatic(OrganizationScope::class)
        every { Organizations.isEnabledAndOrganizationsPresent(session) } returns false
        every { OrganizationScope.valueOfScope(session) } returns null

        authenticator.authenticate(context)

        verify(exactly = 1) { authSession.setAuthNote(AuthenticationManager.SSO_AUTH, "true") }
        verify(exactly = 1) { context.attachUserSession(userSession) }
        verify(exactly = 1) { context.success() }
        verify(exactly = 0) { context.attempted() }
    }

    @Test
    fun `authenticate - organization context restored succeeds`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val session = mockk<KeycloakSession>()
        val realm = mockk<RealmModel>()
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val protocol = mockk<LoginProtocol>()
        val authResult = mockk<AuthenticationManager.AuthResult>()
        val userSession = mockk<UserSessionModel>()
        val user = mockk<UserModel>()
        val topLevelFlow = mockk<AuthenticationFlowModel>()
        val executionModel = mockk<org.keycloak.models.AuthenticationExecutionModel>()
        val orgProvider = mockk<OrganizationProvider>()
        val org = mockOrg()
        val keycloakContext = mockk<KeycloakContext>(relaxed = true)

        every { context.session } returns session
        every { context.realm } returns realm
        every { context.authenticationSession } returns authSession
        every { context.topLevelFlow } returns topLevelFlow
        every { context.execution } returns executionModel
        every { topLevelFlow.id } returns "top-flow"
        every { executionModel.id } returns "exec-id"
        every { authSession.protocol } returns "openid-connect"

        every { authResult.session() } returns userSession
        every { authResult.user() } returns user
        every { userSession.getNote(Constants.LOA_MAP) } returns "loa-map"
        every { userSession.getNote(OrganizationModel.ORGANIZATION_ATTRIBUTE) } returns "org-id"

        every { session.getProvider(LoginProtocol::class.java, "openid-connect") } returns protocol
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
        every { session.context } returns keycloakContext
        every { orgProvider.getById("org-id") } returns org

        every { protocol.requireReauthentication(userSession, authSession) } returns false
        every { context.user = user } just Runs
        every { context.attachUserSession(userSession) } just Runs

        mockkStatic(AuthenticationManager::class)
        every {
            AuthenticationManager.authenticateIdentityCookie(session, realm, true)
        } returns authResult

        mockkStatic(AuthenticatorUtil::class)
        every { AuthenticatorUtil.isForkedFlow(authSession) } returns false

        mockkConstructor(AcrStore::class)
        every {
            anyConstructed<AcrStore>().getHighestAuthenticatedLevelFromPreviousAuthentication("top-flow")
        } returns 2
        every {
            anyConstructed<AcrStore>().getRequestedLevelOfAuthentication(topLevelFlow)
        } returns 2
        every {
            anyConstructed<AcrStore>().setLevelAuthenticatedToCurrentRequest(2)
        } just Runs

        mockkStatic(AuthenticatorUtils::class)
        every {
            AuthenticatorUtils.updateCompletedExecutions(authSession, userSession, "exec-id")
        } just Runs

        mockkStatic(Organizations::class)
        mockkStatic(OrganizationScope::class)
        every { Organizations.isEnabledAndOrganizationsPresent(session) } returns true
        every { OrganizationScope.valueOfScope(session) } returns mockk()

        authenticator.authenticate(context)

        verify(exactly = 1) {
            authSession.setAuthNote(OrganizationModel.ORGANIZATION_ATTRIBUTE, "id-1")
        }
        verify(exactly = 1) {
            authSession.setClientNote(OrganizationModel.ORGANIZATION_ATTRIBUTE, "id-1")
        }
        verify(exactly = 1) {
            keycloakContext.organization = org
        }
        verify(exactly = 1) { context.success() }
    }

    @Test
    fun `authenticate - organization context but no org in session attempts`() {
        val context = mockk<AuthenticationFlowContext>(relaxed = true)
        val session = mockk<KeycloakSession>()
        val realm = mockk<RealmModel>()
        val authSession = mockk<AuthenticationSessionModel>(relaxed = true)
        val protocol = mockk<LoginProtocol>()
        val authResult = mockk<AuthenticationManager.AuthResult>()
        val userSession = mockk<UserSessionModel>()
        val user = mockk<UserModel>()
        val topLevelFlow = mockk<AuthenticationFlowModel>()
        val executionModel = mockk<org.keycloak.models.AuthenticationExecutionModel>()
        val orgProvider = mockk<OrganizationProvider>()

        every { context.session } returns session
        every { context.realm } returns realm
        every { context.authenticationSession } returns authSession
        every { context.topLevelFlow } returns topLevelFlow
        every { context.execution } returns executionModel
        every { topLevelFlow.id } returns "top-flow"
        every { executionModel.id } returns "exec-id"
        every { authSession.protocol } returns "openid-connect"

        every { authResult.session() } returns userSession
        every { authResult.user() } returns user
        every { userSession.getNote(Constants.LOA_MAP) } returns "loa-map"
        every { userSession.getNote(OrganizationModel.ORGANIZATION_ATTRIBUTE) } returns null

        every { session.getProvider(LoginProtocol::class.java, "openid-connect") } returns protocol
        every { session.getProvider(OrganizationProvider::class.java) } returns orgProvider
        every { protocol.requireReauthentication(userSession, authSession) } returns false
        every { context.user = user } just Runs
        every { context.attachUserSession(userSession) } just Runs

        mockkStatic(AuthenticationManager::class)
        every {
            AuthenticationManager.authenticateIdentityCookie(session, realm, true)
        } returns authResult

        mockkStatic(AuthenticatorUtil::class)
        every { AuthenticatorUtil.isForkedFlow(authSession) } returns false

        mockkConstructor(AcrStore::class)
        every {
            anyConstructed<AcrStore>().getHighestAuthenticatedLevelFromPreviousAuthentication("top-flow")
        } returns 2
        every {
            anyConstructed<AcrStore>().getRequestedLevelOfAuthentication(topLevelFlow)
        } returns 2
        every {
            anyConstructed<AcrStore>().setLevelAuthenticatedToCurrentRequest(2)
        } just Runs

        mockkStatic(AuthenticatorUtils::class)
        every {
            AuthenticatorUtils.updateCompletedExecutions(authSession, userSession, "exec-id")
        } just Runs

        mockkStatic(Organizations::class)
        mockkStatic(OrganizationScope::class)
        every { Organizations.isEnabledAndOrganizationsPresent(session) } returns true
        every { OrganizationScope.valueOfScope(session) } returns mockk()

        authenticator.authenticate(context)

        verify(exactly = 1) { context.attempted() }
        verify(exactly = 0) { context.success() }
    }
}
