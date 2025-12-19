package no.fintlabs.authenticator

import no.fintlabs.flow.AuthenticationErrorHandler.failure
import org.jboss.logging.Logger
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.authenticators.browser.IdentityProviderAuthenticator
import org.keycloak.events.Details

class OrgRedirectorAuthenticator : IdentityProviderAuthenticator() {
    private val logger: Logger = Logger.getLogger(OrgRedirectorAuthenticator::class.java)

    fun doRedirect(
        context: AuthenticationFlowContext,
        alias: String,
    ) {
        super.redirect(context, alias)
    }

    override fun authenticate(context: AuthenticationFlowContext) {
        val selectedIdp = context.authenticationSession.getAuthNote(Details.IDENTITY_PROVIDER)
        if (selectedIdp == null) {
            context.failure("No identity provider selected selected")
            return
        }

        val idp = context.session.identityProviders().getById(selectedIdp)
        if (idp == null) {
            context.failure("Could not find identity provider")
            return
        }

        logger.infof("Redirecting to selected provider: %s", idp.alias)
        doRedirect(context, idp.alias)
    }
}
