package no.novari.provider

import no.novari.utils.ClientPermissionAttributes.getBlacklistedOrganizations
import no.novari.utils.ClientPermissionAttributes.getWhitelistedOrganizations
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.organization.OrganizationProvider

class DefaultClientOrgAccessProvider(
    private val session: KeycloakSession,
) : ClientOrgAccessProvider {
    override fun getAllowedOrgs(context: AuthenticationFlowContext): List<OrganizationModel> {
        val client = context.authenticationSession.client
        val orgProvider = session.getProvider(OrganizationProvider::class.java)

        val whitelist = getWhitelistedOrganizations(client)
        val blacklist = getBlacklistedOrganizations(client)

        return orgProvider.allStream
            .filter {
                (whitelist.isEmpty() || whitelist.contains(it.alias)) &&
                    !blacklist.contains(it.alias) &&
                    it.isEnabled &&
                    it.identityProviders.anyMatch { idp -> idp.isEnabled }
            }.toList()
    }

    override fun isOrgAllowed(
        context: AuthenticationFlowContext,
        organization: OrganizationModel,
    ): Boolean {
        if (!organization.isEnabled) return false

        val client = context.authenticationSession.client
        val whitelist = getWhitelistedOrganizations(client)
        val blacklist = getBlacklistedOrganizations(client)

        return (whitelist.isEmpty() || whitelist.contains(organization.alias)) &&
            !blacklist.contains(organization.alias)
    }

    override fun getOrganizationModel(orgId: String): OrganizationModel? =
        session.getProvider(OrganizationProvider::class.java).getById(orgId)

    override fun close() = Unit
}
