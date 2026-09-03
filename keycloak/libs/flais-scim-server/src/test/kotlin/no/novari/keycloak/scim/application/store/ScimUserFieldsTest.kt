package no.novari.keycloak.scim.application.store

import com.unboundid.scim2.common.Path
import no.novari.keycloak.scim.store.ScimUserFields
import no.novari.keycloak.scim.store.UserField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

internal class ScimUserFieldsTest {
    private fun resolve(path: String) = ScimUserFields.resolve(Path.fromString(path))

    @ParameterizedTest
    @CsvSource(
        "id, id, CASE_EXACT",
        "userName, username, STORED_LOWERCASE",
        "username, username, STORED_LOWERCASE",
        "active, enabled, BOOLEAN",
        "emails.value, email, STORED_LOWERCASE",
    )
    fun `core attributes resolve to user columns`(
        path: String,
        property: String,
        kind: UserField.Kind,
    ) {
        assertEquals(UserField.Column(property, kind), resolve(path))
    }

    @ParameterizedTest
    @CsvSource(
        "externalId, externalId, CASE_EXACT",
        "roles, roles, MIXED_CASE",
        "roles.value, roles, MIXED_CASE",
    )
    fun `core attributes resolve to user attributes`(
        path: String,
        attribute: String,
        kind: UserField.Kind,
    ) {
        assertEquals(UserField.Attribute(attribute, kind), resolve(path))
    }

    @ParameterizedTest
    @CsvSource(
        "employeeId, employeeId",
        "studentNumber, studentNumber",
        "userPrincipalName, userPrincipalName",
    )
    fun `fint extension attributes resolve to user attributes`(
        attribute: String,
        expected: String,
    ) {
        assertEquals(
            UserField.Attribute(expected, UserField.Kind.CASE_EXACT),
            resolve("${ScimUserFields.FINT_SCHEMA_URN}:$attribute"),
        )
    }

    @Test
    fun `fint given and family name resolve to mixed case columns`() {
        assertEquals(
            UserField.Column("firstName", UserField.Kind.MIXED_CASE),
            resolve("${ScimUserFields.FINT_SCHEMA_URN}:givenName"),
        )
        assertEquals(
            UserField.Column("lastName", UserField.Kind.MIXED_CASE),
            resolve("${ScimUserFields.FINT_SCHEMA_URN}:familyName"),
        )
    }

    @Test
    fun `emails primary is constant because translateUser always marks the single email primary`() {
        assertEquals(UserField.Constant(true), resolve("emails.primary"))
    }

    @Test
    fun `emails value filter that every user satisfies is dropped`() {
        assertEquals(
            UserField.Column("email", UserField.Kind.STORED_LOWERCASE),
            resolve("emails[primary eq true].value"),
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            // Only present inside the rawRoles JSON blob.
            "roles.type",
            "roles.display",
            "roles.primary",
            // `emails` is a complex attribute; only exact subattributes are pushed down.
            "emails",
            // Never emitted by translateUser.
            "emails.type",
            "meta.created",
            "name.givenName",
            "unknownAttribute",
        ],
    )
    fun `attributes that cannot be mapped exactly are left unresolved`(path: String) {
        assertNull(resolve(path))
    }

    @Test
    fun `value filters that are not always satisfied are left unresolved`() {
        assertNull(resolve("""emails[type eq "work"].value"""))
        assertNull(resolve("emails[primary eq false].value"))
    }

    @Test
    fun `unknown schema urns are left unresolved`() {
        assertNull(resolve("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department"))
    }

    @Test
    fun `only columns are offered as sort keys`() {
        assertEquals(
            UserField.Column("username", UserField.Kind.STORED_LOWERCASE),
            ScimUserFields.resolveSortColumn(Path.fromString("userName")),
        )
        // Multivalued, so there is no single well-defined ordering key.
        assertNull(ScimUserFields.resolveSortColumn(Path.fromString("roles")))
    }
}
