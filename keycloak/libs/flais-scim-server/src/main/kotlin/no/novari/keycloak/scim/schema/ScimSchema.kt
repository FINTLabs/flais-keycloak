package no.novari.keycloak.scim.schema

import com.unboundid.scim2.common.annotations.Schema
import no.novari.keycloak.scim.mapping.ScimFieldPath
import kotlin.reflect.KClass
import kotlin.reflect.KFunction1
import kotlin.reflect.KProperty1

internal class ScimSchema<S : Any>(
    type: KClass<S>,
) {
    val urn: String =
        requireNotNull(type.java.getAnnotation(Schema::class.java)?.id) {
            "${type.qualifiedName} is missing @Schema"
        }

    fun path(attribute: KProperty1<in S, *>): ScimFieldPath = ScimFieldPath(urn, attribute.name)

    fun path(getter: KFunction1<S, *>): ScimFieldPath = ScimFieldPath(urn, getter.attributeName())

    fun <C : Any> path(
        attribute: KProperty1<in S, *>,
        childAttribute: KProperty1<in C, *>,
    ): ScimFieldPath = ScimFieldPath(urn, "${attribute.name}.${childAttribute.name}")

    fun <C : Any> path(
        attribute: KProperty1<in S, *>,
        childGetter: KFunction1<C, *>,
    ): ScimFieldPath = ScimFieldPath(urn, "${attribute.name}.${childGetter.attributeName()}")

    private fun KFunction1<*, *>.attributeName(): String {
        val withoutGet = name.removePrefix("get")
        require(withoutGet != name && withoutGet.isNotEmpty()) {
            "$name is not a JavaBean getter"
        }
        return withoutGet.replaceFirstChar { it.lowercase() }
    }
}
