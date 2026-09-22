package no.novari.keycloak.scim.search

/**
 * Searches the SCIM user collection.
 *
 * Declining a request is part of the contract rather than an exception, because a real
 * implementation can only push a subset of SCIM filters down to its storage. An implementation is
 * never allowed to return a [ScimUserSearchResult.Page] that disagrees with the SCIM evaluator.
 */
internal interface ScimUserSearch {
    fun search(criteria: ScimUserSearchCriteria): ScimUserSearchResult
}
