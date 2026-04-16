package no.fintlabs.authenticator.org

import no.fintlabs.flow.AuthenticationErrorHandler.failure
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.Authenticator
import org.keycloak.events.Details
import org.keycloak.models.KeycloakSession
import org.keycloak.models.OrganizationModel
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.keycloak.organization.OrganizationProvider

class OrgSessionCommitAuthenticator : Authenticator {
    private val logger: Logger = Logger.getLogger(OrgSessionCommitAuthenticator::class.java)

    override fun authenticate(context: AuthenticationFlowContext) {
        val authSession = context.authenticationSession
        val selectedOrgId = authSession.getAuthNote(Details.ORG_ID)

        if (selectedOrgId.isNullOrBlank()) {
            context.failure("No organization selected")
            return
        }

        val provider = context.session.getProvider(OrganizationProvider::class.java)
        val organization = provider.getById(selectedOrgId)

        if (organization == null || !organization.isEnabled) {
            context.failure("Selected organization is invalid")
            return
        }

        commitOrganization(context, organization)
        context.success()
    }

    override fun action(context: AuthenticationFlowContext) {
        // No-op: this authenticator performs only server-side validation
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
    }

    override fun close() {
    }

    private fun commitOrganization(
        context: AuthenticationFlowContext,
        organization: OrganizationModel,
    ) {
        val authSession = context.authenticationSession
        val session = context.session

        authSession.setAuthNote(OrganizationModel.ORGANIZATION_ATTRIBUTE, organization.id)
        authSession.setClientNote(OrganizationModel.ORGANIZATION_ATTRIBUTE, organization.id)
        session.context.organization = organization

        authSession.setUserSessionNote(OrganizationModel.ORGANIZATION_ATTRIBUTE, organization.id)

        logger.debugf(
            "Committed organization '%s' (%s)",
            organization.alias,
            organization.id,
        )
    }
}
