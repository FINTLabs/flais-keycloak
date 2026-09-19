package no.novari.keycloak.scim.search

import com.unboundid.scim2.common.Path
import com.unboundid.scim2.common.filters.Filter

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
