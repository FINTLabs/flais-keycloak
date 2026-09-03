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
import org.keycloak.models.jpa.entities.UserAttributeEntity
import org.keycloak.models.jpa.entities.UserEntity

/**
 * Raised when a filter cannot be translated to SQL. Callers are expected to fall back to in-memory
 * evaluation rather than surfacing this to the client.
 */
internal class UnsupportedScimFilterException(
    message: String,
) : RuntimeException(message)

/**
 * Compiles a SCIM filter into a JPA [Predicate] over `UserEntity`.
 *
 * This is an optimization with an escape hatch, not a second implementation of SCIM. Whenever the
 * database cannot reproduce the SDK's in-memory semantics exactly, it throws
 * [UnsupportedScimFilterException] so the caller falls back to the evaluator that defines the
 * correct answer. It must never return a predicate that disagrees with that evaluator.
 *
 * ### Null handling
 *
 * SQL uses three-valued logic: `NOT (email = 'x')` is UNKNOWN when `email` is NULL, which drops the
 * row. The SCIM evaluator instead treats equality-style predicates over missing attributes as
 * ordinary booleans: `eq` is false, `ne` is true, and `not(...)` then negates that boolean. Every
 * nullable leaf predicate here must therefore return TRUE or FALSE explicitly rather than UNKNOWN.
 *
 * ### User attributes
 *
 * Keycloak stores user attribute strings longer than 255 characters in `LONG_VALUE` instead of
 * `VALUE`. Attribute presence can still be compiled exactly by checking both columns, but text
 * comparisons fall back to the SDK evaluator because the native query cannot safely ignore
 * `LONG_VALUE`.
 */
