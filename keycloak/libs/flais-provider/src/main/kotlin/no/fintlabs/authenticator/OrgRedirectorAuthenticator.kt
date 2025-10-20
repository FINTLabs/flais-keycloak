package no.fintlabs.authenticator

import no.fintlabs.flow.AuthenticationErrorHandler.failure
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.authenticators.browser.IdentityProviderAuthenticator
import org.keycloak.events.Details

class OrgRedirectorAuthenticator : IdentityProviderAuthenticator() {
    private val logger: Logger = Logger.getLogger(OrgRedirectorAuthenticator::class.java)

    override fun authenticate(context: AuthenticationFlowContext) {
        val selectedIdp = context.authenticationSession.getAuthNote(Details.IDENTITY_PROVIDER)
        if (selectedIdp == null) {
            context.failure("No identity provider selected selected")
        }
        val idp = context.session.identityProviders().getById(selectedIdp)
        when {
            idp == null -> context.failure("Could not find identity provider")
            else -> {
                logger.infof("Redirecting to selected provider: %s", idp.alias)
                super.redirect(context, idp.alias)
            }
        }
    }
}
