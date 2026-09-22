package no.novari.keycloak.scim.application.search.jpa.filter

import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.TextNode
import no.novari.keycloak.scim.search.jpa.filter.ComparisonOp
import no.novari.keycloak.scim.search.jpa.filter.ScimConstantEvaluator
import no.novari.keycloak.scim.search.jpa.mapping.Constant
import no.novari.keycloak.scim.search.jpa.mapping.ConstantValue
import no.novari.keycloak.scim.search.jpa.mapping.FieldKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ScimConstantEvaluatorTest {
    @Test
    fun `text constants support every comparison operator`() {
        val comparisons =
            mapOf(
                ComparisonOp.EQ to "work",
                ComparisonOp.NE to "home",
                ComparisonOp.CO to "or",
                ComparisonOp.SW to "wo",
                ComparisonOp.EW to "rk",
                ComparisonOp.GT to "home",
                ComparisonOp.GE to "work",
                ComparisonOp.LT to "z",
                ComparisonOp.LE to "work",
            )
        val field = Constant<Any>(ConstantValue.Text("work"), FieldKind.CASE_EXACT)
        comparisons.forEach { (op, value) ->
            assertEquals(true, ScimConstantEvaluator.evaluate(field, op, TextNode(value)), op.name)
        }
    }

    @Test
    fun `text constants preserve case matching rules`() {
        for (kind in listOf(FieldKind.CASE_EXACT, FieldKind.STORED_LOWERCASE, FieldKind.MIXED_CASE)) {
            val field = Constant<Any>(ConstantValue.Text("work"), kind)
            assertEquals(
                kind != FieldKind.CASE_EXACT,
                ScimConstantEvaluator.evaluate(field, ComparisonOp.EQ, TextNode("WORK")),
            )
        }
    }

    @Test
    fun `boolean constants evaluate equality and inequality`() {
        val field = Constant<Any>(ConstantValue.Bool(true), FieldKind.BOOLEAN)
        for (value in listOf(true, false)) {
            assertEquals(value, ScimConstantEvaluator.evaluate(field, ComparisonOp.EQ, BooleanNode.valueOf(value)))
            assertEquals(!value, ScimConstantEvaluator.evaluate(field, ComparisonOp.NE, BooleanNode.valueOf(value)))
        }
    }

    @Test
    fun `invalid constant comparisons retain existing exception types`() {
        val boolean = Constant<Any>(ConstantValue.Bool(true), FieldKind.BOOLEAN)
        val text = Constant<Any>(ConstantValue.Text("work"), FieldKind.MIXED_CASE)
        assertThrows<IllegalArgumentException> {
            ScimConstantEvaluator.evaluate(boolean, ComparisonOp.EQ, TextNode("true"))
        }
        assertThrows<IllegalArgumentException> {
            ScimConstantEvaluator.evaluate(text, ComparisonOp.EQ, BooleanNode.TRUE)
        }
        assertThrows<IllegalStateException> {
            ScimConstantEvaluator.evaluate(boolean, ComparisonOp.CO, BooleanNode.TRUE)
        }
    }
}