internal class ScimFilterCompiler(
    private val builder: CriteriaBuilder,
    private val query: CriteriaQuery<*>,
    private val user: From<*, UserEntity>,
) : FilterVisitor<Predicate, Unit> {
    private enum class Op { EQ, NE, CO, SW, EW, GT, GE, LT, LE }

    fun compile(filter: Filter): Predicate = filter.visit(this, Unit)

    override fun visit(
        filter: EqualFilter,
        param: Unit,
    ): Predicate = compare(filter, Op.EQ)

    override fun visit(
        filter: NotEqualFilter,
        param: Unit,
    ): Predicate = compare(filter, Op.NE)

    override fun visit(
        filter: ContainsFilter,
        param: Unit,
    ): Predicate = compare(filter, Op.CO)

    override fun visit(
        filter: StartsWithFilter,
        param: Unit,
    ): Predicate = compare(filter, Op.SW)

    override fun visit(
        filter: EndsWithFilter,
        param: Unit,
    ): Predicate = compare(filter, Op.EW)

    override fun visit(
        filter: GreaterThanFilter,
        param: Unit,
    ): Predicate = compare(filter, Op.GT)

    override fun visit(
        filter: GreaterThanOrEqualFilter,
        param: Unit,
    ): Predicate = compare(filter, Op.GE)

    override fun visit(
        filter: LessThanFilter,
        param: Unit,
    ): Predicate = compare(filter, Op.LT)

    override fun visit(
        filter: LessThanOrEqualFilter,
        param: Unit,
    ): Predicate = compare(filter, Op.LE)

    override fun visit(
        filter: AndFilter,
        param: Unit,
    ): Predicate = builder.and(*filter.combinedFilters.map { it.visit(this, param) }.toTypedArray())

    override fun visit(
        filter: OrFilter,
        param: Unit,
    ): Predicate = builder.or(*filter.combinedFilters.map { it.visit(this, param) }.toTypedArray())

    override fun visit(
        filter: NotFilter,
        param: Unit,
    ): Predicate = builder.not(filter.invertedFilter.visit(this, param))

    override fun visit(
        filter: PresentFilter,
        param: Unit,
    ): Predicate =
        when (val field = resolve(filter.attributePath)) {
            is UserField.Constant -> constant(true)
            is UserField.Attribute ->
                attributePresent(field.name)

            is UserField.Column ->
                presentColumn(field)
        }

    /**
     * Value filters such as `emails[value sw "a"]` are not compiled. The common
     * `emails[primary eq true].value` form is handled during path resolution instead, so reaching
     * here means a shape worth deferring to the evaluator.
     */
    override fun visit(
        filter: ComplexValueFilter,
        param: Unit,
    ): Predicate = unsupported("value filter '$filter' is not compiled to SQL")

    private fun compare(
        filter: Filter,
        op: Op,
    ): Predicate {
        val field = resolve(filter.attributePath)
        val value = filter.comparisonValue ?: unsupported("filter '$filter' has no comparison value")

        return when (field) {
            is UserField.Constant -> compareConstant(field, op, value)
            is UserField.Column -> compareColumn(field, op, value)
            is UserField.Attribute -> unsupported("attribute '${filter.attributePath}' may be stored in LONG_VALUE")
        }
    }

    private fun compareConstant(
        field: UserField.Constant,
        op: Op,
        value: ValueNode,
    ): Predicate {
        if (!value.isBoolean) unsupported("'$value' is not a boolean")
        return when (op) {
            Op.EQ -> constant(field.value == value.booleanValue())
            Op.NE -> constant(field.value != value.booleanValue())
            else -> unsupported("operator $op is not defined for a boolean attribute")
        }
    }

    private fun compareColumn(
        field: UserField.Column,
        op: Op,
        value: ValueNode,
    ): Predicate {
        if (field.kind == UserField.Kind.BOOLEAN) {
            if (!value.isBoolean) unsupported("'$value' is not a boolean")
            val wanted = value.booleanValue()
            return when (op) {
                Op.EQ -> nullSafe(field.property) { column -> builder.equal(column, wanted) }
                Op.NE -> nullableColumn(field.property) { column -> builder.notEqual(column, wanted) }
                else -> unsupported("operator $op is not defined for a boolean attribute")
            }
        }

        val needle = text(value)
        val predicate = { column: Expression<Any> ->
            stringPredicate(column.`as`(String::class.java), field.kind, op, needle)
        }
        return if (op == Op.NE) {
            nullableColumn(field.property, predicate)
        } else {
            nullSafe(field.property, predicate)
        }
    }

    private fun presentColumn(field: UserField.Column): Predicate {
        if (field.kind == UserField.Kind.BOOLEAN) {
            return nullSafe(field.property) { builder.conjunction() }
        }

        return nullSafe(field.property) { column -> builder.notEqual(column, "") }
    }

    private fun stringPredicate(
        raw: Expression<String>,
        kind: UserField.Kind,
        op: Op,
        value: String,
    ): Predicate {
        val expression: Expression<String>
        val needle: String
        when (kind) {
            UserField.Kind.CASE_EXACT -> {
                expression = raw
                needle = value
            }

            // Already folded in storage, so folding the column again would defeat the index.
            UserField.Kind.STORED_LOWERCASE -> {
                expression = raw
                needle = value.lowercase()
            }

            UserField.Kind.MIXED_CASE -> {
                expression = builder.lower(raw)
                needle = value.lowercase()
            }

            UserField.Kind.BOOLEAN -> unsupported("boolean attribute compared as text")
        }

        return when (op) {
            Op.EQ -> builder.equal(expression, needle)
            Op.NE -> builder.notEqual(expression, needle)
            Op.CO -> builder.like(expression, "%${escapeLike(needle)}%", LIKE_ESCAPE)
            Op.SW -> builder.like(expression, "${escapeLike(needle)}%", LIKE_ESCAPE)
            Op.EW -> builder.like(expression, "%${escapeLike(needle)}", LIKE_ESCAPE)
            Op.GT -> builder.greaterThan(expression, needle)
            Op.GE -> builder.greaterThanOrEqualTo(expression, needle)
            Op.LT -> builder.lessThan(expression, needle)
            Op.LE -> builder.lessThanOrEqualTo(expression, needle)
        }
    }

    /**
     * Correlated `EXISTS` over `UserAttributeEntity`. A join would be wrong here: it multiplies rows
     * for multivalued attributes and breaks under negation and disjunction.
     */
    private fun attributePresent(name: String): Predicate {
        val subquery = query.subquery(String::class.java)
        val attribute = subquery.from(UserAttributeEntity::class.java)
        val stored = attribute.get<String>("value")
        val longStored = attribute.get<String>("longValue")

        subquery.select(attribute.get("name"))
        subquery.where(
            builder.equal(attribute.get<Any>("user"), user),
            builder.equal(attribute.get<String>("name"), name),
            builder.or(
                builder.isNotNull(stored),
                builder.isNotNull(longStored),
            ),
        )

        return builder.exists(subquery)
    }

    /**
     * Guards a column comparison so the result is FALSE, never UNKNOWN, when the column is NULL.
     * See the null-handling note on the class.
     */
    private fun nullSafe(
        property: String,
        predicate: (Expression<Any>) -> Predicate,
    ): Predicate {
        val column = user.get<Any>(property)
        return builder.and(builder.isNotNull(column), predicate(column))
    }

    /**
     * Guards nullable `ne` comparisons so missing values compare as not equal, matching the SDK
     * evaluator's treatment of absent attributes.
     */
    private fun nullableColumn(
        property: String,
        predicate: (Expression<Any>) -> Predicate,
    ): Predicate {
        val column = user.get<Any>(property)
        return builder.or(builder.isNull(column), predicate(column))
    }

    private fun constant(value: Boolean): Predicate = if (value) builder.conjunction() else builder.disjunction()

    private fun resolve(path: Path?): UserField {
        val resolved = path ?: unsupported("filter has no attribute path")
        return ScimUserFields.resolve(resolved)
            ?: unsupported("attribute '$resolved' cannot be resolved to Keycloak storage")
    }

    private fun text(value: ValueNode): String {
        if (!value.isTextual) unsupported("'$value' is not a string")
        val text = value.textValue()
        if (text.length > MAX_INDEXED_VALUE_LENGTH) {
            unsupported("comparison values longer than $MAX_INDEXED_VALUE_LENGTH characters are stored hashed")
        }
        return text
    }

    private fun escapeLike(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    private fun unsupported(message: String): Nothing = throw UnsupportedScimFilterException(message)

    private companion object {
        const val LIKE_ESCAPE = '\\'

        /** Length of `USER_ATTRIBUTE.VALUE`; longer values are stored in `LONG_VALUE`. */
        const val MAX_INDEXED_VALUE_LENGTH = 255
    }
}
