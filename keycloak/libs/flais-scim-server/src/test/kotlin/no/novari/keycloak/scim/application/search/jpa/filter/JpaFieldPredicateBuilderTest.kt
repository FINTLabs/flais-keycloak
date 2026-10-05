package no.novari.keycloak.scim.application.search.jpa.filter

import com.fasterxml.jackson.databind.node.TextNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Root
import jakarta.persistence.criteria.Subquery
import no.novari.keycloak.scim.search.jpa.filter.ComparisonOp
import no.novari.keycloak.scim.search.jpa.filter.JpaFieldPredicateBuilder
import no.novari.keycloak.scim.search.jpa.mapping.Attribute
import no.novari.keycloak.scim.search.jpa.mapping.Column
import no.novari.keycloak.scim.search.jpa.mapping.FieldKind
import org.junit.jupiter.api.Test
import org.keycloak.models.jpa.entities.UserAttributeEntity
import org.keycloak.models.jpa.entities.UserEntity
import org.keycloak.storage.jpa.JpaHashUtils

class JpaFieldPredicateBuilderTest {
    private val builder = mockk<CriteriaBuilder>(relaxed = true)
    private val query = mockk<CriteriaQuery<*>>()
    private val user = mockk<From<*, UserEntity>>()
    private val predicates = JpaFieldPredicateBuilder(builder, query, user)

    @Test
    fun `column inequality includes missing values`() {
        val column = mockk<Path<Any>>()
        val text = mockk<Expression<String>>()
        every { user.get<Any>("email") } returns column
        every { column.`as`(String::class.java) } returns text

        predicates.compare(Column<Any>("email", FieldKind.CASE_EXACT), ComparisonOp.NE, TextNode("work"))

        verify { builder.or(builder.isNull(column), builder.notEqual(text, "work")) }
    }

    @Test
    fun `contains escapes SQL wildcard characters`() {
        val column = mockk<Path<Any>>()
        val text = mockk<Expression<String>>()
        every { user.get<Any>("email") } returns column
        every { column.`as`(String::class.java) } returns text

        predicates.compare(Column<Any>("email", FieldKind.CASE_EXACT), ComparisonOp.CO, TextNode("a%_"))

        verify { builder.like(text, "%a\\%\\_%", '\\') }
    }

    @Test
    fun `multivalued attributes use correlated exists and the appropriate long value hash`() {
        for (kind in listOf(FieldKind.CASE_EXACT, FieldKind.MIXED_CASE)) {
            val subquery = mockk<Subquery<String>>(relaxed = true)
            val attribute = mockk<Root<UserAttributeEntity>>()
            val value = mockk<Path<String>>()
            val longValue = mockk<Path<String>>()
            val hash = mockk<Path<ByteArray>>()
            val lowerHash = mockk<Path<ByteArray>>()
            val name = mockk<Path<String>>()
            val owner = mockk<Path<Any>>()
            every { query.subquery(String::class.java) } returns subquery
            every { subquery.from(UserAttributeEntity::class.java) } returns attribute
            every { attribute.get<String>("value") } returns value
            every { attribute.get<String>("longValue") } returns longValue
            every { attribute.get<ByteArray>("longValueHash") } returns hash
            every { attribute.get<ByteArray>("longValueHashLowerCase") } returns lowerHash
            every { attribute.get<String>("name") } returns name
            every { attribute.get<Any>("user") } returns owner

            predicates.compare(Attribute<Any>("roles", kind, multiValued = true), ComparisonOp.EQ, TextNode("WORK"))

            val expectedHash =
                if (kind == FieldKind.CASE_EXACT) {
                    JpaHashUtils.hashForAttributeValue("WORK")
                } else {
                    JpaHashUtils.hashForAttributeValueLowerCase("WORK")
                }
            verify {
                builder.equal(owner, user)
                builder.equal(name, "roles")
                builder.equal(if (kind == FieldKind.CASE_EXACT) hash else lowerHash, expectedHash)
                builder.exists(subquery)
            }
        }
    }
}
