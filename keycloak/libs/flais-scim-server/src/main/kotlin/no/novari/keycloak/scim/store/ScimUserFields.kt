package no.novari.keycloak.scim.store

import com.unboundid.scim2.common.Path
import com.unboundid.scim2.common.filters.FilterType

/**
 * A SCIM attribute resolved to somewhere it can be found in Keycloak's schema.
 */
internal sealed interface UserField {
    /** A column on `UserEntity`. */
    data class Column(
        val property: String,
        val kind: Kind,
    ) : UserField

    /** A row in `UserAttributeEntity`. May be multivalued. */
    data class Attribute(
        val name: String,
        val kind: Kind,
    ) : UserField

    /**
     * A value this provider emits identically for every user, so it can be decided without
     * touching the database.
     */
    data class Constant(
        val value: Boolean,
    ) : UserField

    enum class Kind {
        /** Compared verbatim; SCIM `id` is caseExact. */
        CASE_EXACT,

        /** Keycloak already stores these lowercased, so only the search term needs folding. */
        STORED_LOWERCASE,

        /** Arbitrary case in the database, so both sides need folding. */
        MIXED_CASE,

        BOOLEAN,
    }
}

/**
 * Maps SCIM attribute paths onto Keycloak storage.
 *
 * The mapping must mirror `ScimUserEndpoint.translateUser`, which is what the in-memory fallback
 * filters over. Any attribute that cannot be mapped exactly is left unresolved so the caller falls
 * back rather than answering from a subtly different definition.
 *
 * Deliberately absent:
 * - `roles.type`, `roles.display`, `roles.primary` — only stored inside the `rawRoles` JSON blob.
 * - `emails.type` — never emitted by this provider.
 * - `meta.*` — not persisted as user data.
 */
internal object ScimUserFields {
    const val FINT_SCHEMA_URN = "urn:ietf:params:scim:schemas:extension:fint:2.0:User"
    private const val CORE_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:User"

    private val core =
        mapOf(
            "id" to UserField.Column("id", UserField.Kind.CASE_EXACT),
            "externalid" to UserField.Attribute("externalId", UserField.Kind.CASE_EXACT),
            "username" to UserField.Column("username", UserField.Kind.STORED_LOWERCASE),
            "active" to UserField.Column("enabled", UserField.Kind.BOOLEAN),
            // translateUser emits a complex email object, so only subattributes map to columns.
            "emails.value" to UserField.Column("email", UserField.Kind.STORED_LOWERCASE),
            "emails.primary" to UserField.Constant(true),
            // The `roles` user attribute holds one row per SCIM role value.
            "roles" to UserField.Attribute("roles", UserField.Kind.MIXED_CASE),
            "roles.value" to UserField.Attribute("roles", UserField.Kind.MIXED_CASE),
        )

    private val fint =
        mapOf(
            "employeeid" to UserField.Attribute("employeeId", UserField.Kind.CASE_EXACT),
            "studentnumber" to UserField.Attribute("studentNumber", UserField.Kind.CASE_EXACT),
            "userprincipalname" to UserField.Attribute("userPrincipalName", UserField.Kind.CASE_EXACT),
            "givenname" to UserField.Column("firstName", UserField.Kind.MIXED_CASE),
            "familyname" to UserField.Column("lastName", UserField.Kind.MIXED_CASE),
        )

    fun resolve(path: Path): UserField? {
        val table =
            when (path.schemaUrn?.lowercase()) {
                null, CORE_SCHEMA_URN.lowercase() -> core
                FINT_SCHEMA_URN.lowercase() -> fint
                else -> return null
            }

        val key = StringBuilder()
        path.forEachIndexed { index, element ->
            if (element.valueFilter != null && !element.isAlwaysSatisfied()) {
                return null
            }
            if (index > 0) key.append('.')
            key.append(element.attribute.lowercase())
        }

        return table[key.toString()]
    }

    /**
     * True for a value filter that every user this provider emits satisfies, so it can be dropped.
     *
     * The only such case is `emails[primary eq true]`: `translateUser` always emits a single email
     * marked primary.
     */
    private fun Path.Element.isAlwaysSatisfied(): Boolean {
        if (!attribute.equals("emails", ignoreCase = true)) return false

        val valueFilter = valueFilter ?: return true
        if (valueFilter.filterType != FilterType.EQUAL) return false

        val subPath = valueFilter.attributePath ?: return false
        if (subPath.size() != 1) return false
        if (!subPath.getElement(0).attribute.equals("primary", ignoreCase = true)) return false

        val value = valueFilter.comparisonValue ?: return false
        return value.isBoolean && value.booleanValue()
    }

    private inline fun Path.forEachIndexed(action: (Int, Path.Element) -> Unit) {
        var index = 0
        for (element in this) {
            action(index, element)
            index++
        }
    }

    /** Convenience for callers that only accept fields orderable by a single column. */
    fun resolveSortColumn(path: Path): UserField.Column? = resolve(path) as? UserField.Column
}
