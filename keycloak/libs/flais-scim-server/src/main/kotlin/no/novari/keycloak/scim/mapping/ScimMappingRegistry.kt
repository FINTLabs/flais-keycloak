package no.novari.keycloak.scim.mapping

import com.unboundid.scim2.common.Path
import com.unboundid.scim2.common.filters.FilterType

internal class ScimMappingRegistry<E : Any>(
    modules: List<ScimMappingModule<E>>,
    private val defaultSchemaUrn: String?,
) {
    private val tables =
        modules.associate { module ->
            module.schemaUrn.normalizedUrn() to module.entries.associate { it.path.normalized() to it.mapping }
        }

    fun resolve(path: Path): FieldMapping<E>? {
        val table = tables[path.schemaUrn.normalizedUrn()] ?: return null
        val key = path.normalizedPath(table) ?: return null
        return table[key]
    }

    private fun Path.normalizedPath(table: Map<String, FieldMapping<E>>): String? {
        val key = StringBuilder()
        forEachIndexed { index, element ->
            if (element.valueFilter != null && !element.isAlwaysSatisfied(table)) {
                return null
            }
            if (index > 0) key.append('.')
            key.append(element.attribute.lowercase())
        }
        return key.toString()
    }

    private fun Path.Element.isAlwaysSatisfied(table: Map<String, FieldMapping<E>>): Boolean {
        val valueFilter = valueFilter ?: return true
        if (valueFilter.filterType != FilterType.EQUAL) return false

        val subPath = valueFilter.attributePath ?: return false
        val comparisonValue = valueFilter.comparisonValue ?: return false
        if (!comparisonValue.isBoolean) return false

        val fieldKey =
            buildString {
                append(attribute.lowercase())
                subPath.forEachIndexed { _, element ->
                    append('.')
                    append(element.attribute.lowercase())
                }
            }

        val constant = table[fieldKey] as? Constant<E> ?: return false
        return constant.kind == FieldKind.BOOLEAN && constant.value == comparisonValue.booleanValue()
    }

    private inline fun Path.forEachIndexed(action: (Int, Path.Element) -> Unit) {
        var index = 0
        for (element in this) {
            action(index, element)
            index++
        }
    }

    private fun String?.normalizedUrn(): String? = (this ?: defaultSchemaUrn)?.lowercase()
}
