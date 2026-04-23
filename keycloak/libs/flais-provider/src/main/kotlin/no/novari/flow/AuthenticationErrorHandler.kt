package no.novari.flow

import jakarta.ws.rs.core.Response
import org.keycloak.authentication.AuthenticationFlowContext

object AuthenticationErrorHandler {
    fun AuthenticationFlowContext.failure(msg: String) {
        val form = this.form().setError(msg).createErrorPage(Response.Status.OK)
        this.challenge(form)
    }
}
