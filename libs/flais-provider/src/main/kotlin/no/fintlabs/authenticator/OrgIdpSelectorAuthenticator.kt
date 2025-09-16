package no.fintlabs.authenticator

import no.fintlabs.dtos.IdpDto
import no.fintlabs.helpers.FailureHelper.failure
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.Authenticator
import org.keycloak.events.Details
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel

class OrgIdpSelectorAuthenticator : Authenticator {
    private val logger: Logger = Logger.getLogger(OrgIdpSelectorAuthenticator::class.java)

    override fun authenticate(context: AuthenticationFlowContext) {
        val selectedOrg = context.authenticationSession.getAuthNote(Details.ORG_ID)
        if (selectedOrg == null) {
            context.failure("No organization selected")
            return
        }

        val idps =
                context.session
                        .identityProviders()
                        .allStream
                        .filter { idp -> idp.isEnabled && idp.organizationId == selectedOrg }
                        .toList()
        when (idps.size) {
            0 -> context.failure("The selected organization does not have any login options")
            1 -> {
                logger.debugf(
                        "Only one identity provider available, continuing with provider: %s",
                        idps.first().alias
                )
                context.authenticationSession.setAuthNote(
                        Details.IDENTITY_PROVIDER,
                        idps.first().internalId
                )
                context.success()
            }
            else -> {
                val idpsDto = idps.map { IdpDto(it.alias, it.displayName) }
                val form =
                        context.form()
                                .setAttribute("providers", idpsDto)
                                .createForm("flais-org-idp-selector.ftl")
                context.challenge(form)
            }
        }
    }

    override fun action(context: AuthenticationFlowContext) {
        val selectedOrg = context.authenticationSession.getAuthNote(Details.ORG_ID)

        val formParams = context.httpRequest.decodedFormParameters
        val selectedIdp = formParams.getFirst(Details.IDENTITY_PROVIDER)

        if (selectedIdp.isNullOrEmpty()) {
            context.failure("No identity provider selected")
            return
        }

        val idp = context.session.identityProviders().getByAlias(selectedIdp)
        when {
            idp == null -> context.failure("Could not find the selected identity provider")
            !idp.isEnabled -> context.failure("The selected identity provider is not enabled")
            idp.organizationId != selectedOrg -> {
                context.failure(
                        "The selected identity provider is not registered to the selected organization"
                )
            }
            else -> {
                context.authenticationSession.setAuthNote(Details.IDENTITY_PROVIDER, idp.internalId)
                context.success()
            }
        }
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
    ) = Unit

    override fun close() = Unit
}
