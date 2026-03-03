package no.fintlabs.keycloak.scim.utils

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.unboundid.scim2.common.messages.PatchOperation
import com.unboundid.scim2.common.messages.PatchRequest
import com.unboundid.scim2.server.utils.ResourceTypeDefinition

object EntraScimTransformer {
    fun normalizeExtensionSchemas(
        root: ObjectNode,
        rtd: ResourceTypeDefinition,
    ): ObjectNode {
        val extensionSchemaIds: Set<String> =
            rtd.schemaExtensions
                .keys
                .mapNotNull { it.id }
                .toSet()

        val schemasNode = (root.get("schemas") as? ArrayNode) ?: root.putArray("schemas")
        val existing =
            schemasNode
                .filter { it.isTextual }
                .map { it.asText() }
                .toMutableSet()

        extensionSchemaIds.forEach { urn ->
            if (root.has(urn) && existing.add(urn)) {
                schemasNode.add(urn)
            }
        }

        return root
    }

    fun normalizePatch(
        request: PatchRequest,
        rtd: ResourceTypeDefinition,
    ): PatchRequest {
        val schemaIds: Set<String> =
            rtd.schemaExtensions
                .keys
                .mapNotNull { it.id }
                .plus(rtd.coreSchema.id)
                .toSet()

        val outOps = mutableListOf<PatchOperation>()

        for (op in request.operations) {
            val node = op.jsonNode as? ObjectNode

            if (op.path != null || node == null || !node.isObject) {
                outOps += op
                continue
            }

            val flattened =
                node
                    .properties()
                    .asSequence()
                    .filter { (key, value) -> key.startsWith("urn:") && !value.isObject }
                    .toList()

            if (flattened.isEmpty()) {
                outOps += op
                continue
            }

            val remaining = node.deepCopy()
            flattened.forEach { (key, _) -> remaining.remove(key) }

            if (remaining.size() > 0) {
                outOps += PatchOperation.create(op.opType, op.path, remaining)
            }

            for ((key, value) in flattened) {
                val schemaUrn =
                    schemaIds
                        .asSequence()
                        .filter { key.startsWith("$it:") }
                        .maxByOrNull { it.length } ?: continue

                val attribute = key.removePrefix("$schemaUrn:").trim()
                if (attribute.isBlank()) continue

                val normalizedPath =
                    if (schemaUrn == rtd.coreSchema.id) {
                        attribute
                    } else {
                        "$schemaUrn:$attribute"
                    }

                outOps += PatchOperation.create(op.opType, normalizedPath, value)
            }
        }

        return PatchRequest(outOps)
    }
}
