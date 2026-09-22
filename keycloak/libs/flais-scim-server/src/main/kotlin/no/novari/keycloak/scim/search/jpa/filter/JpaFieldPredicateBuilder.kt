package no.novari.keycloak.scim.search.jpa.filter

import com.fasterxml.jackson.databind.node.ValueNode
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Predicate
import no.novari.keycloak.scim.search.jpa.UnsupportedScimSearchException
import no.novari.keycloak.scim.search.jpa.mapping.Attribute
import no.novari.keycloak.scim.search.jpa.mapping.Column
import no.novari.keycloak.scim.search.jpa.mapping.FieldKind
import org.keycloak.models.jpa.entities.UserAttributeEntity
import org.keycloak.models.jpa.entities.UserEntity
import org.keycloak.storage.jpa.JpaHashUtils
import java.util.Locale

internal class JpaFieldPredicateBuilder(
    private val builder: CriteriaBuilder,
    private val query: CriteriaQuery<*>,
    private val user: From<*, UserEntity>,
) {
    fun compare(
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

    fun compare(
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

    fun present(field: Column<*>): Predicate =
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
    fun present(field: Attribute<*>): Predicate = attributePresent(field.name)

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

    private fun escapeLike(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    private fun unsupported(message: String): Nothing =
        throw UnsupportedScimSearchException(
            message,
        )

    private companion object {
        const val LIKE_ESCAPE = '\\'

        /**
         * Length of USER_ATTRIBUTE.VALUE; longer values are stored in
         * LONG_VALUE.
         */
        const val MAX_INDEXED_VALUE_LENGTH =
            255
    }
}
