package no.novari.keycloak.scim.application.store

import com.fasterxml.jackson.databind.node.ObjectNode
import com.unboundid.scim2.common.GenericScimResource
import com.unboundid.scim2.common.filters.Filter
import com.unboundid.scim2.common.utils.FilterEvaluator
import com.unboundid.scim2.common.utils.JsonUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Pins the SCIM semantics that `ScimFilterCompiler` reproduces in SQL.
 *
 * The compiler is only allowed to be an optimization: for any filter it accepts, it must return the
 * same users as the SDK's in-memory evaluator, which is the reference implementation. These cases
 * mirror the expectations asserted natively by `ScimUserSearchTest`, so if the two ever disagree one
 * of the two suites fails.
 *
 * The interesting cases are missing attributes under negation, where SQL's three-valued logic does
 * not match SCIM, and multivalued attributes, where "any value matches" changes what `ne` means.
 */
class ScimFilterSemanticsTest {
    /** Mirrors what `ScimUserEndpoint.translateUser` emits. */
    private fun user(
        userName: String,
        email: String?,
        active: Boolean,
        roles: List<String>,
    ): GenericScimResource {
        val emailNode =
            """[{"value": ${email?.let { "\"$it\"" } ?: "null"}, "primary": true}]"""
        val roleNodes = roles.joinToString(",") { """{"value": "$it"}""" }

        return GenericScimResource(
            JsonUtils.getObjectReader().readTree(
                """
                {
                  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
                  "userName": "$userName",
                  "active": $active,
                  "emails": $emailNode,
                  "roles": [$roleNodes]
                }
                """.trimIndent(),
            ) as ObjectNode,
        )
    }

    private val alice = user("alice.search@telemark.no", "alice.search@telemark.no", true, listOf("read", "write"))
    private val bob = user("bob.search@telemark.no", "bob.search@telemark.no", false, listOf("read"))
    private val dave =
        user(
            "dave.search@telemark.no",
            "dave.search@telemark.no",
            true,
            listOf("long-role-marker-" + "x".repeat(260)),
        )

    /** No email and no roles: the row that makes missing-value handling observable. */
    private val carol = user("carol.search@telemark.no", null, true, emptyList())

    private val users = listOf(alice, bob, carol, dave)

    private fun matches(filter: String): Int {
        val parsed = Filter.fromString(filter)
        return users.count { FilterEvaluator.evaluate(parsed, it.objectNode) }
    }

    @ParameterizedTest(name = "{0} matches {1} user(s)")
    @CsvSource(
        delimiter = '|',
        value = [
            "userName eq \"alice.search@telemark.no\"                 | 1",
            "userName ne \"alice.search@telemark.no\"                 | 3",
            "userName co \"search\"                                   | 4",
            "userName sw \"alice\"                                    | 1",
            "userName ew \"@telemark.no\"                             | 4",
            "active eq true                                           | 3",
            "active eq false                                          | 1",
            "emails pr                                                | 4",
            "emails.value pr                                          | 3",
            "emails eq \"alice.search@telemark.no\"                   | 0",
            "active eq true and userName sw \"alice\"                 | 1",
            "userName sw \"alice\" or userName sw \"bob\"             | 2",
            "roles.value eq \"read\"                                  | 2",
            "roles.value eq \"write\"                                 | 1",
            "roles.value co \"long-role-marker\"                      | 1",
        ],
    )
    fun `the evaluator agrees with the compiled predicates`(
        filter: String,
        expected: Int,
    ) {
        assertEquals(expected, matches(filter.trim()))
    }

    @ParameterizedTest(name = "{0} matches {1} user(s)")
    @CsvSource(
        delimiter = '|',
        value = [
            // Carol has no email, so the inner comparison is false and `not` includes her. A naive
            // SQL translation would return UNKNOWN here and silently drop her.
            "not (emails.value eq \"alice.search@telemark.no\")       | 3",
            "not (emails.value co \"search\")                         | 1",
            "emails.value ne \"alice.search@telemark.no\"             | 3",
            // Only Carol has no `read` role.
            "not (roles.value eq \"read\")                            | 2",
            // Only Alice holds a role other than `read`; Carol holds none at all.
            "roles.value ne \"read\"                                  | 2",
            "not (active eq true)                                     | 1",
        ],
    )
    fun `negation and missing values behave as the compiler assumes`(
        filter: String,
        expected: Int,
    ) {
        assertEquals(expected, matches(filter.trim()))
    }
}
