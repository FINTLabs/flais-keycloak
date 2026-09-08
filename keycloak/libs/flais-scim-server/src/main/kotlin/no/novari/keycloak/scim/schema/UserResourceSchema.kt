package no.novari.keycloak.scim.schema

import com.unboundid.scim2.common.BaseScimResource
import com.unboundid.scim2.common.types.Email
import com.unboundid.scim2.common.types.Role
import no.novari.keycloak.scim.resources.UserResource

internal object UserResourceSchema {
    private val schema = ScimSchema(UserResource::class)
    val urn = schema.urn

    val id = schema.path(BaseScimResource::getId)
    val externalId = schema.path(BaseScimResource::getExternalId)
    val active = schema.path(UserResource::active)
    val userName = schema.path(UserResource::userName)

    object Emails {
        val self = schema.path(UserResource::emails)
        val value = schema.path(UserResource::emails, Email::getValue)
        val primary = schema.path(UserResource::emails, Email::getPrimary)
        val type = schema.path(UserResource::emails, Email::getType)
    }

    object Roles {
        val self = schema.path(UserResource::roles)
        val value = schema.path(UserResource::roles, Role::getValue)
        val display = schema.path(UserResource::roles, Role::getDisplay)
        val type = schema.path(UserResource::roles, Role::getType)
        val primary = schema.path(UserResource::roles, Role::getPrimary)
    }
}
