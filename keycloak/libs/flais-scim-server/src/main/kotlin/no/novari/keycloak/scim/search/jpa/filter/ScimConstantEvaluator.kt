package no.novari.keycloak.scim.search.jpa.filter

import com.fasterxml.jackson.databind.node.ValueNode
import no.novari.keycloak.scim.search.jpa.mapping.Constant
import no.novari.keycloak.scim.search.jpa.mapping.ConstantValue
import no.novari.keycloak.scim.search.jpa.mapping.FieldKind
import java.util.Locale

internal object ScimConstantEvaluator {
    fun evaluate(
        field: Constant<*>,
        op: ComparisonOp,
        value: ValueNode,
    ): Boolean =
        when (val constant = field.value) {
            is ConstantValue.Bool ->
                evaluateBoolean(
                    constant = constant.value,
                    op = op,
                    comparisonValue = value,
                )

            is ConstantValue.Text ->
                evaluateString(
                    constant = constant.value,
                    kind = field.kind,
                    op = op,
                    comparisonValue = value,
                )
        }

    private fun evaluateBoolean(
        constant: Boolean,
        op: ComparisonOp,
        comparisonValue: ValueNode,
    ): Boolean {
        require(comparisonValue.isBoolean) {
            "'$comparisonValue' is not a boolean"
        }

        val comparison =
            comparisonValue.booleanValue()

        return when (op) {
            ComparisonOp.EQ -> constant == comparison
            ComparisonOp.NE -> constant != comparison

            else ->
                error("Operator $op is not supported for boolean constants")
        }
    }

    private fun evaluateString(
        constant: String,
        kind: FieldKind,
        op: ComparisonOp,
        comparisonValue: ValueNode,
    ): Boolean {
        require(comparisonValue.isTextual) {
            "'$comparisonValue' is not a string"
        }

        val comparison =
            comparisonValue.textValue()

        val (left, right) =
            when (kind) {
                FieldKind.CASE_EXACT ->
                    constant to comparison

                FieldKind.STORED_LOWERCASE ->
                    constant to comparison.lowercase(Locale.ROOT)

                FieldKind.MIXED_CASE ->
                    constant.lowercase(Locale.ROOT) to
                        comparison.lowercase(Locale.ROOT)

                FieldKind.BOOLEAN ->
                    error("BOOLEAN kind used with string constant")
            }

        return when (op) {
            ComparisonOp.EQ -> left == right
            ComparisonOp.NE -> left != right
            ComparisonOp.CO -> left.contains(right)
            ComparisonOp.SW -> left.startsWith(right)
            ComparisonOp.EW -> left.endsWith(right)
            ComparisonOp.GT -> left > right
            ComparisonOp.GE -> left >= right
            ComparisonOp.LT -> left < right
            ComparisonOp.LE -> left <= right
        }
    }
}
