package no.novari.authenticator.org

import no.novari.provider.ClientOrgAccessProvider
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

        val orgId = authResult.session().getNote(OrganizationModel.ORGANIZATION_ATTRIBUTE)
        if (orgId == null) {
            logger.debug("Could not resolve organization id from user session")
            context.attempted()
            return
        }

        val clientOrgAccessProvider = context.session.getProvider(ClientOrgAccessProvider::class.java)
        val organization = clientOrgAccessProvider.getOrganizationModel(orgId)
        if (organization == null) {
            logger.debug("Could not resolve organization from user session")
            context.attempted()
            return
        }

        if (clientOrgAccessProvider.isOrgAllowed(context, organization)) {
            logger.debugf(
                "Access granted for organization '%s' (%s) for client %s",
                organization.alias,
                organization.id,
                context.authenticationSession.client.clientId,
            )

            restoreOrganizationContext(context, organization)

            context.success()
            return
        }

        logger.debugf(
            "Organization scope detected, but access could not be granted for organization %s for client %s",
            organization.alias,
            context.authenticationSession.client.clientId,
        )

        context.attempted()
    }

    private fun isOrganizationContext(context: AuthenticationFlowContext): Boolean {
        val session = context.session
        return Organizations.isEnabledAndOrganizationsPresent(session) &&
            OrganizationScope.valueOfScope(session) != null
    }

    private fun restoreOrganizationContext(
        context: AuthenticationFlowContext,
        organization: OrganizationModel,
    ) {
        val authSession: AuthenticationSessionModel = context.authenticationSession

        authSession.setAuthNote(OrganizationModel.ORGANIZATION_ATTRIBUTE, organization.id)
        authSession.setClientNote(OrganizationModel.ORGANIZATION_ATTRIBUTE, organization.id)
        context.session.context.organization = organization

        logger.debugf(
            "Restored organization '%s' (%s) from user session",
            organization.alias,
            organization.id,
        )
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
}
