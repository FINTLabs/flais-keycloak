package no.fintlabs.helpers

import jakarta.ws.rs.NotFoundException
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.models.IdentityProviderModel
import org.keycloak.models.RoleModel

object ClientHelper {
    const val CLIENT_BLACKLIST_ORGANIZATIONS_ATTRIBUTE: String =
        "permission.blacklisted.organizations"
    const val CLIENT_WHITELIST_ORGANIZATIONS_ATTRIBUTE: String =
        "permission.whitelisted.organizations"

    private val logger: Logger = Logger.getLogger(ClientHelper::class.java)

    fun getOrganizationsRole(context: AuthenticationFlowContext): RoleModel {
        val client = context.authenticationSession.client

        return client.getRole("organizations")
            ?: run { throw NotFoundException("client missing required role 'organizations'") }
    }

    fun hasAccessToIdp(
        context: AuthenticationFlowContext,
        idp: IdentityProviderModel,
    ): Boolean {
        try {
            val client = context.authenticationSession.client
            val clientAttributes = client.attributes
            val orgs = OrgHelper.filterOrgs(context, clientAttributes)

            return orgs.map { it.id }.contains(idp.organizationId)
        } catch (e: Exception) {
            logger.errorf(e.message)
            return false
        }
    }
}
