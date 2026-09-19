package no.novari.keycloak.scim.store

import com.fasterxml.jackson.databind.node.ValueNode
import com.unboundid.scim2.common.Path
import com.unboundid.scim2.common.filters.AndFilter
import com.unboundid.scim2.common.filters.ComplexValueFilter
import com.unboundid.scim2.common.filters.ContainsFilter
import com.unboundid.scim2.common.filters.EndsWithFilter
import com.unboundid.scim2.common.filters.EqualFilter
import com.unboundid.scim2.common.filters.Filter
import com.unboundid.scim2.common.filters.FilterVisitor
import com.unboundid.scim2.common.filters.GreaterThanFilter
import com.unboundid.scim2.common.filters.GreaterThanOrEqualFilter
import com.unboundid.scim2.common.filters.LessThanFilter
import com.unboundid.scim2.common.filters.LessThanOrEqualFilter
import com.unboundid.scim2.common.filters.NotEqualFilter
import com.unboundid.scim2.common.filters.NotFilter
import com.unboundid.scim2.common.filters.OrFilter
import com.unboundid.scim2.common.filters.PresentFilter
import com.unboundid.scim2.common.filters.StartsWithFilter
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Predicate
import no.novari.keycloak.scim.mapping.AlwaysPresentComplex
import no.novari.keycloak.scim.mapping.Attribute
import no.novari.keycloak.scim.mapping.Column
import no.novari.keycloak.scim.mapping.ComparisonOp
import no.novari.keycloak.scim.mapping.Constant
import no.novari.keycloak.scim.mapping.ConstantValue
import no.novari.keycloak.scim.mapping.FieldKind
import no.novari.keycloak.scim.mapping.FieldMapping
import no.novari.keycloak.scim.mapping.KeycloakScimSearchMappings
import no.novari.keycloak.scim.mapping.Unsupported
import org.keycloak.models.jpa.entities.UserAttributeEntity
import org.keycloak.models.jpa.entities.UserEntity
import org.keycloak.storage.jpa.JpaHashUtils
import java.util.Locale

internal class UnsupportedScimFilterException(
    message: String,
) : RuntimeException(message)

/**
 * Intermediate representation of a compiled SCIM predicate.
 *
 * Keeping TRUE/FALSE separate from SQL lets us constant-fold filters before
 * creating Criteria predicates.
 */
internal sealed interface CompiledPredicate {
    data object True : CompiledPredicate

    data object False : CompiledPredicate

    data class Sql(
        val predicate: Predicate,
    ) : CompiledPredicate
}

/**
 * Compiles a SCIM filter into a JPA Predicate over UserEntity.
 *
 * Constant fields are evaluated before SQL is generated. This allows expressions
 * such as:
 *
 *   emails.type eq "work" and userName eq "foo"
 *
 * where emails.type is configured as constant("work"), to compile to only the
 * username database predicate.
 */

