package no.fintlabs.keycloak.scim.application.utils

import com.fasterxml.jackson.databind.node.ObjectNode
import com.unboundid.scim2.common.messages.PatchOpType
import com.unboundid.scim2.common.messages.PatchOperation
import com.unboundid.scim2.common.messages.PatchRequest
import com.unboundid.scim2.common.utils.JsonUtils
import com.unboundid.scim2.server.utils.ResourceTypeDefinition
import no.fintlabs.keycloak.scim.endpoints.ScimUserEndpoint
import no.fintlabs.keycloak.scim.utils.EntraScimTransformer
import no.fintlabs.keycloak.scim.utils.ResourceTypeDefinitionUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EntraScimTransformerTest {
    private val urn = "urn:ietf:params:scim:schemas:extension:fint:2.0:User"
    private val userPrincipalName = "hato2606@testvigoiks.onmicrosoft.com"
    private val employeeId = "1124"
    private val mapper = JsonUtils.createObjectMapper()
    private val nodeFactory = JsonUtils.getJsonNodeFactory()

    fun testRtd(): ResourceTypeDefinition = ResourceTypeDefinitionUtil.createResourceTypeDefinition(ScimUserEndpoint::class)

    @Test
    fun `normalizePatch rewrites flattened extension keys into nested urn object`() {
        val inputValue =
            mapper.readTree(
                """
                {
                  "$urn:userPrincipalName": "$userPrincipalName",
                  "$urn:employeeId": "$employeeId"
                }
                """.trimIndent(),
            )

        val op = PatchOperation.create(PatchOpType.ADD, "", inputValue)
        val req = PatchRequest(listOf(op))

        val normalized = EntraScimTransformer.normalizePatch(req)
        assertEquals(1, normalized.operations.size)

        val normalizedNode = normalized.operations[0].jsonNode
        assertNotNull(normalizedNode)
        assertTrue(normalizedNode.isObject)
        assertFalse(normalizedNode.has("$urn:userPrincipalName"))
        assertFalse(normalizedNode.has("$urn:employeeId"))

        val ext = normalizedNode.get(urn)
        assertNotNull(ext)
        assertTrue(ext.isObject)
        assertEquals(
            userPrincipalName,
            ext.get("userPrincipalName").asText(),
        )
        assertEquals(employeeId, ext.get("employeeId").asText())
    }

    @Test
    fun `normalizePatch keeps already nested extension objects unchanged`() {
        val inputValue =
            mapper.readTree(
                """
                {
                  "$urn": {
                    "userPrincipalName": "$userPrincipalName",
                    "employeeId": "$employeeId"
                  }
                }
                """.trimIndent(),
            )

        val op = PatchOperation.create(PatchOpType.ADD, "", inputValue)
        val req = PatchRequest(listOf(op))

        val normalized = EntraScimTransformer.normalizePatch(req)

        val out = normalized.operations[0].jsonNode
        val ext = out.get(urn)
        assertTrue(ext.isObject)
        assertEquals(userPrincipalName, ext.get("userPrincipalName").asText())
        assertEquals(employeeId, ext.get("employeeId").asText())
    }

    @Test
    fun `normalizePatch does nothing when op jsonNode is not an object`() {
        val inputValue = nodeFactory.textNode("just-a-string")
        val op = PatchOperation.create(PatchOpType.ADD, "", inputValue)
        val req = PatchRequest(listOf(op))

        val normalized = EntraScimTransformer.normalizePatch(req)
        assertEquals(1, normalized.operations.size)
        assertEquals("just-a-string", normalized.operations[0].jsonNode.asText())
    }

    @Test
    fun `normalizeSchemasForPresentExtensions creates schemas array and adds present extension urns only`() {
        val rtd = testRtd()
        val root = nodeFactory.objectNode()

        root.set<ObjectNode>(urn, nodeFactory.objectNode().put("department", "IT"))

        EntraScimTransformer.normalizeSchemasForPresentExtensions(root, rtd)

        val schemas = root.get("schemas")
        assertNotNull(schemas)
        assertTrue(schemas.isArray)

        val textValues = schemas.map { it.asText() }.toSet()
        assertTrue(textValues.contains(urn))
    }

    @Test
    fun `normalizeSchemasForPresentExtensions does not duplicate existing schema urn`() {
        val rtd = testRtd()
        val root =
            nodeFactory.objectNode().apply {
                putArray("schemas").add(urn)
                set<ObjectNode>(urn, nodeFactory.objectNode().put("department", "IT"))
            }

        root.putArray("schemas").add(urn)

        EntraScimTransformer.normalizeSchemasForPresentExtensions(root, rtd)

        val schemas = root.get("schemas")
        val occurrences = schemas.count { it.isTextual && it.asText() == urn }
        assertEquals(1, occurrences)
    }

    @Test
    fun `normalizeSchemasForPresentExtensions ignores non-text schema entries when calculating existing`() {
        val rtd = testRtd()
        val root = nodeFactory.objectNode()

        root.set<ObjectNode>(urn, nodeFactory.objectNode())

        val schemas = root.putArray("schemas")
        schemas.add(nodeFactory.objectNode().put("not", "text"))
        schemas.add(urn)

        EntraScimTransformer.normalizeSchemasForPresentExtensions(root, rtd)

        val occurrences =
            (root.get("schemas"))
                .count { it.isTextual && it.asText() == urn }

        assertEquals(1, occurrences)
    }
}
