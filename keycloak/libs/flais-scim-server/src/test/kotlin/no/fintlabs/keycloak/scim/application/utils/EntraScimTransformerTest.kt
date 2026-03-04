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
    fun `normalizePatch rewrites flattened core schema urn keys to attribute path without urn`() {
        val rtd = testRtd()

        val coreUrn = rtd.coreSchema.id
        val givenName = "Jeanette"

        val inputValue =
            mapper.readTree(
                """
                {
                  "$coreUrn:name.givenName": "$givenName",
                  "$urn:employeeId": "$employeeId"
                }
                """.trimIndent(),
            )

        val normalized =
            EntraScimTransformer.normalizePatch(
                PatchRequest(listOf(PatchOperation.add(inputValue))),
                rtd,
            )

        val actual =
            normalized.operations.associate { op ->
                op.path.toString() to op.jsonNode.asText()
            }

        val expected =
            mapOf(
                "name.givenName" to givenName,
                "$urn:employeeId" to employeeId,
            )

        assertEquals(expected, actual)

        normalized.operations.forEach { op ->
            assertFalse(op.jsonNode.isObject)
            assertTrue(op.path.toString().isNotBlank())
        }
    }

    @Test
    fun `normalizePatch rewrites flattened extension keys to correct format using path`() {
        val rtd = testRtd()
        val inputValue =
            mapper.readTree(
                """
                {
                  "$urn:userPrincipalName": "$userPrincipalName",
                  "$urn:employeeId": "$employeeId"
                }
                """.trimIndent(),
            )

        val normalized =
            EntraScimTransformer.normalizePatch(
                PatchRequest(listOf(PatchOperation.add(inputValue))),
                rtd,
            )

        val actual =
            normalized.operations.associate { op ->
                op.path.toString() to op.jsonNode.asText()
            }

        val expected =
            mapOf(
                "$urn:userPrincipalName" to userPrincipalName,
                "$urn:employeeId" to employeeId,
            )

        assertEquals(expected, actual)

        normalized.operations.forEach { op ->
            assertFalse(op.jsonNode.isObject)
        }
    }

    @Test
    fun `normalizePatch keeps already nested extension objects unchanged`() {
        val rtd = testRtd()
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

        val normalized = EntraScimTransformer.normalizePatch(req, rtd)

        val out = normalized.operations[0].jsonNode
        val ext = out.get(urn)
        assertTrue(ext.isObject)
        assertEquals(userPrincipalName, ext.get("userPrincipalName").asText())
        assertEquals(employeeId, ext.get("employeeId").asText())
    }

    @Test
    fun `normalizePatch splits flattened urn keys into standard operations and keeps correct untouched`() {
        val rtd = testRtd()

        val givenName = "Jeanette"
        val familyName = "Bergersen"

        val op1 =
            PatchOperation.create(
                PatchOpType.REPLACE,
                "name.givenName",
                mapper.readTree("\"$givenName\""),
            )

        val op2 =
            PatchOperation.create(
                PatchOpType.REPLACE,
                "name.familyName",
                mapper.readTree("\"$familyName\""),
            )

        val inputValue =
            mapper.readTree(
                """
                {
                  "$urn:userPrincipalName": "$userPrincipalName",
                  "$urn:employeeId": "$employeeId"
                }
                """.trimIndent(),
            )

        val op3 = PatchOperation.add(inputValue)

        val req = PatchRequest(listOf(op1, op2, op3))

        val normalized = EntraScimTransformer.normalizePatch(req, rtd)

        assertEquals(4, normalized.operations.size)

        val normalizedGivenName = normalized.operations.first { it.path.toString() == "name.givenName" }
        assertEquals(PatchOpType.REPLACE, normalizedGivenName.opType)
        assertEquals(givenName, normalizedGivenName.jsonNode.asText())

        val normalizedFamilyName = normalized.operations.first { it.path.toString() == "name.familyName" }
        assertEquals(PatchOpType.REPLACE, normalizedFamilyName.opType)
        assertEquals(familyName, normalizedFamilyName.jsonNode.asText())

        val upnOp = normalized.operations.first { it.path.toString() == "$urn:userPrincipalName" }
        assertEquals(PatchOpType.ADD, upnOp.opType)
        assertEquals(userPrincipalName, upnOp.jsonNode.asText())

        val employeeIdOp = normalized.operations.first { it.path.toString() == "$urn:employeeId" }
        assertEquals(PatchOpType.ADD, employeeIdOp.opType)
        assertEquals(employeeId, employeeIdOp.jsonNode.asText())

        assertFalse(normalized.operations.any { it.path.toString().isBlank() })
    }

    @Test
    fun `normalizePatch does nothing when op jsonNode is not an object`() {
        val rtd = testRtd()
        val inputValue = nodeFactory.textNode("just-a-string")
        val op = PatchOperation.create(PatchOpType.ADD, "", inputValue)
        val req = PatchRequest(listOf(op))

        val normalized = EntraScimTransformer.normalizePatch(req, rtd)
        assertEquals(1, normalized.operations.size)
        assertEquals("just-a-string", normalized.operations[0].jsonNode.asText())
    }

    @Test
    fun `normalizeExtensionSchemas creates schemas array and adds present extension urns only`() {
        val rtd = testRtd()
        val root = nodeFactory.objectNode()

        root.set<ObjectNode>(urn, nodeFactory.objectNode().put("department", "IT"))

        EntraScimTransformer.normalizeExtensionSchemas(root, rtd)

        val schemas = root.get("schemas")
        assertNotNull(schemas)
        assertTrue(schemas.isArray)

        val textValues = schemas.map { it.asText() }.toSet()
        assertTrue(textValues.contains(urn))
    }

    @Test
    fun `normalizeExtensionSchemas does not duplicate existing schema urn`() {
        val rtd = testRtd()
        val root =
            nodeFactory.objectNode().apply {
                putArray("schemas").add(urn)
                set<ObjectNode>(urn, nodeFactory.objectNode().put("department", "IT"))
            }

        root.putArray("schemas").add(urn)

        EntraScimTransformer.normalizeExtensionSchemas(root, rtd)

        val schemas = root.get("schemas")
        val occurrences = schemas.count { it.isTextual && it.asText() == urn }
        assertEquals(1, occurrences)
    }

    @Test
    fun `normalizeExtensionSchemas ignores non-text schema entries when calculating existing`() {
        val rtd = testRtd()
        val root = nodeFactory.objectNode()

        root.set<ObjectNode>(urn, nodeFactory.objectNode())

        val schemas = root.putArray("schemas")
        schemas.add(nodeFactory.objectNode().put("not", "text"))
        schemas.add(urn)

        EntraScimTransformer.normalizeExtensionSchemas(root, rtd)

        val occurrences =
            (root.get("schemas"))
                .count { it.isTextual && it.asText() == urn }

        assertEquals(1, occurrences)
    }
}
