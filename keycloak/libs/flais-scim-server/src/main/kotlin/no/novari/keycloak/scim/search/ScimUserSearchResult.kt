package no.novari.keycloak.scim.search

import org.keycloak.models.UserModel

internal sealed interface ScimUserSearchResult {
    data class Page(
        val users: List<UserModel>,
        val totalResults: Int,
        /** True when more rows match beyond this page, so the caller should emit a next cursor. */
        val hasMore: Boolean,
    ) : ScimUserSearchResult

    /** The search could not be executed as asked. */
    data class Unsupported(
        val reason: String,
    ) : ScimUserSearchResult
}
