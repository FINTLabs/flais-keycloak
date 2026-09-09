package no.novari.keycloak.scim.mapping

/**
 * A SCIM attribute resolved to somewhere it can be found in the backing persistence model.
 */
internal sealed interface FieldMapping<E : Any> {
    val kind: FieldKind
    val sortable: Boolean
}

internal data class Column<E : Any>(
    val property: String,
    override val kind: FieldKind,
    override val sortable: Boolean = true,
) : FieldMapping<E>

internal data class Attribute<E : Any>(
    val name: String,
    override val kind: FieldKind,
    val multiValued: Boolean = false,
    override val sortable: Boolean = false,
) : FieldMapping<E>

internal data class Constant<E : Any>(
    val value: Boolean,
    override val kind: FieldKind = FieldKind.BOOLEAN,
    override val sortable: Boolean = false,
) : FieldMapping<E>

internal data class AlwaysPresentComplex<E : Any>(
    val name: String,
    override val kind: FieldKind = FieldKind.CASE_EXACT,
    override val sortable: Boolean = false,
) : FieldMapping<E>

internal data class Unsupported<E : Any>(
    val reason: String,
    override val kind: FieldKind = FieldKind.CASE_EXACT,
    override val sortable: Boolean = false,
) : FieldMapping<E>

internal enum class FieldKind {
    /** Compared verbatim; SCIM `id` is caseExact. */
    CASE_EXACT,

    /** Keycloak already stores these lowercased, so only the search term needs folding. */
    STORED_LOWERCASE,

    /** Arbitrary case in the database, so both sides need folding. */
    MIXED_CASE,

    BOOLEAN,
}

internal fun <E : Any> column(
    property: String,
    kind: FieldKind,
    sortable: Boolean = true,
): Column<E> = Column(property, kind, sortable)

internal fun <E : Any> attribute(
    name: String,
    kind: FieldKind,
    multiValued: Boolean = false,
): Attribute<E> = Attribute(name, kind, multiValued)

internal fun <E : Any> constant(value: Boolean): Constant<E> = Constant(value)

internal fun <E : Any> alwaysPresentComplex(name: String): AlwaysPresentComplex<E> = AlwaysPresentComplex(name)

internal fun <E : Any> unsupported(reason: String): Unsupported<E> = Unsupported(reason)
