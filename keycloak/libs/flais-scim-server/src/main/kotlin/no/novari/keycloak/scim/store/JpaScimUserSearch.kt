package no.novari.keycloak.scim.store

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.keycloak.connections.jpa.JpaConnectionProvider
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.jpa.entities.UserEntity
import org.keycloak.models.jpa.entities.UserGroupMembershipEntity
import org.keycloak.models.jpa.entities.UserRoleMappingEntity

/**
 * Read-only, database-backed [ScimUserSearch].
 *
 * The SCIM `/Users` collection is the set of users that are members of an organization's internal
 * group **and** hold the `scim-managed` realm role, optionally narrowed by a SCIM filter. That
 * combination cannot be expressed through Keycloak's public provider APIs:
 *
 * - [org.keycloak.organization.OrganizationProvider.getMembersCount] takes no filter argument.
 * - [org.keycloak.models.UserProvider.getUsersCount] has no organization or role predicate, and the
 *   `groupIds` overload is silently ignored unless fine-grained admin permissions are active.
 * - `searchForUserStream` only ANDs its parameters and exposes a single global `exact` flag, so it
 *   cannot express `or`, `not`, or mixed operators.
 *
 * Without this query, `totalResults` can only be produced by materializing every organization
 * member and counting in memory.
 *
 * Rows are resolved back to [org.keycloak.models.UserModel] through [org.keycloak.models.UserProvider]
 * so that caching, federation and model semantics are preserved. Nothing here mutates Keycloak
 * tables.
 *
 * Coupling note: this depends on Keycloak's internal JPA entities and is therefore tied to the
 * local JPA user storage. It is written against the Criteria API rather than JPQL strings so that a
 * Keycloak upgrade which changes these entities fails at compile time instead of at runtime.
 */
