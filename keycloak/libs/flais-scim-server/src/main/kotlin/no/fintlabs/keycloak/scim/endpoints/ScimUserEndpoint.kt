package no.fintlabs.keycloak.scim.endpoints

import com.fasterxml.jackson.databind.node.ObjectNode
import com.unboundid.scim2.common.messages.PatchRequest
import com.unboundid.scim2.common.types.Email
import com.unboundid.scim2.common.types.EnterpriseUserExtension
import com.unboundid.scim2.common.types.Name
import com.unboundid.scim2.common.types.Role
import com.unboundid.scim2.common.utils.ApiConstants
import com.unboundid.scim2.common.utils.JsonUtils
import com.unboundid.scim2.server.annotations.ResourceType
import com.unboundid.scim2.server.utils.ResourcePreparer
import com.unboundid.scim2.server.utils.SchemaChecker
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.InternalServerErrorException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriBuilder
import jakarta.ws.rs.core.UriInfo
import no.fintlabs.keycloak.scim.context.ScimContext
import no.fintlabs.keycloak.scim.resources.SearchHandler
import no.fintlabs.keycloak.scim.resources.UserResource
import no.fintlabs.keycloak.scim.utils.ResourcePath
import no.fintlabs.keycloak.scim.utils.ResourceTypeDefinitionUtil.createResourceTypeDefinition
import no.fintlabs.keycloak.scim.utils.ScimRoles
import org.keycloak.models.FederatedIdentityModel
import org.keycloak.models.UserModel
import org.keycloak.util.JsonSerialization
import kotlin.streams.asSequence

