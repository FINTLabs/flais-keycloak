package no.fintlabs.helpers

import jakarta.ws.rs.client.Entity.form
import jakarta.ws.rs.core.Response
import org.keycloak.authentication.AuthenticationFlowContext

object FailureHelper {
    fun AuthenticationFlowContext.failure(msg: String) {
        val form = this.form().setError(msg).createErrorPage(Response.Status.OK)
        this.challenge(form)
    }
}
