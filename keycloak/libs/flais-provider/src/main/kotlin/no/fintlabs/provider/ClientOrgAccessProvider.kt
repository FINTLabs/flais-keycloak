package no.fintlabs.provider

import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.models.OrganizationModel
import org.keycloak.provider.Provider

interface ClientOrgAccessProvider : Provider {
    fun getAllowedOrgs(context: AuthenticationFlowContext): List<OrganizationModel>

    fun isOrgAllowed(
        context: AuthenticationFlowContext,
        organization: OrganizationModel,
    ): Boolean

    fun getOrganizationModel(orgId: String): OrganizationModel?
}
