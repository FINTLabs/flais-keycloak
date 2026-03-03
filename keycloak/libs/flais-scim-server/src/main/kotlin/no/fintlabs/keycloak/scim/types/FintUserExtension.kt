package no.fintlabs.keycloak.scim.types

import com.unboundid.scim2.common.annotations.Attribute
import com.unboundid.scim2.common.annotations.Schema
import com.unboundid.scim2.common.types.AttributeDefinition

@Schema(
    id = "urn:ietf:params:scim:schemas:extension:fint:2.0:User",
    name = "FintUser",
    description = "Fint user extension",
)
class FintUserExtension {
    @Attribute(
        description = "Employee ID",
        isRequired = false,
        mutability = AttributeDefinition.Mutability.READ_WRITE,
        returned = AttributeDefinition.Returned.DEFAULT,
    )
    var employeeId: String? = null

    @Attribute(
        description = "Student Number",
        isRequired = false,
        mutability = AttributeDefinition.Mutability.READ_WRITE,
        returned = AttributeDefinition.Returned.DEFAULT,
    )
    var studentNumber: String? = null

    @Attribute(
        description = "User Principal Name",
        isRequired = false,
        mutability = AttributeDefinition.Mutability.READ_WRITE,
        returned = AttributeDefinition.Returned.DEFAULT,
    )
    var userPrincipalName: String? = null
}
