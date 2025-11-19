package no.fintlabs.keycloak.scim

import com.unboundid.scim2.common.messages.PatchRequest
import com.unboundid.scim2.common.types.Email
import com.unboundid.scim2.common.types.Name
import com.unboundid.scim2.common.types.Role
import com.unboundid.scim2.server.annotations.ResourceType
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import no.fintlabs.keycloak.scim.consts.ScimRoles
import no.fintlabs.keycloak.scim.resources.UserResource
import no.fintlabs.keycloak.scim.results.SearchResults
import org.keycloak.models.UserModel
import org.keycloak.util.JsonSerialization


@ResourceType(
    description = "User Account",
    name = "User",
    schema = UserResource::class)
class ScimUserResource(
    private val scimContext: ScimContext,
) {
    @GET
    fun getUsers(
        @Context uriInfo: UriInfo,
    ) : Response {
        val searchResult = SearchResults(RESOURCE_TYPE_DEFINITION, uriInfo) { _ ->
                val scimRole = requireNotNull(scimContext.realm.getRole(ScimRoles.SCIM_MANAGED_ROLE)) {
                    "SCIM managed role not found"
                }

                scimContext.orgProvider
                    .getMembersStream(
                        scimContext.organization,
                        emptyMap(),
                        true,
                        null,
                        null
                    )
                    .filter { it.hasRole(scimRole) }
                    .map { translateUser(it) }
            }
        return Response.ok(searchResult).build()
    }

    @GET
    @Path("/{id}")
    fun getUser(@PathParam("id") id: String): Response {
        return Response.ok(null).build()
    }

    @POST
    fun createUser(scimUser: ScimUserResource): Response {
        return Response.status(201).entity(null).build()
    }

    @PATCH
    @Path("/{id}")
    fun patchUser(@PathParam("id") id: String, request: PatchRequest): Response {
        return Response.ok(null).build()
    }

    @DELETE
    @Path("/{id}")
    fun deleteUser(@PathParam("id") id: String): Response {
        return Response.noContent().build()
    }

    private fun translateUser(user: UserModel) = UserResource().apply {
        id = user.id
        externalId = user.getFirstAttribute("externalId")
        userName = user.username
        active = user.isEnabled
        emails = mutableListOf(Email().apply {
            primary = true
            value = user.email
        })
        name = Name().apply {
            givenName = user.firstName
            familyName = user.lastName
        }
        roles = user.getAttributeStream("roles").map {
            JsonSerialization.readValue(it, Role::class.java)
        }.toList().toMutableList()
    }

    companion object {
        private val RESOURCE_TYPE_DEFINITION =
            ResourceTypeDefinition.fromJaxRsResource(ScimUserResource::class.java)
    }
}