internal class ScimFilterCompiler(
    private val builder: CriteriaBuilder,
    private val query: CriteriaQuery<*>,
    private val user: From<*, UserEntity>,
) : FilterVisitor<CompiledPredicate, Path> {
    fun compile(filter: Filter): Predicate =
        filter
            .visit(this, Path.root())
            .toPredicate()

    override fun visit(
        filter: EqualFilter,
        param: Path,
    ): CompiledPredicate = compare(filter, ComparisonOp.EQ, param)

    override fun visit(
        filter: NotEqualFilter,
        param: Path,
    ): CompiledPredicate = compare(filter, ComparisonOp.NE, param)

    override fun visit(
        filter: ContainsFilter,
        param: Path,
    ): CompiledPredicate = compare(filter, ComparisonOp.CO, param)

    override fun visit(
        filter: StartsWithFilter,
        param: Path,
    ): CompiledPredicate = compare(filter, ComparisonOp.SW, param)

    override fun visit(
        filter: EndsWithFilter,
        param: Path,
    ): CompiledPredicate = compare(filter, ComparisonOp.EW, param)

    override fun visit(
        filter: GreaterThanFilter,
        param: Path,
    ): CompiledPredicate = compare(filter, ComparisonOp.GT, param)

    override fun visit(
        filter: GreaterThanOrEqualFilter,
        param: Path,
    ): CompiledPredicate = compare(filter, ComparisonOp.GE, param)

    override fun visit(
        filter: LessThanFilter,
        param: Path,
    ): CompiledPredicate = compare(filter, ComparisonOp.LT, param)

    override fun visit(
        filter: LessThanOrEqualFilter,
        param: Path,
    ): CompiledPredicate = compare(filter, ComparisonOp.LE, param)

    /*
     * TRUE and x  -> x
     * FALSE and x -> FALSE
     *
     * We iterate rather than map() so FALSE can short-circuit compilation of
     * all remaining branches.
     */
    override fun visit(
        filter: AndFilter,
        param: Path,
    ): CompiledPredicate {
        val predicates =
            mutableListOf<Predicate>()

        for (filterPart in filter.combinedFilters) {
            when (val compiled = filterPart.visit(this, param)) {
                CompiledPredicate.True ->
                    Unit

                CompiledPredicate.False ->
                    return CompiledPredicate.False

                is CompiledPredicate.Sql ->
                    predicates += compiled.predicate
            }
        }

        return when (predicates.size) {
            0 ->
                CompiledPredicate.True

            1 ->
                CompiledPredicate.Sql(predicates.single())

            else ->
                CompiledPredicate.Sql(
                    builder.and(*predicates.toTypedArray()),
                )
        }
    }

    /*
     * TRUE or x  -> TRUE
     * FALSE or x -> x
     */
    override fun visit(
        filter: OrFilter,
        param: Path,
    ): CompiledPredicate {
        val predicates =
            mutableListOf<Predicate>()

        for (filterPart in filter.combinedFilters) {
            when (val compiled = filterPart.visit(this, param)) {
                CompiledPredicate.True ->
                    return CompiledPredicate.True

                CompiledPredicate.False ->
                    Unit

                is CompiledPredicate.Sql ->
                    predicates += compiled.predicate
            }
        }

        return when (predicates.size) {
            0 ->
                CompiledPredicate.False

            1 ->
                CompiledPredicate.Sql(predicates.single())

            else ->
                CompiledPredicate.Sql(
                    builder.or(*predicates.toTypedArray()),
                )
        }
    }

    override fun visit(
        filter: NotFilter,
        param: Path,
    ): CompiledPredicate =
        when (
            val compiled =
                filter.invertedFilter.visit(this, param)
        ) {
            CompiledPredicate.True ->
                CompiledPredicate.False

            CompiledPredicate.False ->
                CompiledPredicate.True

            is CompiledPredicate.Sql ->
                CompiledPredicate.Sql(
                    builder.not(compiled.predicate),
                )
        }

    override fun visit(
        filter: PresentFilter,
        param: Path,
    ): CompiledPredicate =
        withPath(filter.attributePath, param) { path ->
            when (val field = resolve(path)) {
            /*
             * A configured constant is always emitted, so `pr` is known
             * without touching the database.
             */
                is Constant<*> ->
                    CompiledPredicate.True

                is AlwaysPresentComplex<*> ->
                    CompiledPredicate.True

                is Attribute<*> ->
                    CompiledPredicate.Sql(
                        attributePresent(field.name),
                    )

                is Column<*> ->
                    CompiledPredicate.Sql(
                        presentColumn(field),
                    )

                is Unsupported<*> ->
                    unsupported(field.reason)
            }
        }

    override fun visit(
        filter: ComplexValueFilter,
        param: Path,
    ): CompiledPredicate =
        withPath(filter.attributePath, param) { path ->
            compileValueFilter(path, filter.valueFilter)
        }

    private fun compileValueFilter(
        path: Path,
        filter: Filter,
    ): CompiledPredicate {
        // Flattening is safe only for a complex mapping with one emitted entry.
        if (resolve(path) !is AlwaysPresentComplex<*>) {
            unsupported("value filters on '$path' require a single complex entry")
        }
        return filter.visit(this, path)
    }

    private fun withPath(
        path: Path?,
        scope: Path,
        compile: (Path) -> CompiledPredicate,
    ): CompiledPredicate {
        val relative = path ?: unsupported("filter has no attribute path")
        if (!scope.isRoot && relative.schemaUrn != null) {
            unsupported("value filter attribute '$relative' must be relative")
        }
        val absolute = if (scope.isRoot) relative else scope.attribute(relative)
        return compile(absolute)
    }

    private fun compare(
        filter: Filter,
        op: ComparisonOp,
        scope: Path,
    ): CompiledPredicate =
        withPath(filter.attributePath, scope) { path ->
            val field =
                resolve(path)

            val value =
                filter.comparisonValue
                    ?: unsupported(
                        "filter '$filter' has no comparison value",
                    )

            when (field) {
                is Constant<*> ->
                    compareConstant(
                        field = field,
                        op = op,
                        value = value,
                    )

                is AlwaysPresentComplex<*> ->
                    unsupported(
                        "complex attribute '${field.name}' cannot be compared in the database",
                    )

                is Column<*> ->
                    CompiledPredicate.Sql(
                        compareColumn(
                            field = field,
                            op = op,
                            value = value,
                        ),
                    )

                is Attribute<*> ->
                    CompiledPredicate.Sql(
                        compareAttribute(
                            field = field,
                            op = op,
                            value = value,
                        ),
                    )

                is Unsupported<*> ->
                    unsupported(field.reason)
            }
        }

    /**
     * Constant fields never need SQL.
     *
     * Examples:
     *
     *   constant(true)
     *
     *     primary eq true   -> TRUE
     *     primary eq false  -> FALSE
     *
     *   constant("work", CASE_EXACT)
     *
     *     type eq "work"    -> TRUE
     *     type eq "home"    -> FALSE
     */
    private fun compareConstant(
        field: Constant<*>,
        op: ComparisonOp,
        value: ValueNode,
    ): CompiledPredicate =
        when (val constant = field.value) {
            is ConstantValue.Bool ->
                evaluateBoolean(
                    constant = constant.value,
                    op = op,
                    comparisonValue = value,
                )

            is ConstantValue.Text ->
                evaluateString(
                    constant = constant.value,
                    kind = field.kind,
                    op = op,
                    comparisonValue = value,
                )
        }.compiled()

    private fun compareColumn(
        field: Column<*>,
        op: ComparisonOp,
        value: ValueNode,
    ): Predicate {
        if (field.kind == FieldKind.BOOLEAN) {
            if (!value.isBoolean) {
                unsupported("'$value' is not a boolean")
            }

            val wanted =
                value.booleanValue()

            return when (op) {
                ComparisonOp.EQ ->
                    nullSafe(field.property) { column ->
                        builder.equal(
                            column,
                            wanted,
                        )
                    }

                ComparisonOp.NE ->
                    nullableColumn(field.property) { column ->
                        builder.notEqual(
                            column,
                            wanted,
                        )
                    }

                else ->
                    unsupported(
                        "operator $op is not defined for a boolean attribute",
                    )
            }
        }

        val needle =
            text(value)

        val predicate = { column: Expression<Any> ->
            stringPredicate(
                raw = column.`as`(String::class.java),
                kind = field.kind,
                op = op,
                value = needle,
            )
        }

        return if (op == ComparisonOp.NE) {
            nullableColumn(
                field.property,
                predicate,
            )
        } else {
            nullSafe(
                field.property,
                predicate,
            )
        }
    }

    private fun compareAttribute(
        field: Attribute<*>,
        op: ComparisonOp,
        value: ValueNode,
    ): Predicate {
        if (field.kind == FieldKind.BOOLEAN) {
            if (!value.isBoolean) {
                unsupported("'$value' is not a boolean")
            }

            val wanted =
                value.booleanValue()

            return when (op) {
                ComparisonOp.EQ ->
                    attributeNullSafe(
                        attributeName = field.name,
                        predicateShort = { column ->
                            builder.equal(
                                column,
                                wanted.toString(),
                            )
                        },
                    )

                ComparisonOp.NE ->
                    nullableAttribute(
                        attributeName = field.name,
                        predicateShort = { column ->
                            builder.notEqual(
                                column,
                                wanted.toString(),
                            )
                        },
                    )

                else ->
                    unsupported(
                        "operator $op is not defined for a boolean attribute",
                    )
            }
        }

        val needle =
            text(value)

        val predicateShort = { stored: Expression<String> ->
            stringPredicate(
                raw = stored,
                kind = field.kind,
                op = op,
                value = needle,
            )
        }

        val predicateLong = {
            stored: Expression<String>,
            hash: Expression<ByteArray>,
            lowerCaseHash: Expression<ByteArray>,
            ->
            longAttributePredicate(
                raw = stored,
                hash = hash,
                lowerCaseHash = lowerCaseHash,
                kind = field.kind,
                op = op,
                value = needle,
            )
        }

        return if (op == ComparisonOp.NE) {
            nullableAttribute(
                attributeName = field.name,
                predicateShort = predicateShort,
                predicateLong = predicateLong,
            )
        } else {
            attributeNullSafe(
                attributeName = field.name,
                predicateShort = predicateShort,
                predicateLong = predicateLong,
            )
        }
    }

    private fun presentColumn(field: Column<*>): Predicate =
        builder.isNotNull(
            user.get<Any>(field.property),
        )

    private fun stringPredicate(
        raw: Expression<String>,
        kind: FieldKind,
        op: ComparisonOp,
        value: String,
    ): Predicate {
        val expression: Expression<String>
        val needle: String

        when (kind) {
            FieldKind.CASE_EXACT -> {
                expression = raw
                needle = value
            }

            FieldKind.STORED_LOWERCASE -> {
                expression = raw
                needle =
                    value.lowercase(Locale.ROOT)
            }

            FieldKind.MIXED_CASE -> {
                expression =
                    builder.lower(raw)

                needle =
                    value.lowercase(Locale.ROOT)
            }

            FieldKind.BOOLEAN ->
                unsupported(
                    "boolean attribute compared as text",
                )
        }

        return when (op) {
            ComparisonOp.EQ ->
                builder.equal(
                    expression,
                    needle,
                )

            ComparisonOp.NE ->
                builder.notEqual(
                    expression,
                    needle,
                )

            ComparisonOp.CO ->
                builder.like(
                    expression,
                    "%${escapeLike(needle)}%",
                    LIKE_ESCAPE,
                )

            ComparisonOp.SW ->
                builder.like(
                    expression,
                    "${escapeLike(needle)}%",
                    LIKE_ESCAPE,
                )

            ComparisonOp.EW ->
                builder.like(
                    expression,
                    "%${escapeLike(needle)}",
                    LIKE_ESCAPE,
                )

            ComparisonOp.GT ->
                builder.greaterThan(
                    expression,
                    needle,
                )

            ComparisonOp.GE ->
                builder.greaterThanOrEqualTo(
                    expression,
                    needle,
                )

            ComparisonOp.LT ->
                builder.lessThan(
                    expression,
                    needle,
                )

            ComparisonOp.LE ->
                builder.lessThanOrEqualTo(
                    expression,
                    needle,
                )
        }
    }

    private fun longAttributePredicate(
        raw: Expression<String>,
        hash: Expression<ByteArray>,
        lowerCaseHash: Expression<ByteArray>,
        kind: FieldKind,
        op: ComparisonOp,
        value: String,
    ): Predicate =
        when (op) {
            ComparisonOp.EQ ->
                builder.equal(
                    longHashExpression(
                        kind,
                        hash,
                        lowerCaseHash,
                    ),
                    longHashValue(
                        kind,
                        value,
                    ),
                )

            ComparisonOp.NE ->
                builder.notEqual(
                    longHashExpression(
                        kind,
                        hash,
                        lowerCaseHash,
                    ),
                    longHashValue(
                        kind,
                        value,
                    ),
                )

            else ->
                stringPredicate(
                    raw = raw,
                    kind = kind,
                    op = op,
                    value = value,
                )
        }

    private fun longHashExpression(
        kind: FieldKind,
        hash: Expression<ByteArray>,
        lowerCaseHash: Expression<ByteArray>,
    ): Expression<ByteArray> =
        when (kind) {
            FieldKind.CASE_EXACT ->
                hash

            FieldKind.STORED_LOWERCASE,
            FieldKind.MIXED_CASE,
            ->
                lowerCaseHash

            FieldKind.BOOLEAN ->
                unsupported(
                    "boolean attribute compared as text",
                )
        }

    private fun longHashValue(
        kind: FieldKind,
        value: String,
    ): ByteArray =
        when (kind) {
            FieldKind.CASE_EXACT ->
                JpaHashUtils.hashForAttributeValue(
                    value,
                )

            FieldKind.STORED_LOWERCASE,
            FieldKind.MIXED_CASE,
            ->
                JpaHashUtils.hashForAttributeValueLowerCase(
                    value,
                )

            FieldKind.BOOLEAN ->
                unsupported(
                    "boolean attribute compared as text",
                )
        }

    /**
     * Correlated EXISTS over UserAttributeEntity.
     *
     * A join would multiply rows for multivalued attributes and behave badly
     * under negation/disjunction.
     */
    private fun attributePresent(name: String): Predicate = attributeNullSafe(name)

    private fun attributeNullSafe(
        attributeName: String,
        predicateShort: (Expression<String>) -> Predicate = {
            builder.conjunction()
        },
        predicateLong: (
            Expression<String>,
            Expression<ByteArray>,
            Expression<ByteArray>,
        ) -> Predicate = { _, _, _ ->
            builder.conjunction()
        },
    ): Predicate {
        val subquery =
            query.subquery(String::class.java)

        val attribute =
            subquery.from(
                UserAttributeEntity::class.java,
            )

        val stored =
            attribute.get<String>("value")

        val longStored =
            attribute.get<String>("longValue")

        val longStoredHash =
            attribute.get<ByteArray>(
                "longValueHash",
            )

        val longStoredLowerCaseHash =
            attribute.get<ByteArray>(
                "longValueHashLowerCase",
            )

        subquery.select(
            attribute.get("name"),
        )

        subquery.where(
            builder.equal(
                attribute.get<Any>("user"),
                user,
            ),
            builder.equal(
                attribute.get<String>("name"),
                attributeName,
            ),
            builder.or(
                builder.isNotNull(stored),
                builder.isNotNull(longStored),
            ),
            builder.or(
                predicateShort(stored),
                predicateLong(
                    longStored,
                    longStoredHash,
                    longStoredLowerCaseHash,
                ),
            ),
        )

        return builder.exists(subquery)
    }

    private fun nullableAttribute(
        attributeName: String,
        predicateShort: (Expression<String>) -> Predicate = {
            builder.conjunction()
        },
        predicateLong: (
            Expression<String>,
            Expression<ByteArray>,
            Expression<ByteArray>,
        ) -> Predicate = { _, _, _ ->
            builder.conjunction()
        },
    ): Predicate =
        builder.or(
            builder.not(
                attributePresent(attributeName),
            ),
            attributeNullSafe(
                attributeName = attributeName,
                predicateShort = predicateShort,
                predicateLong = predicateLong,
            ),
        )

    /**
     * Guards a column comparison so the result is FALSE rather than UNKNOWN
     * when the column is NULL.
     */
    private fun nullSafe(
        property: String,
        predicate: (Expression<Any>) -> Predicate,
    ): Predicate {
        val column =
            user.get<Any>(property)

        return builder.and(
            builder.isNotNull(column),
            predicate(column),
        )
    }

    /**
     * Missing attributes compare as not-equal according to the SCIM evaluator,
     * so nullable NE needs explicit handling.
     */
    private fun nullableColumn(
        property: String,
        predicate: (Expression<Any>) -> Predicate,
    ): Predicate {
        val column =
            user.get<Any>(property)

        return builder.or(
            builder.isNull(column),
            predicate(column),
        )
    }

    private fun resolve(path: Path?): FieldMapping<UserEntity> {
        val resolved =
            path
                ?: unsupported(
                    "filter has no attribute path",
                )

        return KeycloakScimSearchMappings.users
            .resolve(resolved)
            ?: unsupported(
                "attribute '$resolved' cannot be resolved",
            )
    }

    private fun text(value: ValueNode): String {
        if (!value.isTextual) {
            unsupported(
                "'$value' is not a string",
            )
        }

        val text =
            value.textValue()

        if (text.length > MAX_INDEXED_VALUE_LENGTH) {
            unsupported(
                "comparison values longer than " +
                    "$MAX_INDEXED_VALUE_LENGTH characters are stored hashed",
            )
        }

        return text
    }

    private fun evaluateBoolean(
        constant: Boolean,
        op: ComparisonOp,
        comparisonValue: ValueNode,
    ): Boolean {
        require(comparisonValue.isBoolean) {
            "'$comparisonValue' is not a boolean"
        }

        val comparison =
            comparisonValue.booleanValue()

        return when (op) {
            ComparisonOp.EQ -> constant == comparison
            ComparisonOp.NE -> constant != comparison

            else ->
                error("Operator $op is not supported for boolean constants")
        }
    }

    private fun evaluateString(
        constant: String,
        kind: FieldKind,
        op: ComparisonOp,
        comparisonValue: ValueNode,
    ): Boolean {
        require(comparisonValue.isTextual) {
            "'$comparisonValue' is not a string"
        }

        val comparison =
            comparisonValue.textValue()

        val (left, right) =
            when (kind) {
                FieldKind.CASE_EXACT ->
                    constant to comparison

                FieldKind.STORED_LOWERCASE ->
                    constant to comparison.lowercase(Locale.ROOT)

                FieldKind.MIXED_CASE ->
                    constant.lowercase(Locale.ROOT) to
                        comparison.lowercase(Locale.ROOT)

                FieldKind.BOOLEAN ->
                    error("BOOLEAN kind used with string constant")
            }

        return when (op) {
            ComparisonOp.EQ -> left == right
            ComparisonOp.NE -> left != right
            ComparisonOp.CO -> left.contains(right)
            ComparisonOp.SW -> left.startsWith(right)
            ComparisonOp.EW -> left.endsWith(right)
            ComparisonOp.GT -> left > right
            ComparisonOp.GE -> left >= right
            ComparisonOp.LT -> left < right
            ComparisonOp.LE -> left <= right
        }
    }

    private fun escapeLike(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    private fun Boolean.compiled(): CompiledPredicate =
        if (this) {
            CompiledPredicate.True
        } else {
            CompiledPredicate.False
        }

    private fun CompiledPredicate.toPredicate(): Predicate =
        when (this) {
            CompiledPredicate.True ->
                builder.conjunction()

            CompiledPredicate.False ->
                builder.disjunction()

            is CompiledPredicate.Sql ->
                predicate
        }

    private fun unsupported(message: String): Nothing =
        throw UnsupportedScimFilterException(
            message,
        )

    private companion object {
        const val LIKE_ESCAPE = '\\'

        const val RAW_ROLE_ATTRIBUTE =
            "rawRoles"

        const val ROLE_ATTRIBUTE =
            "roles"

        /**
         * Length of USER_ATTRIBUTE.VALUE; longer values are stored in
         * LONG_VALUE.
         */
        const val MAX_INDEXED_VALUE_LENGTH =
            255
    }
}
