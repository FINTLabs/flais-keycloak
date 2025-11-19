package no.fintlabs.keycloak.scim

import com.unboundid.scim2.common.exceptions.ResourceNotFoundException
import com.unboundid.scim2.common.messages.PatchRequest
import com.unboundid.scim2.common.types.Email
import com.unboundid.scim2.common.types.Name
import com.unboundid.scim2.common.types.Role
import com.unboundid.scim2.server.annotations.ResourceType
import com.unboundid.scim2.server.utils.ResourcePreparer
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import com.unboundid.scim2.server.utils.SchemaChecker
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriBuilder
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
    fun getUser(
        @PathParam("id") id: String,
        @Context uriInfo: UriInfo
    ): Response {
        try {
            val user = scimContext.orgProvider.getMemberById(scimContext.organization, id)
            val scimUser = translateUser(user).let {
                val resourcePreparer = ResourcePreparer<UserResource>(RESOURCE_TYPE_DEFINITION, uriInfo)
                resourcePreparer.setResourceTypeAndLocation(it)
                resourcePreparer.trimRetrievedResource(it)
            }
            return Response.ok(scimUser).build()
        } catch (e: NotFoundException) {
            throw ResourceNotFoundException("No user found with id $id")
        }
    }

    @POST
    fun createUser(
        @Context uriInfo: UriInfo,
        scimUser: UserResource
    ): Response {
        val resourcePreparer = ResourcePreparer<UserResource>(
            RESOURCE_TYPE_DEFINITION, uriInfo)
        SCHEMA_CHECKER.checkCreate(
            SCHEMA_CHECKER.removeReadOnlyAttributes(scimUser.asGenericScimResource().objectNode))
            .throwSchemaExceptions()

        val userProvider = scimContext.session.users()
        if (userProvider.getUserById(scimContext.realm, scimUser.userName) != null) {
            return Response.status(Response.Status.CONFLICT).build()
        }

        val user = userProvider.addUser(scimContext.realm, scimUser.userName)
        user.isEnabled = scimUser.active!!

        user.setSingleAttribute("externalId", scimUser.externalId)

        scimUser.name?.let { name ->
            user.firstName = name.givenName
            user.lastName = name.familyName
        }

        scimUser.emails?.find { it.primary }?.let { email ->
            user.email = email.value
            user.isEmailVerified = true
        }

        val scimRole = requireNotNull(scimContext.realm.getRole(ScimRoles.SCIM_MANAGED_ROLE)) {
            "SCIM managed role not found"
        }
        user.grantRole(scimRole)

        scimUser.roles?.let {
            user.setAttribute(
                "roles",
                it.map(JsonSerialization::writeValueAsString).toList()
            )
        }

        val result = translateUser(user).let {
            resourcePreparer.trimCreatedResource(it, scimUser)
        }
        val resultURI = UriBuilder.fromUri(uriInfo.requestUri).path(result.id).build()
        return Response.created(resultURI).entity(result).build()
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

        private val SCHEMA_CHECKER = SchemaChecker(RESOURCE_TYPE_DEFINITION)
    }
}
