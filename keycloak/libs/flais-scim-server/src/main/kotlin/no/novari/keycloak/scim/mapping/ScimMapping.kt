package no.novari.keycloak.scim.mapping

import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

internal abstract class ScimMapping<S : Any, E : Any>(
    internal val type: KClass<S>,
) {
    internal open val ignoreNotMapped: Boolean = false

    internal val mappings =
        linkedMapOf<String, FieldMapping<E>>()

    protected fun property(
        property: KProperty1<S, *>,
        mapping: FieldMapping<E>,
    ) {
        register(property.name, mapping)
    }

    protected fun property(
        name: String,
        mapping: FieldMapping<E>,
    ) {
        register(name, mapping)
    }

    /**
     * Complex single-value property.
     *
     * Works for both C and C? because KProperty1's return type is covariant.
     */
    protected fun <C : Any> complex(
        property: KProperty1<S, C?>,
        mapping: FieldMapping<E>,
        configurator: ScimMapping<C, E>,
    ) {
        complex(
            name = property.name,
            mapping = mapping,
            configurator = configurator,
        )
    }

    /**
     * Complex collection such as List<Role>, Set<Member>, etc.
     */
    protected fun <C : Any> complexCollection(
        property: KProperty1<S, Collection<C>?>,
        mapping: FieldMapping<E>,
        configurator: ScimMapping<C, E>,
    ) {
        complex(
            name = property.name,
            mapping = mapping,
            configurator = configurator,
        )
    }

    /**
     * Fallback for SCIM properties that cannot be referenced using ::property.
     */
    protected fun <C : Any> complex(
        name: String,
        mapping: FieldMapping<E>,
        configurator: ScimMapping<C, E>,
    ) {
        configurator.validate()

        register(name, mapping)

        configurator.mappings.forEach { (childPath, childMapping) ->
            register(
                "$name.$childPath",
                childMapping,
            )
        }
    }

    internal fun find(path: String): FieldMapping<E>? = mappings[normalize(path)]

    internal fun validate() {
        if (ignoreNotMapped) return

        val expected =
            type.memberProperties
                .map { normalize(it.name) }
                .toSet()

        val configured =
            mappings.keys
                .map { it.substringBefore('.') }
                .toSet()

        val missing = expected - configured

        require(missing.isEmpty()) {
            buildString {
                append("Missing SCIM mappings for ")
                append(type.qualifiedName)
                append(": ")
                append(missing.sorted().joinToString())
            }
        }
    }

    private fun register(
        path: String,
        mapping: FieldMapping<E>,
    ) {
        val normalized = normalize(path)

        check(normalized !in mappings) {
            "SCIM field '$path' is already configured for ${type.qualifiedName}"
        }

        mappings[normalized] = mapping
    }

    private companion object {
        fun normalize(path: String): String = path.lowercase(Locale.ROOT)
    }
}
