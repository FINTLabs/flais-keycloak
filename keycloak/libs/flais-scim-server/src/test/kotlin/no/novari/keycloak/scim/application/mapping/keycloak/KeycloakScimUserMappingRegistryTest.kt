package no.novari.keycloak.scim.application.mapping.keycloak

import com.unboundid.scim2.common.Path
import no.novari.keycloak.scim.mapping.AlwaysPresentComplex
import no.novari.keycloak.scim.mapping.Attribute
import no.novari.keycloak.scim.mapping.Column
import no.novari.keycloak.scim.mapping.Constant
import no.novari.keycloak.scim.mapping.FieldKind
import no.novari.keycloak.scim.mapping.Unsupported
import no.novari.keycloak.scim.mapping.keycloak.KeycloakScimUserMappingRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.keycloak.models.jpa.entities.UserEntity

internal class KeycloakScimUserMappingRegistryTest {
    private fun resolve(path: String) = KeycloakScimUserMappingRegistry.resolve(Path.fromString(path))

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
        kind: FieldKind,
    ) {
        val field = resolve(path) as Column<UserEntity>
        assertEquals(property, field.property)
        assertEquals(kind, field.kind)
    }

    @ParameterizedTest
    @CsvSource(
        "externalId, externalId, CASE_EXACT",
        "roles, rawRoles, CASE_EXACT",
        "roles.value, roles, MIXED_CASE",
    )
    fun `core attributes resolve to user attributes`(
        path: String,
        attribute: String,
        kind: FieldKind,
    ) {
        assertEquals(Attribute<UserEntity>(attribute, kind, multiValued = path.startsWith("roles")), resolve(path))
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
            Attribute<UserEntity>(expected, FieldKind.CASE_EXACT),
            resolve("${KeycloakScimUserMappingRegistry.fintSchemaUrn}:$attribute"),
        )
    }

    @Test
    fun `fint given and family name resolve to mixed case columns`() {
        assertEquals(
            "firstName",
            (resolve("${KeycloakScimUserMappingRegistry.fintSchemaUrn}:givenName") as Column<UserEntity>).property,
        )
        assertEquals(
            "lastName",
            (resolve("${KeycloakScimUserMappingRegistry.fintSchemaUrn}:familyName") as Column<UserEntity>).property,
        )
    }

    @Test
    fun `emails primary is constant because translateUser always marks the single email primary`() {
        assertEquals(AlwaysPresentComplex<UserEntity>("emails"), resolve("emails"))
        assertEquals(Constant<UserEntity>(true), resolve("emails.primary"))
    }

    @Test
    fun `emails value filter that every user satisfies is dropped`() {
        val field = resolve("emails[primary eq true].value") as Column<UserEntity>
        assertEquals("email", field.property)
        assertEquals(FieldKind.STORED_LOWERCASE, field.kind)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "roles.type",
            "roles.display",
            "roles.primary",
            "emails.type",
        ],
    )
    fun `known schema attributes that cannot be mapped exactly resolve as unsupported`(path: String) {
        val field = resolve(path) as Unsupported<UserEntity>
        assertEquals(false, field.sortable)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
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
            "username",
            KeycloakScimUserMappingRegistry.resolveSortColumn(Path.fromString("userName"))?.property,
        )
        // Multivalued, so there is no single well-defined ordering key.
        assertNull(KeycloakScimUserMappingRegistry.resolveSortColumn(Path.fromString("roles")))
    }
}
