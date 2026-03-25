package no.fintlabs.authenticator.org

import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.AuthenticatorUtil
import org.keycloak.authentication.authenticators.util.AcrStore
import org.keycloak.authentication.authenticators.util.AuthenticatorUtils
import org.keycloak.models.Constants
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.keycloak.organization.OrganizationProvider
import org.keycloak.organization.protocol.mappers.oidc.OrganizationScope
import org.keycloak.organization.utils.Organizations
import org.keycloak.protocol.LoginProtocol
import org.keycloak.services.managers.AuthenticationManager
import org.keycloak.services.messages.Messages
import org.keycloak.sessions.AuthenticationSessionModel

class OrgCookieAuthenticator : Authenticator {
    private val logger: Logger = Logger.getLogger(OrgCookieAuthenticator::class.java)

    override fun requiresUser(): Boolean = false

    override fun authenticate(context: AuthenticationFlowContext) {
        val authResult =
            AuthenticationManager.authenticateIdentityCookie(
                context.session,
                context.realm,
                true,
            )

        if (authResult == null) {
            context.attempted()
            return
        }

        val authSession = context.authenticationSession
        val protocol = context.session.getProvider(LoginProtocol::class.java, authSession.protocol)

        authSession.setAuthNote(Constants.LOA_MAP, authResult.session().getNote(Constants.LOA_MAP))
        context.user = authResult.user()

        val acrStore = AcrStore(context.session, authSession)

        if (protocol.requireReauthentication(authResult.session(), authSession)) {
            acrStore.setLevelAuthenticatedToCurrentRequest(Constants.NO_LOA)
            authSession.setAuthNote(AuthenticationManager.FORCED_REAUTHENTICATION, "true")
            context.setForwardedInfoMessage(Messages.REAUTHENTICATE)
            context.attempted()
            return
        }

        if (AuthenticatorUtil.isForkedFlow(authSession)) {
            context.attempted()
            return
        }

        val topLevelFlowId = context.topLevelFlow.id
        val previouslyAuthenticatedLevel =
            acrStore.getHighestAuthenticatedLevelFromPreviousAuthentication(topLevelFlowId)

        AuthenticatorUtils.updateCompletedExecutions(
            authSession,
            authResult.session(),
            context.execution.id,
        )

        if (acrStore.getRequestedLevelOfAuthentication(context.topLevelFlow) > previouslyAuthenticatedLevel) {
            acrStore.setLevelAuthenticatedToCurrentRequest(previouslyAuthenticatedLevel)

            if (authSession.getClientNote(Constants.KC_ACTION) != null) {
                context.setForwardedInfoMessage(Messages.AUTHENTICATE_STRONG)
            }

            context.attempted()
            return
        }

        acrStore.setLevelAuthenticatedToCurrentRequest(previouslyAuthenticatedLevel)
        authSession.setAuthNote(AuthenticationManager.SSO_AUTH, "true")
        context.attachUserSession(authResult.session())

        if (!isOrganizationContext(context)) {
            context.success()
            return
        }

        if (restoreOrganizationContext(context, authResult.session())) {
            context.success()
        } else {
            logger.debug("Organization scope detected, but no organization could be restored from user session")
            context.attempted()
        }
    }

    override fun action(context: AuthenticationFlowContext) {
        // no-op
    }

    override fun configuredFor(
        session: KeycloakSession,
        realm: RealmModel,
        user: UserModel,
    ): Boolean = true

    override fun setRequiredActions(
        session: KeycloakSession,
        realm: RealmModel,
        user: UserModel,
    ) {
        // no-op
    }

    override fun close() {
        // no-op
    }

    private fun isOrganizationContext(context: AuthenticationFlowContext): Boolean {
        val session = context.session
        return Organizations.isEnabledAndOrganizationsPresent(session) &&
            OrganizationScope.valueOfScope(session) != null
    }

    private fun restoreOrganizationContext(
        context: AuthenticationFlowContext,
        userSession: org.keycloak.models.UserSessionModel,
    ): Boolean {
        val orgId =
            userSession.getNote(OrganizationModel.ORGANIZATION_ATTRIBUTE)
                ?: return false

        val provider = context.session.getProvider(OrganizationProvider::class.java)
        val organization = provider.getById(orgId) ?: return false

        if (!organization.isEnabled) {
            logger.debugf(
                "Stored organization %s exists but is disabled",
                orgId,
            )
            return false
        }

        val authSession: AuthenticationSessionModel = context.authenticationSession

        authSession.setAuthNote(OrganizationModel.ORGANIZATION_ATTRIBUTE, organization.id)
        authSession.setClientNote(OrganizationModel.ORGANIZATION_ATTRIBUTE, organization.id)
        context.session.context.organization = organization

        logger.debugf(
            "Restored organization '%s' (%s) from user session",
            organization.alias,
            organization.id,
        )

        return true
    }
}
