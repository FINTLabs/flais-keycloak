package no.novari.keycloak.scim.application.store

import com.unboundid.scim2.common.filters.Filter
import io.mockk.every
import io.mockk.mockk
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import no.novari.keycloak.scim.store.ScimFilterCompiler
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.keycloak.models.jpa.entities.UserEntity

internal class ScimFilterCompilerTest {
    @Test
    fun `presence accepts empty strings because they are present values`() {
        val builder = mockk<CriteriaBuilder>()
        val query = mockk<CriteriaQuery<*>>()
        val user = mockk<From<*, UserEntity>>()
        val email = mockk<Path<Any>>()
        val present = mockk<Predicate>()

        every { user.get<Any>("email") } returns email
        every { builder.isNotNull(email) } returns present

        val compiled = ScimFilterCompiler(builder, query, user).compile(Filter.fromString("emails.value pr"))

        assertSame(present, compiled)
    }
}
