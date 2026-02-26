package no.fintlabs.keycloak.scim.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.unboundid.scim2.common.messages.PatchOperation
import com.unboundid.scim2.common.messages.PatchRequest
import com.unboundid.scim2.common.utils.JsonUtils
import com.unboundid.scim2.server.utils.ResourceTypeDefinition

object EntraScimTransformer {
    fun normalizeSchemasForPresentExtensions(
        root: ObjectNode,
        rtd: ResourceTypeDefinition,
    ) {
        val extensionSchemaIds: Set<String> =
            rtd.schemaExtensions
                .keys
                .mapNotNull { schemaResource -> schemaResource.id }
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
    }

    fun normalizePatch(request: PatchRequest): PatchRequest {
        val normalizedOps =
            request.operations.map { op ->
                val node = op.jsonNode ?: return@map op
                if (!node.isObject) return@map op

                val rewritten = rewriteFlattenedExtensionKeys(node.deepCopy<ObjectNode>())

                if (rewritten == node) return@map op

                PatchOperation.create(op.opType, op.path, rewritten)
            }

        return PatchRequest(normalizedOps)
    }

    private fun rewriteFlattenedExtensionKeys(v: ObjectNode): ObjectNode {
        val movedByUrn = mutableMapOf<String, ObjectNode>()
        val keysToRemove = mutableListOf<String>()

        for ((key, valueNode) in v.properties()) {
            if (!key.startsWith("urn:")) continue
            if (valueNode.isObject) continue

            val lastColon = key.lastIndexOf(':')
            if (lastColon <= "urn:".length) continue

            val urnCandidate = key.substring(0, lastColon)
            val attrName = key.substring(lastColon + 1)

            if (!urnCandidate.startsWith("urn:")) continue
            if (attrName.isBlank()) continue

            val extObj =
                movedByUrn.getOrPut(urnCandidate) {
                    (v.get(urnCandidate) as? ObjectNode) ?: JsonUtils.getJsonNodeFactory().objectNode()
                }

            extObj.set<JsonNode>(attrName, valueNode)
            keysToRemove.add(key)
        }

        if (keysToRemove.isEmpty()) return v

        keysToRemove.forEach(v::remove)
        movedByUrn.forEach { (urn, obj) -> v.set<ObjectNode>(urn, obj) }
        return v
    }
}
