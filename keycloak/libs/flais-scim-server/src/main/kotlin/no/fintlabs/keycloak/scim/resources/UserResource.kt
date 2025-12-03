package no.fintlabs.keycloak.scim.resources

import com.unboundid.scim2.common.BaseScimResource
import com.unboundid.scim2.common.annotations.Attribute
import com.unboundid.scim2.common.annotations.Nullable
import com.unboundid.scim2.common.annotations.Schema
import com.unboundid.scim2.common.types.AttributeDefinition
import com.unboundid.scim2.common.types.Email
import com.unboundid.scim2.common.types.Name
import com.unboundid.scim2.common.types.Role
import java.util.Objects

@Schema(
    id = "urn:ietf:params:scim:schemas:core:2.0:User",
    name = "User",
    description = "User Account",
)
class UserResource : BaseScimResource() {
    @Nullable
    @Attribute(
        description =
            "Unique identifier for the User typically " +
                "used by the user to directly authenticate to the service provider.",
        isRequired = true,
        isCaseExact = false,
        mutability = AttributeDefinition.Mutability.READ_WRITE,
        returned = AttributeDefinition.Returned.DEFAULT,
        uniqueness = AttributeDefinition.Uniqueness.SERVER,
    )
    var userName: String? = null

    @Nullable
    @Attribute(
        description = "The components of the user's real name.",
        isRequired = false,
        mutability = AttributeDefinition.Mutability.READ_WRITE,
        returned = AttributeDefinition.Returned.DEFAULT,
        uniqueness = AttributeDefinition.Uniqueness.NONE,
    )
    var name: Name? = null

    @Nullable
    @Attribute(
        description =
            "A Boolean value indicating the User's " +
                "administrative status.",
        isRequired = true,
        mutability = AttributeDefinition.Mutability.READ_WRITE,
        returned = AttributeDefinition.Returned.DEFAULT,
        uniqueness = AttributeDefinition.Uniqueness.NONE,
    )
    var active: Boolean? = null

    @Nullable
    @Attribute(
        description = (
            "E-mail addresses for the user. The value " +
                "SHOULD be canonicalized by the Service Provider, e.g., " +
                "bjensen@example.com instead of bjensen@EXAMPLE.COM. Canonical Type " +
                "values of work, home, and other."
        ),
        isRequired = false,
        mutability = AttributeDefinition.Mutability.READ_WRITE,
        returned = AttributeDefinition.Returned.DEFAULT,
        uniqueness = AttributeDefinition.Uniqueness.NONE,
        multiValueClass = Email::class,
    )
    var emails: MutableList<Email>? = null

    @Nullable
    @Attribute(
        description =
            "A list of roles for the User that " +
                "collectively represent who the User is; e.g., 'Student', 'Faculty'.",
        isRequired = false,
        returned = AttributeDefinition.Returned.DEFAULT,
        multiValueClass = Role::class,
    )
    var roles: MutableList<Role>? = null

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false

        if (!super.equals(o)) return false

        val user = o as UserResource
        if (!Objects.equals(userName, user.userName)) return false
        if (!Objects.equals(name, user.name)) return false
        if (!Objects.equals(active, user.active)) return false
        if (!Objects.equals(emails, user.emails)) return false
        if (!Objects.equals(roles, user.roles)) return false

        return true
    }

    override fun hashCode(): Int =
        Objects.hash(
            super.hashCode(),
            userName,
            name,
            active,
            emails,
            roles,
        )
}
