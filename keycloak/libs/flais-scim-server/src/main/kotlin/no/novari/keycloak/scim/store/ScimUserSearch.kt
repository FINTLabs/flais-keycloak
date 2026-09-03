package no.novari.keycloak.scim.store

import com.unboundid.scim2.common.Path
import com.unboundid.scim2.common.filters.Filter
import org.keycloak.models.UserModel

/**
 * Which slice of the result set to return.
 *
 * Index and keyset paging are mutually exclusive, which is why they are separate types rather than
 * nullable fields on the criteria.
 */
internal sealed interface ScimPage {
    /** SCIM `startIndex`/`count` paging. Simple, but the database must scan and discard the offset. */
    data class Index(
        val firstResult: Int,
        val maxResults: Int?,
    ) : ScimPage

    /**
     * Cursor paging by user id. [after] is the last id of the previous page, or null for the first
     * page.
     *
     * The id is used rather than the sort key because it is immutable — a SCIM `PUT` can rename a
     * user mid-enumeration — and because `USER_GROUP_MEMBERSHIP` is keyed
     * `PRIMARY KEY (GROUP_ID, USER_ID)`, so this is a direct range scan on that index.
     */
    data class Keyset(
        val after: String?,
        val maxResults: Int?,
    ) : ScimPage
}

/**
 * A SCIM user search, scoped to the members of one organization that hold the `scim-managed` role.
 */
internal data class ScimUserSearchCriteria(
    val organizationGroupId: String,
    val scimRoleId: String,
    val page: ScimPage,
    val filter: Filter? = null,
    val sortBy: Path? = null,
    val sortAscending: Boolean = true,
) {
    init {
        // Keyset paging seeks on user id alone, so it cannot resume a result set ordered by
        // something else. Callers must reject this combination as a client error before getting
        // here; reaching it would be a programming mistake.
        require(!(page is ScimPage.Keyset && sortBy != null)) {
            "cursor pagination cannot be combined with sortBy"
        }
    }
}

internal sealed interface ScimUserSearchResult {
    data class Page(
        val users: List<UserModel>,
        val totalResults: Int,
        /** True when more rows match beyond this page, so the caller should emit a next cursor. */
        val hasMore: Boolean,
    ) : ScimUserSearchResult

    /**
     * The search could not be executed as asked. This is never a client error: the caller is
     * expected to fall back to evaluating the request in memory.
     */
    data class Unsupported(
        val reason: String,
    ) : ScimUserSearchResult
}

/**
 * Searches the SCIM user collection.
 *
 * Declining a request is part of the contract rather than an exception, because a real
 * implementation can only push a subset of SCIM filters down to its storage. Callers must handle
 * [ScimUserSearchResult.Unsupported] by producing the answer some other way; an implementation is
 * never allowed to return a [ScimUserSearchResult.Page] that disagrees with the SCIM evaluator.
 */
internal interface ScimUserSearch {
    fun search(criteria: ScimUserSearchCriteria): ScimUserSearchResult
}
