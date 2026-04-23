package no.novari.authenticator.client

import no.novari.flow.AuthenticationErrorHandler.failure
import no.novari.provider.ClientOrgAccessProvider
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

class ClientOrgAccessAuthenticator : Authenticator {
    private val logger: Logger =
        Logger.getLogger(ClientOrgAccessAuthenticator::class.java)

    override fun authenticate(context: AuthenticationFlowContext) {
        val client = context.authenticationSession.client

        val serializedCtx =
            SerializedBrokeredIdentityContext.readFromAuthenticationSession(
                context.authenticationSession,
                PostBrokerLoginConstants.PBL_BROKERED_IDENTITY_CONTEXT,
            )

        if (serializedCtx == null) {
            logger.debugf(
                "Access denied. Missing brokered identity context for client=%s",
                client.clientId,
            )
            context.failure("Could not determine identity provider for this login")
            return
        }

        // serializedCtx.identityProviderId returns the alias not Id
        val idpAlias = serializedCtx.identityProviderId
        if (idpAlias.isNullOrBlank()) {
            logger.debugf(
                "Access denied. Missing identity provider alias for client=%s",
                client.clientId,
            )
            context.failure("Could not determine identity provider for this login")
            return
        }

        val organization = resolveOrganizationModelForIdp(context, idpAlias)
        if (organization == null) {
            logger.debugf(
                "Access denied. Could not determine org from idp=%s for client=%s",
                idpAlias,
                client.clientId,
            )
            context.failure("Could not determine organization for this login")
            return
        }

        val clientOrgAccessProvider = context.session.getProvider(ClientOrgAccessProvider::class.java)
        if (!clientOrgAccessProvider.isOrgAllowed(context, organization)) {
            logger.debugf(
                "Access denied. client=%s, idp=%s, org=%s",
                client.clientId,
                idpAlias,
                organization.alias,
            )
            context.failure("Your organization does not have access to this application")
            return
        }

        logger.debugf(
            "Access granted. client=%s, idp=%s, org=%s",
            client.clientId,
            idpAlias,
            organization.alias,
        )

        context.success()
    }

    private fun resolveOrganizationModelForIdp(
        context: AuthenticationFlowContext,
        idpAlias: String,
    ): OrganizationModel? {
        val orgProvider = context.session.getProvider(OrganizationProvider::class.java)

        return orgProvider.allStream
            .filter { org -> org.identityProviders.anyMatch { idp -> idp.alias == idpAlias } }
            .findFirst()
            .orElse(null)
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
