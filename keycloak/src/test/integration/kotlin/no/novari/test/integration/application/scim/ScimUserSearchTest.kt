package no.novari.test.integration.application.scim

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.novari.test.common.config.KcConfig
import no.novari.test.common.environment.kc.KcEnvironment
import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.fixture.TestStrings.Orgs
import no.novari.test.common.fixture.TestStrings.Realms
import no.novari.test.common.utils.KcAdminClient
import no.novari.test.integration.utils.ScimFlow
import no.novari.test.integration.utils.ScimFlow.ScimUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Covers the database-backed SCIM user search.
 *
 * The unit tests stub the JPA query, so these tests are what actually prove the compiled Criteria
 * query runs and means what it is supposed to mean.
 *
 * The expected result sets for the negation and multivalued cases are mirrored by
 * `ScimFilterSemanticsTest`, which asserts the same expectations against the SDK's in-memory
 * evaluator. Together they show the native path and the fallback agree.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KcEnvironmentExtension::class)
class ScimUserSearchTest {
    private val realm = Realms.EXTERNAL
    private val fintUrn = "urn:ietf:params:scim:schemas:extension:fint:2.0:User"

    private val alice = "alice.search@telemark.no"
    private val bob = "bob.search@telemark.no"
    private val carol = "carol.search@telemark.no"
    private val longExternalId = "long-external-id-" + "x".repeat(260)
    private val longRole = "long-role-marker-" + "x".repeat(260)

