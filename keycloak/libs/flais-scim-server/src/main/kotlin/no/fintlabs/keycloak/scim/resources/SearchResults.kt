package no.fintlabs.keycloak.scim.resources

import com.unboundid.scim2.common.ScimResource

data class SearchResults<T : ScimResource>(
    val results: List<T> = emptyList(),
    val itemsPerPage: Int = 0,
    val totalResults: Int = 0,
    val startIndex: Int = 0,
) {
    val schemas = listOf("urn:ietf:params:scim:api:messages:2.0:ListResponse")
}
