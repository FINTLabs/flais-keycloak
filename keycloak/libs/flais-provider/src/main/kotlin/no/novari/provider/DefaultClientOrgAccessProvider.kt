package no.novari.provider

import no.novari.utils.ClientPermissionAttributes.getBlacklistedOrganizations
import no.novari.utils.ClientPermissionAttributes.getWhitelistedOrganizations
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.organization.OrganizationProvider

class DefaultClientOrgAccessProvider(
    private val session: KeycloakSession,
) : ClientOrgAccessProvider {
    private val logger: Logger = Logger.getLogger(DefaultClientOrgAccessProvider::class.java)

    override fun getAllowedOrgs(context: AuthenticationFlowContext): List<OrganizationModel> {
        val client = context.authenticationSession.client
        val orgProvider = session.getProvider(OrganizationProvider::class.java)

        val whitelist = getWhitelistedOrganizations(client)
        val blacklist = getBlacklistedOrganizations(client)

        val allowed =
            orgProvider.allStream
                .filter {
                    (whitelist.isEmpty() || whitelist.contains(it.alias)) &&
                        !blacklist.contains(it.alias) &&
                        it.isEnabled &&
                        it.identityProviders.anyMatch { idp -> idp.isEnabled }
                }.toList()

        logger.debugf(
            "Resolved allowed organizations for client. client=%s whitelistCount=%d blacklistCount=%d allowedCount=%d",
            client.clientId,
            whitelist.size,
            blacklist.size,
            allowed.size,
        )

        return allowed
    }

    override fun isOrgAllowed(
        context: AuthenticationFlowContext,
        organization: OrganizationModel,
    ): Boolean {
        if (!organization.isEnabled) return false

        val client = context.authenticationSession.client
        val whitelist = getWhitelistedOrganizations(client)
        val blacklist = getBlacklistedOrganizations(client)

        val allowed =
            (whitelist.isEmpty() || whitelist.contains(organization.alias)) &&
                !blacklist.contains(organization.alias)

        logger.debugf(
            "Evaluated organization access. client=%s org=%s enabled=%s allowed=%s",
            client.clientId,
            organization.alias,
            organization.isEnabled,
            allowed,
        )

        return allowed
    }

    override fun getOrganizationModel(orgId: String): OrganizationModel? =
        session.getProvider(OrganizationProvider::class.java).getById(orgId)

    override fun close() = Unit
}
