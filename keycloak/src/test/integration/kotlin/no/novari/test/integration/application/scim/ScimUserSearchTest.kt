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
import no.novari.test.common.utils.ScimFlow
import no.novari.test.common.utils.ScimFlow.ScimUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import java.util.UUID

/**
 * Covers database-backed SCIM user search, including missing values,
 * multivalued attributes, paging, and sorting.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KcEnvironmentExtension::class)
class ScimUserSearchTest {
    private val realm = Realms.EXTERNAL
    private val fintUrn = "urn:ietf:params:scim:schemas:extension:fint:2.0:User"

    private val alice = "alice-${UUID.randomUUID()}@telemark.no"
    private val jon = "jon-${UUID.randomUUID()}@telemark.no"
    private val carol = "carol-${UUID.randomUUID()}@telemark.no"
    private val longExternalId = "long-external-id-${UUID.randomUUID()}-" + "x".repeat(260)
    private val longRole = "long-role-marker-${UUID.randomUUID()}-" + "x".repeat(260)

    private fun randomRole(): ScimUser.Role {
        val value = "role-${UUID.randomUUID()}"
        return ScimUser.Role(
            value = value,
            display = value,
            type = "WindowsAzureActiveDirectoryRole",
            primary = false,
        )
    }

    /**
     * Keep the attributes used by search assertions deliberate while randomizing
     * identities and role values.
     */
    private val testUsers =
        listOf(
            ScimFlow.randomUser(
                orgAlias = Orgs.TELEMARK,
                userName = alice,
                givenName = "Alice",
                familyName = "Basic",
                email = alice,
                active = true,
                employeeId = "E1",
                studentNumber = null,
                userPrincipalName = alice,
                roles =
                    listOf(
                        randomRole(),
                        randomRole(),
                        ScimUser.Role(
                            value = longRole,
                            display = longRole,
                            type = "WindowsAzureActiveDirectoryRole",
                            primary = false,
                        ),
                    ),
            ),
            ScimFlow.randomUser(
                orgAlias = Orgs.TELEMARK,
                userName = jon,
                givenName = "Jon",
                familyName = "Basic",
                email = jon,
                active = false,
                employeeId = "",
                studentNumber = null,
                userPrincipalName = jon,
                roles = listOf(randomRole()),
            ),
            ScimFlow
                .randomUser(
                    orgAlias = Orgs.TELEMARK,
                    externalId = longExternalId,
                    userName = carol,
                    email = null,
                    active = true,
                    roles = emptyList(),
                    fintUserExtension = null,
                ).copy(emails = emptyList()),
        )

    private fun resolveFilter(filter: String): String =
        filter
            .trim()
            .replace("{alice}", alice)
            .replace("{aliceExternalId}", testUsers.first().externalId)

    private fun scimBaseUrl(
        env: KcEnvironment,
        kcConfig: KcConfig,
        orgAlias: String = Orgs.TELEMARK,
    ) = "${env.keycloakServiceUrl()}/realms/$realm/scim/v2/${kcConfig.requireOrg(orgAlias).id}"

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

    private fun listUsersStatus(
        env: KcEnvironment,
        kcConfig: KcConfig,
        filter: String? = null,
        sortBy: String? = null,
    ): Int =
        ScimFlow
            .listUsers(
                scimBaseUrl(env, kcConfig),
                tokenUrl(env),
                filter,
                sortBy = sortBy,
            ).use { resp -> resp.code }

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

        ScimFlow.provision(env, kcConfig, Orgs.TELEMARK, testUsers)
    }

    @Test
    fun `list returns an exact totalResults for scim managed members`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val body = listUsers(env, kcConfig)

        assertEquals(testUsers.size, body.totalResults())
        assertEquals(testUsers.size, body.userNames().size)
    }

    @Test
    fun `list excludes organization members that lack the scim-managed role`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val intruder = ScimFlow.randomUser(Orgs.TELEMARK)
        val orgId = kcConfig.requireOrg(Orgs.TELEMARK).id
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            // Create through the admin API so this member has no scim-managed role.
            val userId =
                KcAdminClient.createUser(
                    realm = realmRes,
                    username = intruder.userName,
                    email =
                        intruder.emails
                            .orEmpty()
                            .single()
                            .value,
                    firstName = requireNotNull(intruder.fintUserExtension?.givenName),
                    lastName = requireNotNull(intruder.fintUserExtension?.familyName),
                )
            KcAdminClient.addUserToOrg(realmRes, userId, orgId)

            assertNotNull(KcAdminClient.getOrgMember(realmRes, orgId, userId))
        }

        val body = listUsers(env, kcConfig)

        assertEquals(testUsers.size, body.totalResults())
        assertFalse(intruder.userName in body.userNames())
    }

    @Test
    fun `list excludes users with only an inherited scim-managed role`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val (kc, realmRes) = KcAdminClient.connect(env, realm)

        kc.use {
            val suffix = UUID.randomUUID().toString()
            val compositeName = "scim-search-composite-$suffix"
            realmRes.roles().create(RoleRepresentation().apply { name = compositeName })

            val composite = realmRes.roles().get(compositeName)
            val scimRole = realmRes.roles().get("scim-managed").toRepresentation()
            composite.addComposites(listOf(scimRole))

            val groupResponse =
                realmRes.groups().add(
                    GroupRepresentation().apply { name = "scim-search-group-$suffix" },
                )
            val groupId = groupResponse.use { CreatedResponseUtil.getCreatedId(it) }
            val group = realmRes.groups().group(groupId)
            group.roles().realmLevel().add(listOf(composite.toRepresentation()))

            val userId = requireNotNull(KcAdminClient.findUserByUsername(realmRes, alice)?.id)
            val user = realmRes.users().get(userId)
            user.roles().realmLevel().remove(listOf(scimRole))

            try {
                user.roles().realmLevel().add(listOf(composite.toRepresentation()))

                val compositeBody = listUsers(env, kcConfig)
                assertEquals(testUsers.size - 1, compositeBody.totalResults())
                assertFalse(alice in compositeBody.userNames())

                user.roles().realmLevel().remove(listOf(composite.toRepresentation()))
                user.joinGroup(groupId)

                val groupBody = listUsers(env, kcConfig, cursor = "")
                assertEquals(testUsers.size - 1, groupBody.totalResults())
                assertFalse(alice in groupBody.userNames())
            } finally {
                user.roles().realmLevel().add(listOf(scimRole))
                group.remove()
                composite.remove()
            }
        }
    }

    @Test
    fun `role presence distinguishes partial role objects from their value fields`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val edgeUser =
            ScimFlow.randomUser(
                orgAlias = Orgs.TELEMARK,
                roles = listOf(ScimUser.Role(display = "reader")),
                fintUserExtension = null,
            )

        ScimFlow
            .createUser(
                scimBaseUrl(env, kcConfig),
                tokenUrl(env),
                edgeUser,
            ).use { response ->
                assertEquals(201, response.code)
            }

        assertEquals(2, listUsers(env, kcConfig, filter = "roles pr").totalResults())
        assertEquals(2, listUsers(env, kcConfig, filter = "roles.value pr").totalResults())
        assertEquals(400, listUsersStatus(env, kcConfig, filter = "emails eq true"))
    }

    @ParameterizedTest(name = "{0} matches {1} user(s)")
    @CsvSource(
        delimiter = '|',
        value = [
            // Placeholders are resolved against the randomized fixture.
            "userName eq \"{alice}\"                                  | 1",
            "userName ne \"{alice}\"                                  | 2",
            "id pr                                                   | 3",
            "externalId pr                                           | 3",
            "externalId eq \"{aliceExternalId}\"                       | 1",
            "externalId co \"long-external-id\"                        | 1",
            "userName co \"@telemark.no\"                              | 3",
            "userName sw \"alice\"                                    | 1",
            "userName ew \"@telemark.no\"                              | 3",
            "active eq true                                          | 2",
            "active eq false                                         | 1",
            "emails pr                                               | 3",
            "emails.value pr                                         | 2",
            "emails.primary eq true                                  | 3",
            "active eq true and userName sw \"alice\"                  | 1",
            "userName sw \"alice\" or userName sw \"jon\"              | 2",
            "roles pr                                                | 2",
        ],
    )
    fun `filters return correct totals`(
        filter: String,
        expected: Int,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        assertEquals(
            expected,
            listUsers(env, kcConfig, filter = resolveFilter(filter)).totalResults(),
        )
    }

    @ParameterizedTest(name = "{0} matches {1} user(s)")
    @CsvSource(
        delimiter = '|',
        value = [
            "not (emails.value eq \"{alice}\")                        | 2",
            "not (emails.value co \"@telemark.no\")                    | 1",
            "emails.value ne \"{alice}\"                              | 2",
            "urn:ietf:params:scim:schemas:extension:fint:2.0:User:employeeId ne \"E1\" | 2",
            "not (active eq true)                                    | 1",
        ],
    )
    fun `negation and missing values follow the SCIM evaluator`(
        filter: String,
        expected: Int,
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        assertEquals(
            expected,
            listUsers(env, kcConfig, filter = resolveFilter(filter)).totalResults(),
        )
    }

    @Test
    fun `fint extension attributes are filterable`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        assertEquals(1, listUsers(env, kcConfig, filter = """$fintUrn:employeeId eq "E1"""").totalResults())
        assertEquals(0, listUsers(env, kcConfig, filter = """$fintUrn:employeeId eq "e1"""").totalResults())
        assertEquals(2, listUsers(env, kcConfig, filter = """$fintUrn:employeeId ne "E1"""").totalResults())
        assertEquals(2, listUsers(env, kcConfig, filter = """$fintUrn:employeeId pr""").totalResults())
        assertEquals(1, listUsers(env, kcConfig, filter = """$fintUrn:givenName eq "alice"""").totalResults())
    }

    @Test
    fun `long user attribute values are filterable`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        assertEquals(
            1,
            listUsers(env, kcConfig, filter = """externalId co "long-external-id"""").totalResults(),
        )
    }

    @Test
    fun `paging returns only the requested page while totalResults stays exact`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val firstPage = listUsers(env, kcConfig, startIndex = 1, count = 2)
        assertEquals(testUsers.size, firstPage.totalResults())
        assertEquals(2, firstPage.userNames().size)

        val secondPage = listUsers(env, kcConfig, startIndex = 3, count = 2)
        assertEquals(testUsers.size, secondPage.totalResults())
        assertEquals(1, secondPage.userNames().size)

        assertEquals(
            testUsers.size,
            (firstPage.userNames() + secondPage.userNames()).distinct().size,
        )
    }

    @Test
    fun `a filtered page reports the filtered total, not the collection total`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val body =
            listUsers(
                env,
                kcConfig,
                filter = """userName co "@telemark.no"""",
                startIndex = 1,
                count = 1,
            )

        assertEquals(3, body.totalResults())
        assertEquals(1, body.userNames().size)
    }

    @Test
    fun `count zero returns no resources while totalResults stays exact`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val body = listUsers(env, kcConfig, count = 0)

        assertEquals(testUsers.size, body.totalResults())
        assertEquals(emptyList<String>(), body.userNames())
    }

    @Test
    fun `cursor paging returns a resumable next cursor`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val firstPage = listUsers(env, kcConfig, count = 2, cursor = "")
        assertEquals(testUsers.size, firstPage.totalResults())
        assertEquals(2, firstPage.userNames().size)

        val nextCursor = requireNotNull(firstPage.nextCursor())
        val secondPage = listUsers(env, kcConfig, count = 2, cursor = nextCursor)

        assertEquals(testUsers.size, secondPage.totalResults())
        assertEquals(1, secondPage.userNames().size)
        assertEquals(
            testUsers.size,
            (firstPage.userNames() + secondPage.userNames()).distinct().size,
        )
    }

    @Test
    fun `sorting is applied in the database`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val ascending = listUsers(env, kcConfig, sortBy = "userName", sortOrder = "ascending")
        assertEquals(listOf(alice, carol, jon), ascending.userNames())

        val descending = listUsers(env, kcConfig, sortBy = "userName", sortOrder = "descending")
        assertEquals(listOf(jon, carol, alice), descending.userNames())

        val firstDescending =
            listUsers(
                env,
                kcConfig,
                sortBy = "userName",
                sortOrder = "descending",
                startIndex = 1,
                count = 1,
            )
        assertEquals(listOf(jon), firstDescending.userNames())
    }

    @Test
    fun `sorting nullable attributes follows the SCIM comparator`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val ascending = listUsers(env, kcConfig, sortBy = "emails.value", sortOrder = "ascending")
        assertEquals(listOf(alice, jon, carol), ascending.userNames())

        val descending = listUsers(env, kcConfig, sortBy = "emails.value", sortOrder = "descending")
        assertEquals(listOf(carol, jon, alice), descending.userNames())
    }

    @Test
    fun `userName eq does not return a user provisioned in another organization`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        val outsider =
            ScimFlow.randomUser(
                orgAlias = Orgs.ROGALAND,
                fintUserExtension = null,
            )

        ScimFlow
            .createUser(
                scimBaseUrl(env, kcConfig, Orgs.ROGALAND),
                tokenUrl(env),
                outsider,
            ).use { resp ->
                assertEquals(201, resp.code)
            }

        val body =
            listUsers(
                env,
                kcConfig,
                filter = """userName eq "${outsider.userName}"""",
            )

        assertEquals(0, body.totalResults())
    }

    @Test
    fun `complex attribute comparison filters are rejected`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        assertEquals(
            400,
            listUsersStatus(
                env,
                kcConfig,
                filter = """roles.type eq "WindowsAzureActiveDirectoryRole"""",
            ),
        )
        assertEquals(
            400,
            listUsersStatus(env, kcConfig, filter = """emails eq "$alice""""),
        )
    }

    @Test
    fun `sorts that cannot be compiled are rejected`(
        env: KcEnvironment,
        kcConfig: KcConfig,
    ) {
        assertEquals(400, listUsersStatus(env, kcConfig, sortBy = "roles"))
    }
}
