package no.fintlabs.authenticator.client

import no.fintlabs.flow.AuthenticationErrorHandler.failure
import no.fintlabs.service.ClientOrgAccessService
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.Authenticator
import org.keycloak.authentication.authenticators.broker.util.PostBrokerLoginConstants
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.keycloak.organization.OrganizationProvider

class ClientOrgAccessAuthenticator(
    val clientOrgAccessService: ClientOrgAccessService,
) : Authenticator {
    private val logger: Logger =
        Logger.getLogger(ClientOrgAccessAuthenticator::class.java)

    override fun authenticate(context: AuthenticationFlowContext) {
        when (getMode(context)) {
            ClientOrgAccessAuthenticatorFactory.MODE_POST_LOGIN -> authenticatePostLogin(context)
            ClientOrgAccessAuthenticatorFactory.MODE_BROWSER -> authenticateBrowser(context)
            else -> context.failure("Invalid authenticator configuration")
        }
    }

    private fun getMode(context: AuthenticationFlowContext): String =
        context.authenticatorConfig
            ?.config
            ?.get(ClientOrgAccessAuthenticatorFactory.CONFIG_MODE)
            ?: ClientOrgAccessAuthenticatorFactory.MODE_POST_LOGIN

    private fun authenticatePostLogin(context: AuthenticationFlowContext) {
        val client = context.authenticationSession.client

        val serializedCtx =
            SerializedBrokeredIdentityContext.readFromAuthenticationSession(
                context.authenticationSession,
                PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT,
            )

        if (serializedCtx == null) {
            logger.warnf(
                "Access denied. Missing brokered identity context for client=%s",
                client.clientId,
            )
            context.failure("Could not determine identity provider for this login")
            return
        }

        val idpAlias = serializedCtx.identityProviderId
        if (idpAlias.isNullOrBlank()) {
            logger.warnf(
                "Access denied. Missing identity provider alias for client=%s",
                client.clientId,
            )
            context.failure("Could not determine identity provider for this login")
            return
        }

        val orgAlias = resolveOrgAliasForIdp(context, idpAlias)
        if (orgAlias.isNullOrBlank()) {
            logger.warnf(
                "Access denied. Could not determine org from idp=%s for client=%s",
                idpAlias,
                client.clientId,
            )
            context.failure("Could not determine organization for this login")
            return
        }

        if (!clientOrgAccessService.isOrgAllowed(context, orgAlias)) {
            logger.warnf(
                "Access denied. client=%s, idp=%s, org=%s",
                client.clientId,
                idpAlias,
                orgAlias,
            )
            context.failure("Your organization does not have access to this application")
            return
        }

        logger.infof(
            "Access granted. client=%s, idp=%s, org=%s",
            client.clientId,
            idpAlias,
            orgAlias,
        )
        context.success()
    }

    private fun authenticateBrowser(context: AuthenticationFlowContext) {
        val client = context.authenticationSession.client
        val orgAlias = resolveCurrentOrgAlias(context)

        if (orgAlias.isNullOrBlank()) {
            logger.debugf(
                "No resolved organization found for client=%s. Continuing browser flow.",
                client.clientId,
            )
            context.attempted()
            return
        }

        if (!clientOrgAccessService.isOrgAllowed(context, orgAlias)) {
            logger.warnf(
                "Access denied in browser flow. client=%s, org=%s",
                client.clientId,
                orgAlias,
            )
            context.failure("Your organization does not have access to this application")
            return
        }

        logger.infof(
            "Browser access granted. client=%s, org=%s",
            client.clientId,
            orgAlias,
        )
        context.success()
    }

    private fun resolveOrgAliasForIdp(
        context: AuthenticationFlowContext,
        idpAlias: String,
    ): String? {
        val orgProvider = context.session.getProvider(OrganizationProvider::class.java)

        return orgProvider.allStream
            .filter { org -> org.identityProviders.anyMatch { idp -> idp.alias == idpAlias } }
            .findFirst()
            .orElse(null)
            ?.alias
    }

    private fun resolveCurrentOrgAlias(context: AuthenticationFlowContext): String? {
        val authSession = context.authenticationSession
        val session = context.session

        session.context.organization?.let { return it.alias }

        val orgId =
            authSession.getAuthNote(OrganizationModel.ORGANIZATION_ATTRIBUTE)
                ?: authSession.getClientNote(OrganizationModel.ORGANIZATION_ATTRIBUTE)

        if (!orgId.isNullOrBlank()) {
            val orgProvider = session.getProvider(OrganizationProvider::class.java)
            val org = orgProvider.getById(orgId)
            if (org != null && org.isEnabled) {
                return org.alias
            }
        }

        return null
    }

    override fun requiresUser(): Boolean = true

    override fun action(context: AuthenticationFlowContext) {
        // No-op: this authenticator performs only server-side validation
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
        // No required actions
    }

    override fun close() {
        // No resources to close
    }
}
