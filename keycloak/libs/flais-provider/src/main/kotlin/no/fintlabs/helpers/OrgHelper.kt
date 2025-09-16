package no.fintlabs.helpers

import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.models.OrganizationModel

object OrgHelper {
    const val ORG_LOGO_ATTRIBUTE: String = "attributes.logo"

    private val logger: Logger = Logger.getLogger(OrgHelper::class.java)

    fun filterOrgs(
            context: AuthenticationFlowContext,
            attributes: Map<String, String>,
    ): List<OrganizationModel> {
        val session = context.session

        val orgProvider =
                session?.getProvider(
                        org.keycloak.organization.OrganizationProvider::class.java,
                )

        val organizations = orgProvider?.allStream?.toList() ?: emptyList()
        logger.infof(
                "Loaded organizations: %s",
                organizations.joinToString(separator = ", ") { it.alias }
        )

        val whitelistedOrgs: List<String> =
                attributes["attributes.permission.whitelisted.organizations"]?.split(",")
                        ?: emptyList()
        logger.infof("Loaded whitelisted orgs: %s", whitelistedOrgs)

        val blacklistedOrgs: List<String> =
                attributes["attributes.permission.blacklisted.organizations"]?.split(",")
                        ?: emptyList()
        logger.infof("Loaded blacklisted orgs: %s", blacklistedOrgs)

        val filteredOrgs =
                organizations.filter { org ->
                    (whitelistedOrgs.isEmpty() || whitelistedOrgs.contains(org.alias)) &&
                            (blacklistedOrgs.isEmpty() || !blacklistedOrgs.contains(org.alias))
                }

        logger.infof(
                "Returning filtered organizations: %s",
                filteredOrgs.joinToString(separator = ", ") { it.alias }
        )

        return filteredOrgs
    }
}
