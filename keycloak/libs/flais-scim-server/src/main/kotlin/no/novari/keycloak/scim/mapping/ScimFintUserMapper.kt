package no.novari.keycloak.scim.mapping

import no.novari.keycloak.scim.resources.FintUserExtension
import org.keycloak.models.UserModel
import org.keycloak.models.jpa.entities.UserEntity

internal object ScimFintUserMapper :
    ScimMapping<FintUserExtension, UserEntity>(FintUserExtension::class) {
    init {
        property(
            FintUserExtension::userPrincipalName,
            column(
                UserModel.USERNAME,
                FieldKind.CASE_EXACT,
            ),
        )

        property(
            FintUserExtension::givenName,
            column(
                UserModel.FIRST_NAME,
                FieldKind.CASE_EXACT,
            ),
        )

        property(
            FintUserExtension::familyName,
            column(
                UserModel.LAST_NAME,
                FieldKind.CASE_EXACT,
            ),
        )

        property(
            FintUserExtension::employeeId,
            attribute(
                "employeeId", // TODO: Move to constant
                FieldKind.CASE_EXACT,
            ),
        )

        property(
            FintUserExtension::studentNumber,
            attribute(
                "studentNumber", // TODO: Move to constant
                FieldKind.CASE_EXACT,
            ),
        )
    }
}
