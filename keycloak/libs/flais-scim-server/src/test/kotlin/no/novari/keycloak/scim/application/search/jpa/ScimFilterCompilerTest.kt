package no.novari.keycloak.scim.application.search.jpa

import com.unboundid.scim2.common.filters.Filter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import jakarta.persistence.criteria.Subquery
import no.novari.keycloak.scim.search.jpa.ScimFilterCompiler
import no.novari.keycloak.scim.search.jpa.UnsupportedScimSearchException
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.keycloak.models.UserModel
import org.keycloak.models.jpa.entities.UserAttributeEntity
import org.keycloak.models.jpa.entities.UserEntity

internal class ScimFilterCompilerTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "emails[type eq \"work\" and value eq \"test@novari.no\"]",
            "emails[primary eq true and value eq \"test@novari.no\"]",
            "emails[value eq \"test@novari.no\" and primary eq true]",
            "emails[(type eq \"home\" or primary eq true) and value eq \"test@novari.no\"]",
            "emails[not (primary eq false) and value eq \"test@novari.no\"]",
            "urn:ietf:params:scim:schemas:core:2.0:User:emails[type eq \"work\" and value eq \"test@novari.no\"]",
            "EMAILS[TYPE eq \"WORK\" and VALUE eq \"test@novari.no\"]",
        ],
    )
    fun `email value filters compile their relative comparisons`(filter: String) {
        val criteria = TestCriteria()
        val email = criteria.stringColumn("email")
        val lowered = mockk<Expression<String>>()
        val present = mockk<Predicate>()
        val equal = mockk<Predicate>()
        val guarded = mockk<Predicate>()

        every { criteria.builder.lower(email.text) } returns lowered
        every { criteria.builder.isNotNull(email.raw) } returns present
        every { criteria.builder.equal(lowered, "test@novari.no") } returns equal
        every { criteria.builder.and(present, equal) } returns guarded

        assertSame(guarded, criteria.compile(filter))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "emails[type eq \"home\" and value eq \"test@novari.no\"]",
            "emails[primary eq false and value eq \"test@test.com\"]",
        ],
    )
    fun `unmatched email selectors fold to false`(filter: String) {
        val criteria = TestCriteria()
        val noMatch = mockk<Predicate>()
        every { criteria.builder.disjunction() } returns noMatch

        assertSame(noMatch, criteria.compile(filter))
        verify(exactly = 0) { criteria.user.get<Any>("email") }
    }

    @Test
    fun `selected email value presence is compiled`() {
        val criteria = TestCriteria()
        val email = mockk<Path<Any>>()
        val present = mockk<Predicate>()
        every { criteria.user.get<Any>("email") } returns email
        every { criteria.builder.isNotNull(email) } returns present

        assertSame(present, criteria.compile("emails[primary eq true and value pr]"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "emails[userName eq \"alice\"]",
            "emails[display eq \"work\"]",
            "userName[value eq \"alice\"]",
        ],
    )
    fun `unsupported value filter mappings are rejected`(filter: String) {
        assertThrows<UnsupportedScimSearchException> { TestCriteria().compile(filter) }
    }

    @Test
    fun `presence accepts empty strings because they are present values`() {
        val criteria = TestCriteria()
        val email = mockk<Path<Any>>()
        val present = mockk<Predicate>()

        every { criteria.user.get<Any>("email") } returns email
        every { criteria.builder.isNotNull(email) } returns present

        val compiled = criteria.compile("emails.value pr")

        assertSame(present, compiled)
    }

    @Test
    fun `constant presence folds to true predicate`() {
        val criteria = TestCriteria()
        val truePredicate = mockk<Predicate>()

        every { criteria.builder.conjunction() } returns truePredicate

        val compiled = criteria.compile("emails.type pr")

        assertSame(truePredicate, compiled)
    }

    @Test
    fun `constant equality removes satisfied branch from conjunction`() {
        val criteria = TestCriteria()
        val userName = criteria.stringColumn("username")
        val present = mockk<Predicate>()
        val equal = mockk<Predicate>()
        val guarded = mockk<Predicate>()

        every { criteria.builder.isNotNull(userName.raw) } returns present
        every { criteria.builder.equal(userName.text, "alice") } returns equal
        every { criteria.builder.and(present, equal) } returns guarded

        val compiled = criteria.compile("""emails.type eq "work" and userName eq "ALICE"""")

        assertSame(guarded, compiled)
    }

    @Test
    fun `constant false conjunction short circuits remaining branches`() {
        val criteria = TestCriteria()
        val falsePredicate = mockk<Predicate>()

        every { criteria.builder.disjunction() } returns falsePredicate

        val compiled = criteria.compile("""emails.type eq "home" and userName eq "alice"""")

        assertSame(falsePredicate, compiled)
        verify(exactly = 0) {
            criteria.user.get<Any>("username")
        }
    }

    @Test
    fun `negated constant false folds to true predicate`() {
        val criteria = TestCriteria()
        val truePredicate = mockk<Predicate>()

        every { criteria.builder.conjunction() } returns truePredicate

        val compiled = criteria.compile("""not (emails.type eq "home")""")

        assertSame(truePredicate, compiled)
    }

    @Test
    fun `nullable string not equal includes missing column values`() {
        val criteria = TestCriteria()
        val email = criteria.stringColumn("email")
        val lowered = mockk<Expression<String>>()
        val missing = mockk<Predicate>()
        val notEqual = mockk<Predicate>()
        val nullable = mockk<Predicate>()

        every { criteria.builder.lower(email.text) } returns lowered
        every { criteria.builder.isNull(email.raw) } returns missing
        every { criteria.builder.notEqual(lowered, "alice@example.no") } returns notEqual
        every { criteria.builder.or(missing, notEqual) } returns nullable

        val compiled = criteria.compile("""emails.value ne "Alice@Example.No"""")

        assertSame(nullable, compiled)
    }

    @Test
    fun `boolean equality is guarded against null column values`() {
        val criteria = TestCriteria()
        val enabled = mockk<Path<Any>>()
        val present = mockk<Predicate>()
        val equal = mockk<Predicate>()
        val guarded = mockk<Predicate>()

        every { criteria.user.get<Any>("enabled") } returns enabled
        every { criteria.builder.isNotNull(enabled) } returns present
        every { criteria.builder.equal(enabled, true) } returns equal
        every { criteria.builder.and(present, equal) } returns guarded

        val compiled = criteria.compile("active eq true")

        assertSame(guarded, compiled)
    }

    @Test
    fun `or drops false constant branch`() {
        val criteria = TestCriteria()
        val userName = criteria.stringColumn("username")
        val present = mockk<Predicate>()
        val equal = mockk<Predicate>()
        val guarded = mockk<Predicate>()

        every { criteria.builder.isNotNull(userName.raw) } returns present
        every { criteria.builder.equal(userName.text, "alice") } returns equal
        every { criteria.builder.and(present, equal) } returns guarded

        val compiled = criteria.compile("""emails.type eq "home" or userName eq "alice"""")

        assertSame(guarded, compiled)
    }

    @Test
    fun `complex attributes cannot be compared directly`() {
        val criteria = TestCriteria()

        assertThrows<UnsupportedScimSearchException> {
            criteria.compile("emails eq true")
        }
    }

    @Test
    fun `externalId equality compares attribute values case exactly`() {
        val criteria = TestCriteria()
        val attribute = AttributeSubquery(criteria, "externalId")
        val exists = mockk<Predicate>()

        every { criteria.builder.equal(attribute.stored, "External-ABC") } returns attribute.shortEqual
        every { criteria.builder.equal(attribute.longStoredHash, any<ByteArray>()) } returns attribute.longEqual
        every { criteria.builder.exists(attribute.subquery) } returns exists

        val compiled = criteria.compile("""externalId eq "External-ABC"""")

        assertSame(exists, compiled)
        verify(exactly = 0) {
            criteria.builder.lower(attribute.stored)
            criteria.builder.equal(attribute.stored, "external-abc")
            criteria.builder.equal(attribute.longStoredLowerCaseHash, any<ByteArray>())
        }
    }

    @Test
    fun `fint givenName equality folds mixed case first name column`() {
        val criteria = TestCriteria()
        val firstName = criteria.stringColumn(UserModel.FIRST_NAME)
        val lowered = mockk<Expression<String>>()
        val present = mockk<Predicate>()
        val equal = mockk<Predicate>()
        val guarded = mockk<Predicate>()

        every { criteria.builder.lower(firstName.text) } returns lowered
        every { criteria.builder.isNotNull(firstName.raw) } returns present
        every { criteria.builder.equal(lowered, "alice") } returns equal
        every { criteria.builder.and(present, equal) } returns guarded

        val compiled =
            criteria.compile(
                """urn:ietf:params:scim:schemas:extension:fint:2.0:User:givenName eq "ALICE"""",
            )

        assertSame(guarded, compiled)
    }

    @Test
    fun `fint familyName equality folds mixed case last name column`() {
        val criteria = TestCriteria()
        val lastName = criteria.stringColumn(UserModel.LAST_NAME)
        val lowered = mockk<Expression<String>>()
        val present = mockk<Predicate>()
        val equal = mockk<Predicate>()
        val guarded = mockk<Predicate>()

        every { criteria.builder.lower(lastName.text) } returns lowered
        every { criteria.builder.isNotNull(lastName.raw) } returns present
        every { criteria.builder.equal(lowered, "nordmann") } returns equal
        every { criteria.builder.and(present, equal) } returns guarded

        val compiled =
            criteria.compile(
                """urn:ietf:params:scim:schemas:extension:fint:2.0:User:familyName eq "NORDMANN"""",
            )

        assertSame(guarded, compiled)
    }

    private class TestCriteria {
        val builder = mockk<CriteriaBuilder>()
        val query = mockk<CriteriaQuery<*>>()
        val user = mockk<From<*, UserEntity>>()

        fun compile(filter: String): Predicate =
            ScimFilterCompiler(builder, query, user)
                .compile(Filter.fromString(filter))

        fun stringColumn(property: String): StringColumn {
            val raw = mockk<Path<Any>>()
            val text = mockk<Expression<String>>()

            every { user.get<Any>(property) } returns raw
            every { raw.`as`(String::class.java) } returns text

            return StringColumn(raw, text)
        }
    }

    private data class StringColumn(
        val raw: Path<Any>,
        val text: Expression<String>,
    )

    private class AttributeSubquery(
        criteria: TestCriteria,
        attributeName: String,
    ) {
        val subquery = mockk<Subquery<String>>()
        val stored = mockk<Path<String>>()
        val longStoredHash = mockk<Path<ByteArray>>()
        val longStoredLowerCaseHash = mockk<Path<ByteArray>>()
        val shortEqual = mockk<Predicate>()
        val longEqual = mockk<Predicate>()

        init {
            val attribute = mockk<Root<UserAttributeEntity>>()
            val longStored = mockk<Path<String>>()
            val selectedName = mockk<Path<String>>()
            val matchedUser = mockk<Path<Any>>()
            val userEqual = mockk<Predicate>()
            val nameEqual = mockk<Predicate>()
            val storedPresent = mockk<Predicate>()
            val longStoredPresent = mockk<Predicate>()
            val anyStoredPresent = mockk<Predicate>()
            val comparison = mockk<Predicate>()

            every { criteria.query.subquery(String::class.java) } returns subquery
            every { subquery.from(UserAttributeEntity::class.java) } returns attribute
            every { attribute.get<String>("value") } returns stored
            every { attribute.get<String>("longValue") } returns longStored
            every { attribute.get<ByteArray>("longValueHash") } returns longStoredHash
            every { attribute.get<ByteArray>("longValueHashLowerCase") } returns longStoredLowerCaseHash
            every { attribute.get<String>("name") } returns selectedName
            every { attribute.get<Any>("user") } returns matchedUser
            every { subquery.select(selectedName) } returns subquery
            every { criteria.builder.equal(matchedUser, criteria.user) } returns userEqual
            every { criteria.builder.equal(selectedName, attributeName) } returns nameEqual
            every { criteria.builder.isNotNull(stored) } returns storedPresent
            every { criteria.builder.isNotNull(longStored) } returns longStoredPresent
            every { criteria.builder.or(storedPresent, longStoredPresent) } returns anyStoredPresent
            every { criteria.builder.or(shortEqual, longEqual) } returns comparison
            every {
                subquery.where(
                    userEqual,
                    nameEqual,
                    anyStoredPresent,
                    comparison,
                )
            } returns subquery
        }
    }
}
