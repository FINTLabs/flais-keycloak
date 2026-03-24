package no.fintlabs.service

import no.fintlabs.attributes.ClientPermissionAttribute
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.models.OrganizationModel
import org.keycloak.organization.OrganizationProvider

class ClientOrgAccessService {
    fun getAllowedOrganizations(context: AuthenticationFlowContext): List<OrganizationModel> {
        val client = context.authenticationSession.client
        val orgProvider =
            context.session.getProvider(
                OrganizationProvider::class.java,
            )

        val whitelist = getWhitelist(client.attributes)
        val blacklist = getBlacklist(client.attributes)

        return orgProvider.allStream
            .filter {
                (whitelist.isEmpty() || whitelist.contains(it.alias)) &&
                    !blacklist.contains(it.alias) &&
                    it.identityProviders.anyMatch { idp -> idp.isEnabled }
            }.toList()
    }

    fun isOrgAllowed(
        context: AuthenticationFlowContext,
        orgAlias: String,
    ): Boolean {
        val client = context.authenticationSession.client
        val whitelist = getWhitelist(client.attributes)
        val blacklist = getBlacklist(client.attributes)

        return (whitelist.isEmpty() || whitelist.contains(orgAlias)) &&
            !blacklist.contains(orgAlias)
    }

    private fun getWhitelist(attributes: Map<String, String>): List<String> =
        attributes[ClientPermissionAttribute.PERMISSION_WHITELISTED_ORGANIZATIONS]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    private fun getBlacklist(attributes: Map<String, String>): List<String> =
        attributes[ClientPermissionAttribute.PERMISSION_BLACKLISTED_ORGANIZATIONS]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
}
