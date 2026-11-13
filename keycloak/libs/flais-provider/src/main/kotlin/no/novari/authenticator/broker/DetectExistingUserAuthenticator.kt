package no.novari.authenticator.broker

import jakarta.ws.rs.core.Response
import org.keycloak.authentication.AuthenticationFlowContext
import org.keycloak.authentication.AuthenticationFlowError
import org.keycloak.authentication.AuthenticationFlowException
import org.keycloak.authentication.authenticators.broker.IdpDetectExistingBrokerUserAuthenticator
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext
import org.keycloak.broker.provider.BrokeredIdentityContext
import org.keycloak.events.Errors

class DetectExistingUserAuthenticator : IdpDetectExistingBrokerUserAuthenticator() {
    override fun authenticateImpl(
        context: AuthenticationFlowContext,
        serializedCtx: SerializedBrokeredIdentityContext,
        brokerContext: BrokeredIdentityContext,
    ) {
        val authSession = context.authenticationSession

        authSession.removeAuthNote(EXISTING_USER_INFO)

        val username = getUsername(context, serializedCtx, brokerContext)

        val errorMessageKey =
            context.authenticatorConfig
                ?.config
                ?.get(DetectExistingUserAuthenticatorConfig.ERROR_MESSAGE_KEY)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw AuthenticationFlowException(
                    DetectExistingUserAuthenticatorConfig.MISSING_ERROR_MESSAGE_KEY_MESSAGE,
                    AuthenticationFlowError.INTERNAL_ERROR,
                )

        if (username.isNullOrBlank()) {
            reject(context, errorMessageKey)
            return
        }

        val existingUser =
            checkExistingUser(
                context,
                username,
                serializedCtx,
                brokerContext,
            )

        if (existingUser == null) {
            reject(context, errorMessageKey)
            return
        }

        authSession.setAuthNote(
            EXISTING_USER_INFO,
            existingUser.serialize(),
        )

        context.success()
    }

    override fun actionImpl(
        context: AuthenticationFlowContext,
        serializedCtx: SerializedBrokeredIdentityContext,
        brokerContext: BrokeredIdentityContext,
    ) {
        authenticateImpl(context, serializedCtx, brokerContext)
    }

    private fun reject(
        context: AuthenticationFlowContext,
        errorMessageKey: String,
    ) {
        context.event.error(Errors.USER_NOT_FOUND)

        val response =
            context
                .form()
                .setError(errorMessageKey)
                .createErrorPage(Response.Status.FORBIDDEN)

        context.failure(AuthenticationFlowError.INVALID_USER, response)
    }
}
