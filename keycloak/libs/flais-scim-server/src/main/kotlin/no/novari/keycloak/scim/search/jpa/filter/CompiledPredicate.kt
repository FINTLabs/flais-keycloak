package no.novari.keycloak.scim.search.jpa.filter

import jakarta.persistence.criteria.Predicate

/**
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
