package no.fintlabs.keycloak.scim.route

import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Context
import no.fintlabs.keycloak.scim.authentication.Authenticator.verifyAuthenticated
import no.fintlabs.keycloak.scim.context.ScimContext
import no.fintlabs.keycloak.scim.context.createScimContext
import org.keycloak.models.KeycloakSession

typealias Route<T> = (ScimContext) -> T

class ScimRoute<T> internal constructor(
    private val route: Route<T>,
) {
    @PathParam("organizationId")
    lateinit var organizationId: String

    @Context
    lateinit var session: KeycloakSession

    @Path("/")
    fun all() =
        createScimContext(session, organizationId).let { context ->
            verifyAuthenticated(context)
            route(context)
        }
}

fun <T> scimRoute(route: Route<T>) = ScimRoute(route)
