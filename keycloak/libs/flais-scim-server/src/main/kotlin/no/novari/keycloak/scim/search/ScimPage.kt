package no.novari.keycloak.scim.search

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