internal class JpaScimUserSearch(
    private val session: KeycloakSession,
    private val realm: RealmModel,
) : ScimUserSearch {
    private val entityManager
        get() = session.getProvider(JpaConnectionProvider::class.java).entityManager

    override fun search(criteria: ScimUserSearchCriteria): ScimUserSearchResult {
        val sort =
            try {
                resolveSort(criteria)
            } catch (e: UnsupportedScimFilterException) {
                return ScimUserSearchResult.Unsupported(e.message.orEmpty())
            }

        return try {
            val totalResults = count(criteria)
            val pageSize = criteria.page.maxResults
            if (pageSize == 0) {
                return ScimUserSearchResult.Page(emptyList(), totalResults, hasMore = false)
            }

            // One extra row tells us whether a next cursor is warranted, without a second query.
            val fetchLimit = pageSize?.let { if (it > 0) it + 1 else it }
            val ids = findIds(criteria, sort, fetchLimit)

            val hasMore = pageSize != null && pageSize > 0 && ids.size > pageSize
            val pageIds = if (hasMore) ids.take(pageSize) else ids

            val userProvider = session.users()
            val users = pageIds.mapNotNull { userProvider.getUserById(realm, it) }

            ScimUserSearchResult.Page(users, totalResults, hasMore)
        } catch (e: UnsupportedScimFilterException) {
            ScimUserSearchResult.Unsupported(e.message.orEmpty())
        }
    }

    private val ScimPage.maxResults: Int?
        get() =
            when (this) {
                is ScimPage.Index -> maxResults
                is ScimPage.Keyset -> maxResults
            }

    private fun resolveSort(criteria: ScimUserSearchCriteria): ScimSort? {
        val sortBy = criteria.sortBy ?: return null
        val column =
            ScimUserFields.resolveSortColumn(sortBy)
                ?: throw UnsupportedScimFilterException("cannot sort by '$sortBy' in the database")

        return ScimSort(column, criteria.sortAscending)
    }

    private fun count(criteria: ScimUserSearchCriteria): Int {
        val em = entityManager
        val builder = em.criteriaBuilder
        val query = builder.createQuery(Long::class.java)
        val membership = query.from(UserGroupMembershipEntity::class.java)
        val user = membership.join<UserGroupMembershipEntity, UserEntity>("user")

        query.select(builder.countDistinct(user))
        // The count spans the whole result set, so the keyset position must not be applied here.
        query.where(*restrictions(builder, query, membership, user, criteria, after = null))

        return em.createQuery(query).singleResult.toInt()
    }

    private fun findIds(
        criteria: ScimUserSearchCriteria,
        sort: ScimSort?,
        limit: Int?,
    ): List<String> {
        val em = entityManager
        val builder = em.criteriaBuilder
        val query = builder.createQuery(String::class.java)
        val membership = query.from(UserGroupMembershipEntity::class.java)
        val user = membership.join<UserGroupMembershipEntity, UserEntity>("user")

        val after = (criteria.page as? ScimPage.Keyset)?.after

        // (USER_ID, GROUP_ID) is the composite key of UserGroupMembershipEntity and groupId is
        // fixed, so a user can appear at most once. No DISTINCT is needed, which keeps the ORDER BY
        // valid across all supported databases.
        query.select(user.get("id"))
        query.where(*restrictions(builder, query, membership, user, criteria, after))
        query.orderBy(ordering(builder, user, sort))

        val typedQuery = em.createQuery(query)
        if (criteria.page is ScimPage.Index && criteria.page.firstResult > 0) {
            typedQuery.firstResult = criteria.page.firstResult
        }
        if (limit != null && limit >= 0) {
            typedQuery.maxResults = limit
        }

        return typedQuery.resultList
    }

    /**
     * Orders by user id unless an explicit sort was requested.
     *
     * Ordering by id rather than username makes cursor paging a range scan on
     * `CONSTRAINT_USER_GROUP (GROUP_ID, USER_ID)`, and id is immutable where username is not.
     */
    private fun ordering(
        builder: CriteriaBuilder,
        user: Join<UserGroupMembershipEntity, UserEntity>,
        sort: ScimSort?,
    ): List<Order> {
        val id = user.get<String>("id")
        if (sort == null) {
            return listOf(builder.asc(id))
        }

        val column = user.get<Any>(sort.column.property)
        val expression =
            if (sort.column.kind == UserField.Kind.MIXED_CASE) {
                builder.lower(column.`as`(String::class.java))
            } else {
                column
            }
        val nullRank = nullSortRank(builder, column, sort.ascending)

        // Id is appended as a tiebreaker so paging stays stable when the sort key repeats.
        return listOf(
            builder.asc(nullRank),
            if (sort.ascending) builder.asc(expression) else builder.desc(expression),
            builder.asc(id),
        )
    }

    /**
     * Match the SCIM SDK comparator: nulls sort last ascending and first descending.
     */
    private fun nullSortRank(
        builder: CriteriaBuilder,
        column: Expression<*>,
        ascending: Boolean,
    ): Expression<Int> =
        if (ascending) {
            builder.selectCase<Int>().`when`(builder.isNull(column), 1).otherwise(0)
        } else {
            builder.selectCase<Int>().`when`(builder.isNull(column), 0).otherwise(1)
        }

    private fun restrictions(
        builder: CriteriaBuilder,
        query: CriteriaQuery<*>,
        membership: Root<UserGroupMembershipEntity>,
        user: Join<UserGroupMembershipEntity, UserEntity>,
        criteria: ScimUserSearchCriteria,
        after: String?,
    ): Array<Predicate> {
        val hasScimRole = query.subquery(String::class.java)
        val roleMapping = hasScimRole.from(UserRoleMappingEntity::class.java)
        hasScimRole.select(roleMapping.get("roleId"))
        hasScimRole.where(
            builder.equal(roleMapping.get<Any>("user"), user),
            builder.equal(roleMapping.get<String>("roleId"), criteria.scimRoleId),
        )

        val predicates =
            mutableListOf(
                builder.equal(membership.get<String>("groupId"), criteria.organizationGroupId),
                // Defence in depth: the organization group already implies the realm.
                builder.equal(user.get<String>("realmId"), realm.id),
                builder.exists(hasScimRole),
            )

        after?.let {
            predicates.add(builder.greaterThan(user.get("id"), it))
        }

        criteria.filter?.let {
            predicates.add(ScimFilterCompiler(builder, query, user).compile(it))
        }

        return predicates.toTypedArray()
    }
}

/** How to order a SCIM user search in the database. */
internal data class ScimSort(
    val column: UserField.Column,
    val ascending: Boolean,
)