    /**
     * Three users chosen to exercise the awkward cases: a disabled user, a user with no email and
     * no roles at all (so NULL handling under `not` is visible), and differing role cardinality.
     */
    private val fixture =
        listOf(
            ScimUser(
                schemas = listOf(CORE_SCHEMA, FINT_SCHEMA),
                externalId = "11111111-1111-1111-1111-111111111111",
                userName = alice,
                active = true,
                emails = listOf(ScimUser.Email(alice, primary = true)),
                roles =
                    listOf(
                        ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false),
                        ScimUser.Role("write", "write", "WindowsAzureActiveDirectoryRole", false),
                        ScimUser.Role(longRole, longRole, "WindowsAzureActiveDirectoryRole", false),
                    ),
                fintUserExtension = ScimUser.FintUserExtension("Alice", "Search", "E1", null, alice),
            ),
            ScimUser(
                schemas = listOf(CORE_SCHEMA, FINT_SCHEMA),
                externalId = "22222222-2222-2222-2222-222222222222",
                userName = bob,
                active = false,
                emails = listOf(ScimUser.Email(bob, primary = true)),
                roles = listOf(ScimUser.Role("read", "read", "WindowsAzureActiveDirectoryRole", false)),
                fintUserExtension = ScimUser.FintUserExtension("Bob", "Search", "E2", null, bob),
            ),
            ScimUser(
                schemas = listOf(CORE_SCHEMA),
                externalId = longExternalId,
                userName = carol,
                active = true,
                // No email and no roles: the row that makes NULL semantics observable.
                emails = emptyList(),
                roles = emptyList(),
            ),
        )

    private fun scimBaseUrl(
        env: KcEnvironment,
        kcConfig: KcConfig,
        orgAlias: String = Orgs.TELEMARK,
    ) = "${env.keycloakServiceUrl()}/realms/external/scim/v2/${kcConfig.requireOrg(orgAlias).id}"

    private fun tokenUrl(env: KcEnvironment) = "${env.flaisScimAuthUrl()}/token"

    private fun listUsers(
        env: KcEnvironment,
        kcConfig: KcConfig,
        filter: String? = null,
        startIndex: Int? = null,
        count: Int? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        orgAlias: String = Orgs.TELEMARK,
        cursor: String? = null,
    ): JsonObject =
        ScimFlow
            .listUsers(
                scimBaseUrl(env, kcConfig, orgAlias),
                tokenUrl(env),
                filter,
                startIndex,
                count,
                sortBy,
                sortOrder,
                cursor,
            ).use { resp ->
                assertEquals(200, resp.code)
                Json.parseToJsonElement(requireNotNull(resp.body).string()).jsonObject
            }

    private fun JsonObject.totalResults() = getValue("totalResults").jsonPrimitive.int

    private fun JsonObject.nextCursor() = this["nextCursor"]?.jsonPrimitive?.content

    private fun JsonObject.userNames() =
        (this["Resources"]?.jsonArray ?: JsonArray(emptyList()))
            .map {
                it.jsonObject
                    .getValue("userName")
                    .jsonPrimitive.content
            }

    @BeforeEach
    fun provisionFixture(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        kc.use { KcAdminClient.deleteAllUsers(realmRes) }

        fixture.forEach { user ->
            ScimFlow
                .createUser(scimBaseUrl(env, kcConfig), tokenUrl(env), user)
                .use { resp -> assertEquals(201, resp.code) }
        }
    }

    @Test
    fun `list returns an exact totalResults for scim managed members`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val body = listUsers(env, kcConfig)

        assertEquals(fixture.size, body.totalResults())
        assertEquals(fixture.size, body.userNames().size)
    }

    @Test
    fun `list excludes organization members that lack the scim-managed role`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        // Mirrors a user created by IdP first-broker-login: a MANAGED organization member that was
        // never provisioned over SCIM and therefore holds no scim-managed role.
        val intruder = "idp-joined@telemark.no"
        val orgId = kcConfig.requireOrg(Orgs.TELEMARK).id
        val (kc, realmRes) = KcAdminClient.connect(env, realm)
        kc.use {
            val userId =
                KcAdminClient.createUser(
                    realm = realmRes,
                    username = intruder,
                    email = intruder,
                    firstName = "Idp",
                    lastName = "Joined",
                )
            KcAdminClient.addUserToOrg(realmRes, userId, orgId)

            // Without this the test could pass vacuously: the assertions below only have meaning if
            // the intruder really is an organization member that a role-blind query would count.
            assertNotNull(KcAdminClient.getOrgMember(realmRes, orgId, userId))
        }

        val body = listUsers(env, kcConfig)

        // The role predicate lives in the SQL, so the extra member must not inflate the count.
        assertEquals(fixture.size, body.totalResults())
        assertFalse(intruder in body.userNames())
    }

    @ParameterizedTest(name = "{0} matches {1} user(s)")
    @CsvSource(
        delimiter = '|',
        value = [
            // Equality and inequality on indexed columns.
            "userName eq \"alice.search@telemark.no\"                 | 1",
            "userName ne \"alice.search@telemark.no\"                 | 2",
            "id pr                                                    | 3",
            "externalId pr                                            | 3",
            // Substring operators.
            "userName co \"search\"                                   | 3",
            "userName sw \"alice\"                                    | 1",
            "userName ew \"@telemark.no\"                             | 3",
            // Booleans.
            "active eq true                                           | 2",
            "active eq false                                          | 1",
            // Presence, including the user with no email at all.
            "emails pr                                                | 3",
            "emails.value pr                                          | 2",
            "emails eq \"alice.search@telemark.no\"                   | 0",
            "emails.primary eq true                                   | 3",
            // Logical combinators.
            "active eq true and userName sw \"alice\"                 | 1",
            "userName sw \"alice\" or userName sw \"bob\"             | 2",
            // Multivalued attribute: any value may match.
            "roles.value eq \"read\"                                  | 2",
            "roles.value eq \"write\"                                 | 1",
            "roles pr                                                 | 2",
        ],
    )
    fun `filters return correct totals`(
        filter: String,
        expected: Int,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        assertEquals(expected, listUsers(env, kcConfig, filter = filter.trim()).totalResults())
    }

    @ParameterizedTest(name = "{0} matches {1} user(s)")
    @CsvSource(
        delimiter = '|',
        value = [
            // A missing attribute makes the inner comparison false, so `not` must include the user
            // that has no email. Getting this wrong in SQL silently drops rows.
            "not (emails.value eq \"alice.search@telemark.no\")       | 2",
            "not (emails.value co \"search\")                         | 1",
            // Negation over a multivalued attribute means "has no such value".
            "not (roles.value eq \"read\")                            | 1",
            // Inequality over a multivalued attribute means "has some other value".
            "roles.value ne \"read\"                                  | 1",
            "not (active eq true)                                     | 1",
        ],
    )
    fun `negation and missing values follow the SCIM evaluator`(
        filter: String,
        expected: Int,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        assertEquals(expected, listUsers(env, kcConfig, filter = filter.trim()).totalResults())
    }

    @Test
    fun `fint extension attributes are filterable`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        assertEquals(1, listUsers(env, kcConfig, filter = """$fintUrn:employeeId eq "E1"""").totalResults())
        assertEquals(0, listUsers(env, kcConfig, filter = """$fintUrn:employeeId eq "e1"""").totalResults())
        assertEquals(2, listUsers(env, kcConfig, filter = """$fintUrn:employeeId pr""").totalResults())
        assertEquals(1, listUsers(env, kcConfig, filter = """$fintUrn:givenName eq "alice"""").totalResults())
    }

    @Test
    fun `long user attribute values follow the SCIM evaluator`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        assertEquals(1, listUsers(env, kcConfig, filter = """roles.value co "long-role-marker"""").totalResults())
    }

    @Test
    fun `paging returns only the requested page while totalResults stays exact`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val firstPage = listUsers(env, kcConfig, startIndex = 1, count = 2)
        assertEquals(fixture.size, firstPage.totalResults())
        assertEquals(2, firstPage.userNames().size)

        val secondPage = listUsers(env, kcConfig, startIndex = 3, count = 2)
        assertEquals(fixture.size, secondPage.totalResults())
        assertEquals(1, secondPage.userNames().size)

        // Pages must not overlap, which also proves the offset is applied in the database.
        assertEquals(
            fixture.size,
            (firstPage.userNames() + secondPage.userNames()).distinct().size,
        )
    }

    @Test
    fun `a filtered page reports the filtered total, not the collection total`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val body = listUsers(env, kcConfig, filter = """userName co "search"""", startIndex = 1, count = 1)

        assertEquals(3, body.totalResults())
        assertEquals(1, body.userNames().size)
    }

    @Test
    fun `count zero returns no resources while totalResults stays exact`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val body = listUsers(env, kcConfig, count = 0)

        assertEquals(fixture.size, body.totalResults())
        assertEquals(emptyList<String>(), body.userNames())
    }

    @Test
    fun `cursor paging returns a resumable next cursor`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val firstPage = listUsers(env, kcConfig, count = 2, cursor = "")
        assertEquals(fixture.size, firstPage.totalResults())
        assertEquals(2, firstPage.userNames().size)

        val nextCursor = requireNotNull(firstPage.nextCursor())
        val secondPage = listUsers(env, kcConfig, count = 2, cursor = nextCursor)

        assertEquals(fixture.size, secondPage.totalResults())
        assertEquals(1, secondPage.userNames().size)
        assertEquals(
            fixture.size,
            (firstPage.userNames() + secondPage.userNames()).distinct().size,
        )
    }

    @Test
    fun `sorting is applied in the database`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val ascending = listUsers(env, kcConfig, sortBy = "userName", sortOrder = "ascending")
        assertEquals(listOf(alice, bob, carol), ascending.userNames())

        val descending = listUsers(env, kcConfig, sortBy = "userName", sortOrder = "descending")
        assertEquals(listOf(carol, bob, alice), descending.userNames())

        // Sorting must survive paging, which only holds if the ORDER BY is in the SQL.
        val firstDescending =
            listUsers(env, kcConfig, sortBy = "userName", sortOrder = "descending", startIndex = 1, count = 1)
        assertEquals(listOf(carol), firstDescending.userNames())
    }

    @Test
    fun `userName eq does not return a user provisioned in another organization`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val outsider = "search-user-rogaland@rogaland.no"
        ScimFlow
            .createUser(
                scimBaseUrl(env, kcConfig, Orgs.ROGALAND),
                tokenUrl(env),
                ScimUser(
                    schemas = listOf(CORE_SCHEMA),
                    externalId = "99999999-9999-9999-9999-999999999999",
                    userName = outsider,
                    active = true,
                    emails = listOf(ScimUser.Email(outsider, primary = true)),
                ),
            ).use { resp -> assertEquals(201, resp.code) }

        val body = listUsers(env, kcConfig, filter = """userName eq "$outsider"""")

        assertEquals(0, body.totalResults())
    }

    @Test
    fun `filters that cannot be compiled still work through the in-memory fallback`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        // roles.type only exists inside the rawRoles JSON blob.
        val body = listUsers(env, kcConfig, filter = """roles.type eq "WindowsAzureActiveDirectoryRole"""")

        assertEquals(2, body.totalResults())
    }

    @Test
    fun `sorts that cannot be compiled still work through the in-memory fallback`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val body = listUsers(env, kcConfig, sortBy = "roles")

        assertEquals(fixture.size, body.totalResults())
    }

    private companion object {
        const val CORE_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User"
        const val FINT_SCHEMA = "urn:ietf:params:scim:schemas:extension:fint:2.0:User"
    }
}
