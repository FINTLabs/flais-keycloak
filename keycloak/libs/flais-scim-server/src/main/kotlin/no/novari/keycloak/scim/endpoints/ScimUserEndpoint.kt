package no.novari.keycloak.scim.endpoints

import com.unboundid.scim2.common.annotations.Attribute
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
import jakarta.ws.rs.BadRequestException
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
import no.novari.keycloak.scim.context.ScimContext
import no.novari.keycloak.scim.resources.SearchHandler
import no.novari.keycloak.scim.resources.UserResource
import no.novari.keycloak.scim.types.FintUserExtension
import no.novari.keycloak.scim.utils.EntraScimTransformer
import no.novari.keycloak.scim.utils.ResourcePath
import no.novari.keycloak.scim.utils.ResourceTypeDefinitionUtil.createResourceTypeDefinition
import no.novari.keycloak.scim.utils.ScimRoles
import org.keycloak.models.FederatedIdentityModel
import org.keycloak.models.UserModel
import org.keycloak.util.JsonSerialization
import tools.jackson.databind.node.ObjectNode
import java.net.URI
import kotlin.streams.asSequence

@ResourceType(
    description = "User Account",
    name = "User",
    schema = UserResource::class,
    optionalSchemaExtensions = [EnterpriseUserExtension::class, FintUserExtension::class],
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

        return scimOk(searchResult)
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
                ?: return scimError(
                    Response.Status.NOT_FOUND,
                    "No user found with id $id",
                )

        assertUserScimManaged(user)

        val scimUser =
            translateUser(user).let {
                val resourcePreparer = ResourcePreparer<UserResource>(RESOURCE_TYPE_DEFINITION, uriInfo)
                resourcePreparer.setResourceTypeAndLocation(it)
                resourcePreparer.trimRetrievedResource(it)
            }

        return scimOk(scimUser)
    }

    @POST
    @Produces(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    @Consumes(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    fun createUser(
        @Context uriInfo: UriInfo,
        body: String,
    ): Response {
        val scimUser = readUserResource(body)

        val resourcePreparer =
            ResourcePreparer<UserResource>(
                RESOURCE_TYPE_DEFINITION,
                uriInfo,
            )

        val node = SCHEMA_CHECKER.removeReadOnlyAttributes(scimUser.asGenericScimResource().objectNode)
        val normalizedNode = EntraScimTransformer.normalizeExtensionSchemas(node, RESOURCE_TYPE_DEFINITION)

        SCHEMA_CHECKER.checkCreate(normalizedNode).throwSchemaExceptions()

        val userProvider = scimContext.session.users()
        if (userProvider.getUserById(scimContext.realm, scimUser.userName) != null) {
            return scimError(
                Response.Status.CONFLICT,
                "User already exists with id ${scimUser.userName}",
            )
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

        return scimCreated(resultURI, result)
    }

    @PUT
    @Path("/{id}")
    @Produces(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    @Consumes(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    fun updateUser(
        @Context uriInfo: UriInfo,
        @PathParam("id") id: String,
        body: String,
    ): Response {
        val updatedScimUser = readUserResource(body)

        val user =
            runCatching { scimContext.orgProvider.getMemberById(scimContext.organization, id) }.getOrElse {
                throw NotFoundException("No user found with id $id")
            }

        assertUserScimManaged(user)
        assertUserOrganizationManaged(user)

        val node = updatedScimUser.asGenericScimResource().objectNode
        val normalizedNode = EntraScimTransformer.normalizeExtensionSchemas(node, RESOURCE_TYPE_DEFINITION)

        JsonUtils.valueToNode<ObjectNode>(translateUser(user)).apply {
            SCHEMA_CHECKER
                .checkReplace(normalizedNode, SCHEMA_CHECKER.removeReadOnlyAttributes(this))
                .throwSchemaExceptions()
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

        return scimOk(result)
    }

    @PATCH
    @Path("/{id}")
    @Produces(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    @Consumes(ApiConstants.MEDIA_TYPE_SCIM, MediaType.APPLICATION_JSON)
    fun patchUser(
        @Context uriInfo: UriInfo,
        @PathParam("id") id: String,
        body: String,
    ): Response {
        val patchOperations = readPatchRequest(body)

        val user =
            runCatching { scimContext.orgProvider.getMemberById(scimContext.organization, id) }.getOrElse {
                throw NotFoundException("No user found with id $id")
            }

        assertUserScimManaged(user)
        assertUserOrganizationManaged(user)

        val normalizedPatch = EntraScimTransformer.normalizePatch(patchOperations, RESOURCE_TYPE_DEFINITION)

        val node =
            JsonUtils.valueToNode<ObjectNode>(translateUser(user)).apply {
                SCHEMA_CHECKER
                    .checkModify(normalizedPatch, SCHEMA_CHECKER.removeReadOnlyAttributes(this))
                    .throwSchemaExceptions()
                normalizedPatch.forEach { it.apply(this) }
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

        return scimOk(result)
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

            setExtension(
                FintUserExtension().apply {
                    employeeId = user.getFirstAttribute("employeeId")
                    studentNumber = user.getFirstAttribute("studentNumber")
                    userPrincipalName = user.getFirstAttribute("userPrincipalName")
                },
            )
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
        } ?: run {
            user.firstName = null
            user.lastName = null
        }

        scimUser.emails?.find { it.primary }?.let { email ->
            user.email = email.value
            user.isEmailVerified = true
        } ?: run {
            user.email = null
            user.isEmailVerified = false
        }

        scimUser.roles?.let {
            user.removeAttribute("rawRoles")
            user.setAttribute("rawRoles", it.map(JsonSerialization::writeValueAsString))

            user.removeAttribute("roles")
            user.setAttribute(
                "roles",
                it.map { role -> role.value },
            )
        } ?: run {
            user.removeAttribute("rawRoles")
            user.removeAttribute("roles")
        }

        val fintUserExt = FintUserExtension::class.java
        val attributeFields =
            fintUserExt.declaredFields
                .filter { it.isAnnotationPresent(Attribute::class.java) }

        scimUser.getExtension(fintUserExt)?.let { ext ->
            attributeFields.forEach { field ->
                field.isAccessible = true
                val value = field.get(ext)

                if (value != null) {
                    user.setSingleAttribute(field.name, value.toString())
                } else {
                    user.removeAttribute(field.name)
                }
            }
        } ?: run {
            attributeFields.forEach { field ->
                user.removeAttribute(field.name)
            }
        }
    }

    private fun updateUserIdpLinking(user: UserModel) {
        val emailDomain =
            user.email
                ?.substringAfter('@', missingDelimiterValue = "")
                ?.takeIf { it.isNotEmpty() }
                ?: return

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

    private fun readUserResource(body: String): UserResource =
        runCatching {
            JsonUtils
                .getObjectReader()
                .forType(UserResource::class.java)
                .readValue<UserResource>(body)
        }.getOrElse {
            throw BadRequestException("Invalid SCIM User payload", it)
        }

    private fun readPatchRequest(body: String): PatchRequest =
        runCatching {
            JsonUtils
                .getObjectReader()
                .forType(PatchRequest::class.java)
                .readValue<PatchRequest>(body)
        }.getOrElse {
            throw BadRequestException("Invalid SCIM Patch payload", it)
        }

    private fun scimOk(entity: Any): Response =
        Response
            .ok(JsonUtils.getObjectWriter().writeValueAsString(entity))
            .type(ApiConstants.MEDIA_TYPE_SCIM)
            .build()

    private fun scimCreated(
        location: URI,
        entity: Any,
    ): Response =
        Response
            .created(location)
            .entity(JsonUtils.getObjectWriter().writeValueAsString(entity))
            .type(ApiConstants.MEDIA_TYPE_SCIM)
            .build()

    private fun scimError(
        status: Response.Status,
        detail: String,
    ): Response =
        Response
            .status(status)
            .type(ApiConstants.MEDIA_TYPE_SCIM)
            .entity(
                JsonUtils.getObjectWriter().writeValueAsString(
                    mapOf(
                        "schemas" to listOf("urn:ietf:params:scim:api:messages:2.0:Error"),
                        "status" to status.statusCode.toString(),
                        "detail" to detail,
                    ),
                ),
            ).build()

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

    fun UserModel.setExternalId(id: String?) {
        if (id == null) {
            this.removeAttribute("externalId")
        } else {
            this.setSingleAttribute("externalId", id)
        }
    }

    companion object {
        private val RESOURCE_TYPE_DEFINITION = createResourceTypeDefinition<ScimUserEndpoint>()
        private val SCHEMA_CHECKER = SchemaChecker(RESOURCE_TYPE_DEFINITION)
    }
}
