package no.novari.keycloak.scim.application.store

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
import no.novari.keycloak.scim.store.ScimFilterCompiler
import no.novari.keycloak.scim.store.UnsupportedScimFilterException
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.keycloak.models.jpa.entities.UserEntity

internal class ScimFilterCompilerTest {
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

        assertThrows<UnsupportedScimFilterException> {
            criteria.compile("emails eq true")
        }
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
}
