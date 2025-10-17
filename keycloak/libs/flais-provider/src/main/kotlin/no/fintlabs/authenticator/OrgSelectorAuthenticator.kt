package no.fintlabs.authenticator

import no.fintlabs.dtos.OrgDto
import no.fintlabs.helpers.ClientHelper.CLIENT_BLACKLIST_ORGANIZATIONS_ATTRIBUTE
import no.fintlabs.helpers.ClientHelper.CLIENT_WHITELIST_ORGANIZATIONS_ATTRIBUTE
import no.fintlabs.helpers.FailureHelper.failure
import no.fintlabs.helpers.OrgHelper
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.Authenticator
import org.keycloak.events.Details
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import kotlin.collections.first

class OrgSelectorAuthenticator : Authenticator {
    private val logger: Logger = Logger.getLogger(OrgSelectorAuthenticator::class.java)

    override fun authenticate(context: AuthenticationFlowContext) {
        val organizations = getOrganizations(context)
        logger.debugf("Found orgs: %s", organizations)

        context.authenticationSession.getAuthNote(Details.ORG_ID)?.let {
            organizations.find { org -> org.alias == it }?.let { org ->
                logger.debugf("Already selected org: %s", org.alias)
                context.success()
                return
            }
        }

        if (organizations.count() == 1) {
            logger.debugf(
                "Only one org configured, continuing with org: %s",
                organizations.first().alias,
            )
            context.authenticationSession.setAuthNote(Details.ORG_ID, organizations.first().id)
            context.success()
            return
        }

        createOrgSelectForm(context, organizations)
    }

    override fun action(context: AuthenticationFlowContext) {
        val formData = context.httpRequest.decodedFormParameters

        val selectedOrg = formData.getFirst("selected_org")
        logger.infof("Selected Organization: %s", selectedOrg)

        val organizations = getOrganizations(context)
        if (selectedOrg.isNullOrEmpty()) {
            createOrgSelectForm(
                context,
                organizations,
                "You must select a organization to continue",
            )
            return
        }

        organizations.find { it.alias == selectedOrg }?.let { org ->
            context.authenticationSession.setAuthNote(Details.ORG_ID, org.id)

            val rememberMe = formData.getFirst(Details.REMEMBER_ME) ?: ""
            if (rememberMe == "on") {
                context.authenticationSession.setAuthNote(Details.REMEMBER_ME, "true")
            }
            context.success()
            return
        }

        context.failure(
            "Selected organization does not have permission to login to this application",
        )
    }

    private fun createOrgSelectForm(
        context: AuthenticationFlowContext,
        organizations: List<OrganizationModel>,
        error: String? = null,
    ) {
        val form =
            context
                .form()
                .apply {
                    if (!error.isNullOrEmpty()) {
                        setError(error)
                    }
                    val orgDto =
                        organizations.map { org ->
                            val logo =
                                org.attributes[OrgHelper.ORG_LOGO_ATTRIBUTE]
                                    ?.first()
                            OrgDto(org.alias, org.name, logo)
                        }
                    setAttribute("organizations", orgDto)
                }.createForm("flais-org-selector.ftl")
        context.challenge(form)
    }

    private fun getOrganizations(context: AuthenticationFlowContext): List<OrganizationModel> {
        val clientAttributes = context.authenticationSession.client.attributes
        val whitelisted =
            clientAttributes[CLIENT_WHITELIST_ORGANIZATIONS_ATTRIBUTE]?.split(",")
                ?: emptyList()
        val blacklisted =
            clientAttributes[CLIENT_BLACKLIST_ORGANIZATIONS_ATTRIBUTE]?.split(",")
                ?: emptyList()

        val orgProvider =
            context.session.getProvider(
                org.keycloak.organization.OrganizationProvider::class.java,
            )

        return orgProvider
            .allStream
            .filter {
                (whitelisted.isEmpty() || whitelisted.contains(it.alias)) &&
                    (blacklisted.isEmpty() || !blacklisted.contains(it.alias)) &&
                    it.identityProviders.filter { idp -> idp.isEnabled }.count() > 0
            }.toList()
    }

    override fun requiresUser(): Boolean = false

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
        // No required actions needed
    }

    override fun close() {
        // No resources to close
    }
}
