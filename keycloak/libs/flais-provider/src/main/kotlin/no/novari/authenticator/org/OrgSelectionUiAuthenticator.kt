package no.novari.authenticator.org

import no.novari.attributes.OrgAttribute
import no.novari.dtos.OrgDto
import no.novari.flow.AuthenticationErrorHandler.failure
import no.novari.provider.ClientOrgAccessProvider
import no.novari.utils.OrgRememberCookie
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.Authenticator
import org.keycloak.events.Details
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel

class OrgSelectionUiAuthenticator : Authenticator {
    private val logger: Logger = Logger.getLogger(OrgSelectionUiAuthenticator::class.java)

    override fun authenticate(context: AuthenticationFlowContext) {
        val clientOrgAccessProvider = context.session.getProvider(ClientOrgAccessProvider::class.java)
        val organizations = clientOrgAccessProvider.getAllowedOrgs(context)
        logger.debugf(
            "Found allowed organizations. client=%s count=%d aliases=%s",
            context.authenticationSession.client.clientId,
            organizations.size,
            organizations.map { it.alias },
        )

        context.authenticationSession.getAuthNote(Details.ORG_ID)?.let {
            organizations.find { org -> org.id == it }?.let { org ->
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

        val clientOrgAccessProvider = context.session.getProvider(ClientOrgAccessProvider::class.java)
        val organizations = clientOrgAccessProvider.getAllowedOrgs(context)
        if (selectedOrg.isNullOrEmpty()) {
            createOrgSelectForm(
                context,
                organizations,
                "You must select an organization to continue",
            )
            return
        }

        organizations.find { it.alias == selectedOrg }?.let { org ->
            logger.infof(
                "Organization selected. client=%s org=%s",
                context.authenticationSession.client.clientId,
                org.alias,
            )
            context.authenticationSession.setAuthNote(Details.ORG_ID, org.id)

            context.success()
            return
        }

        logger.warnf(
            "Invalid organization selection. client=%s selectedOrg=%s",
            context.authenticationSession.client.clientId,
            selectedOrg,
        )
        context.failure(
            "Selected organization does not have permission to login to this application",
        )
    }

    private fun createOrgSelectForm(
        context: AuthenticationFlowContext,
        organizations: List<OrganizationModel>,
        error: String? = null,
    ) {
        val defaultSelectedOrg = getDefaultSelectedOrganization(context, organizations)
        logger.debugf(
            "Resolved default organization selection. client=%s org=%s",
            context.authenticationSession.client.clientId,
            defaultSelectedOrg,
        )
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
                                org.attributes[OrgAttribute.ORGANIZATION_LOGO]
                                    ?.first()
                            OrgDto(org.alias, org.name, logo)
                        }
                    setAttribute("organizations", orgDto)
                    setAttribute("defaultSelectedOrg", defaultSelectedOrg)
                }.createForm("flais-org-selector.ftl")
        context.challenge(form)
    }

    private fun getDefaultSelectedOrganization(
        context: AuthenticationFlowContext,
        organizations: List<OrganizationModel>,
    ): String {
        if (
            context.authenticatorConfig
                ?.config
                ?.get(OrgSelectionUiAuthenticatorConfig.USE_REMEMBERED_ORGANIZATION)
                ?.toBooleanStrictOrNull() == false
        ) {
            return ""
        }

        val rememberedOrg = OrgRememberCookie.read(context) ?: return ""
        return organizations.find { it.alias == rememberedOrg }?.alias ?: ""
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
