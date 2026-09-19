package no.novari.keycloak.scim.search.jpa.filter

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
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Predicate
import no.novari.keycloak.scim.search.jpa.UnsupportedScimSearchException
import no.novari.keycloak.scim.search.jpa.mapping.AlwaysPresentComplex
import no.novari.keycloak.scim.search.jpa.mapping.Attribute
import no.novari.keycloak.scim.search.jpa.mapping.Column
import no.novari.keycloak.scim.search.jpa.mapping.Constant
import no.novari.keycloak.scim.search.jpa.mapping.FieldMapping
import no.novari.keycloak.scim.search.jpa.mapping.KeycloakScimSearchMappings
import no.novari.keycloak.scim.search.jpa.mapping.Unsupported
import org.keycloak.models.jpa.entities.UserEntity

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
    query: CriteriaQuery<*>,
    user: From<*, UserEntity>,
) : FilterVisitor<CompiledPredicate, Path> {
    private val predicates = JpaFieldPredicateBuilder(builder, query, user)

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
                        predicates.present(field),
                    )

                is Column<*> ->
                    CompiledPredicate.Sql(
                        predicates.present(field),
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
                    ScimConstantEvaluator
                        .evaluate(
                            field = field,
                            op = op,
                            value = value,
                        ).compiled()

                is AlwaysPresentComplex<*> ->
                    unsupported(
                        "complex attribute '${field.name}' cannot be compared in the database",
                    )

                is Column<*> ->
                    CompiledPredicate.Sql(
                        predicates.compare(
                            field = field,
                            op = op,
                            value = value,
                        ),
                    )

                is Attribute<*> ->
                    CompiledPredicate.Sql(
                        predicates.compare(
                            field = field,
                            op = op,
                            value = value,
                        ),
                    )

                is Unsupported<*> ->
                    unsupported(field.reason)
            }
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
        throw UnsupportedScimSearchException(
            message,
        )
}