@ResourceType(
    description = "User Account",
    name = "User",
    schema = UserResource::class,
    optionalSchemaExtensions = [EnterpriseUserExtension::class],
)
@ResourcePath("Users")
class ScimUserEndpoint(
    private val scimContext: ScimContext,
) {
    @GET
    @Produces(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    fun getUsers(
        @Context uriInfo: UriInfo,
    ): Response {
        val searchHandler = SearchHandler<UserResource>(RESOURCE_TYPE_DEFINITION, uriInfo)
        val scimRole =
            requireNotNull(scimContext.realm.getRole(ScimRoles.SCIM_MANAGED_ROLE)) {
                "SCIM managed role not found"
            }
        val userResources =
            scimContext.orgProvider
                .getMembersStream(
                    scimContext.organization,
                    emptyMap(),
                    true,
                    null,
                    null,
                ).filter { it.hasRole(scimRole) }
                .map { translateUser(it) }
                .asSequence()
        val searchResult = searchHandler.createSearchResult(userResources)
        return Response.ok(searchResult).build()
    }

    @GET
    @Path("/{id}")
    @Produces(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    fun getUser(
        @PathParam("id") id: String,
        @Context uriInfo: UriInfo,
    ): Response {
        val user =
            scimContext.orgProvider
                .getMemberById(scimContext.organization, id)
                ?: return Response
                    .status(Response.Status.NOT_FOUND)
                    .type(ApiConstants.MEDIA_TYPE_SCIM)
                    .entity(
                        mapOf(
                            "schemas" to listOf("urn:ietf:params:scim:api:messages:2.0:Error"),
                            "status" to 404,
                            "detail" to "No user found with id $id",
                        ),
                    ).build()
        assertUserScimManaged(user)

        val scimUser =
            translateUser(user).let {
                val resourcePreparer = ResourcePreparer<UserResource>(RESOURCE_TYPE_DEFINITION, uriInfo)
                resourcePreparer.setResourceTypeAndLocation(it)
                resourcePreparer.trimRetrievedResource(it)
            }
        return Response.ok(scimUser).build()
    }

    @POST
    @Produces(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    @Consumes(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    fun createUser(
        @Context uriInfo: UriInfo,
        scimUser: UserResource,
    ): Response {
        val resourcePreparer =
            ResourcePreparer<UserResource>(
                RESOURCE_TYPE_DEFINITION,
                uriInfo,
            )
        SCHEMA_CHECKER
            .checkCreate(
                SCHEMA_CHECKER.removeReadOnlyAttributes(scimUser.asGenericScimResource().objectNode),
            ).throwSchemaExceptions()

        val userProvider = scimContext.session.users()
        if (userProvider.getUserById(scimContext.realm, scimUser.userName) != null) {
            return Response.status(Response.Status.CONFLICT).build()
        }

        val user = userProvider.addUser(scimContext.realm, scimUser.userName)
        val scimRole =
            requireNotNull(scimContext.realm.getRole(ScimRoles.SCIM_MANAGED_ROLE)) {
                "SCIM managed role not found"
            }
        user.grantRole(scimRole)
        updateUserModel(user, scimUser)

        scimContext.orgProvider.addManagedMember(scimContext.organization, user)
        updateUserIdpLinking(user)

        val result =
            translateUser(user).let {
                resourcePreparer.trimCreatedResource(it, scimUser)
            }
        val resultURI = UriBuilder.fromUri(uriInfo.requestUri).path(result.id).build()
        return Response.created(resultURI).entity(result).build()
    }

    @PUT
    @Path("/{id}")
    @Produces(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    @Consumes(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    fun updateUser(
        @Context uriInfo: UriInfo,
        @PathParam("id") id: String,
        updatedScimUser: UserResource,
    ): Response {
        val user =
            runCatching { scimContext.orgProvider.getMemberById(scimContext.organization, id) }.getOrElse {
                throw NotFoundException("No user found with id $id")
            }
        assertUserScimManaged(user)
        assertUserOrganizationManaged(user)

        JsonUtils.valueToNode<ObjectNode>(translateUser(user)).apply {
            SCHEMA_CHECKER
                .checkReplace(
                    updatedScimUser.asGenericScimResource().objectNode,
                    SCHEMA_CHECKER.removeReadOnlyAttributes(this),
                ).throwSchemaExceptions()
        }

        updateUserModel(user, updatedScimUser)
        updateUserIdpLinking(user)

        val result =
            translateUser(user).let {
                val resourcePreparer =
                    ResourcePreparer<UserResource>(
                        RESOURCE_TYPE_DEFINITION,
                        uriInfo,
                    )
                resourcePreparer.trimReplacedResource(it, updatedScimUser)
            }

        return Response.ok(result).build()
    }

    @PATCH
    @Path("/{id}")
    @Produces(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    @Consumes(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    fun patchUser(
        @Context uriInfo: UriInfo,
        @PathParam("id") id: String,
        patchOperations: PatchRequest,
    ): Response {
        val user =
            runCatching { scimContext.orgProvider.getMemberById(scimContext.organization, id) }.getOrElse {
                throw NotFoundException("No user found with id $id")
            }
        assertUserScimManaged(user)
        assertUserOrganizationManaged(user)

        val node =
            JsonUtils.valueToNode<ObjectNode>(translateUser(user)).apply {
                SCHEMA_CHECKER
                    .checkModify(
                        patchOperations,
                        SCHEMA_CHECKER.removeReadOnlyAttributes(this),
                    ).throwSchemaExceptions()
                patchOperations.forEach { it.apply(this) }
            }

        val scimUser =
            runCatching {
                JsonUtils.getObjectReader().treeToValue(node, UserResource::class.java)
            }.getOrElse {
                throw InternalServerErrorException(it.message, it)
            }

        updateUserModel(user, scimUser)
        updateUserIdpLinking(user)

        val result =
            translateUser(user).let {
                val resourcePreparer =
                    ResourcePreparer<UserResource>(
                        RESOURCE_TYPE_DEFINITION,
                        uriInfo,
                    )
                resourcePreparer.trimModifiedResource(it, patchOperations)
            }

        return Response.ok(result).build()
    }

    @DELETE
    @Path("/{id}")
    fun deleteUser(
        @PathParam("id") id: String,
    ): Response {
        val user =
            runCatching { scimContext.orgProvider.getMemberById(scimContext.organization, id) }.getOrElse {
                throw NotFoundException("No user found with id $id")
            }
        assertUserScimManaged(user)

        if (!scimContext.orgProvider.isManagedMember(scimContext.organization, user)) {
            throw NotFoundException("User is not part of the organization")
        }

        scimContext.orgProvider.removeMember(scimContext.organization, user)
        return Response.noContent().build()
    }

    private fun translateUser(user: UserModel) =
        UserResource().apply {
            id = user.id
            externalId = user.getExternalId()
            userName = user.username
            active = user.isEnabled
            emails =
                mutableListOf(
                    Email().apply {
                        primary = true
                        value = user.email
                    },
                )
            name =
                Name().apply {
                    givenName = user.firstName
                    familyName = user.lastName
                }
            roles =
                user
                    .getAttributeStream("rawRoles")
                    .map { JsonSerialization.readValue(it, Role::class.java) }
                    .toList()
                    .toMutableList()
        }

    private fun updateUserModel(
        user: UserModel,
        scimUser: UserResource,
    ) {
        user.username = scimUser.userName
        user.isEnabled = scimUser.active!!

        user.setExternalId(scimUser.externalId)

        scimUser.name?.let { name ->
            user.firstName = name.givenName
            user.lastName = name.familyName
        }

        scimUser.emails?.find { it.primary }?.let { email ->
            user.email = email.value
            user.isEmailVerified = true
        }

        scimUser.roles?.let {
            user.removeAttribute("rawRoles")
            user.setAttribute("rawRoles", it.map(JsonSerialization::writeValueAsString))

            user.removeAttribute("roles")
            user.setAttribute(
                "roles",
                it.map { it.value },
            )
        }
    }

    private fun updateUserIdpLinking(user: UserModel) {
        val emailDomain =
            user.email
                .substringAfter('@', missingDelimiterValue = "")
                .takeIf { it.isNotEmpty() } ?: return
        val externalId = user.getExternalId() ?: return

        val userProvider = scimContext.session.users()

        val socialProviders =
            scimContext.orgProvider
                .getIdentityProviders(scimContext.organization)
                .filter { idp ->
                    idp.config["kc.org.domain"]?.equals(emailDomain, ignoreCase = true) ?: false
                }.map { it.alias }
                .toList()

        socialProviders.forEach { provider ->
            if (userProvider.getFederatedIdentity(scimContext.realm, user, provider) != null) return@forEach
            val federatedIdentity =
                FederatedIdentityModel(
                    provider,
                    externalId,
                    user.username,
                )
            userProvider.addFederatedIdentity(scimContext.realm, user, federatedIdentity)
        }

        scimContext.session
            .users()
            .getFederatedIdentitiesStream(scimContext.realm, user)
            .filter { !socialProviders.contains(it.identityProvider) }
            .forEach {
                scimContext.session.users().removeFederatedIdentity(scimContext.realm, user, it.identityProvider)
            }
    }

    private fun assertUserScimManaged(user: UserModel) {
        val scimRole =
            requireNotNull(scimContext.realm.getRole(ScimRoles.SCIM_MANAGED_ROLE)) {
                "SCIM managed role not found"
            }
        if (!user.hasRole(scimRole)) {
            throw ForbiddenException("User is not SCIM-Managed ${user.id}")
        }
    }

    private fun assertUserOrganizationManaged(user: UserModel) {
        if (!scimContext.orgProvider.isManagedMember(scimContext.organization, user)) {
            throw ForbiddenException("User is not part of the organization")
        }
    }

    fun UserModel.getExternalId(): String? = this.getFirstAttribute("externalId")

    fun UserModel.setExternalId(id: String) {
        this.setSingleAttribute("externalId", id)
    }

    companion object {
        private val RESOURCE_TYPE_DEFINITION = createResourceTypeDefinition<ScimUserEndpoint>()
        private val SCHEMA_CHECKER = SchemaChecker(RESOURCE_TYPE_DEFINITION)
    }
}
