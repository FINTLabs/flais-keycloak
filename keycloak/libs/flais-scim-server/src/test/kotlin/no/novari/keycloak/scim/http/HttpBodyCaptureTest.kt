package no.novari.keycloak.scim.http

import jakarta.ws.rs.core.MediaType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HttpBodyCaptureTest {
    private val json = MediaType("application", "scim+json")

    @Test
    fun `mixed single and bulk reads preserve bytes without double counting`() {
        val body = """{"userName":"alice","password":"input-secret"}""".toByteArray()
        val capture = HttpBodyCapture(1024)
        val input = CapturingInputStream(ByteArrayInputStream(body), capture)
        val received = byteArrayOf(input.read().toByte()) + input.readAllBytes()

        assertArrayEquals(body, received)
        assertEquals(body.size.toLong(), capture.totalBytes)
        assertTrue(capture.endOfInput)
        assertEquals("""{"userName":"alice","password":"[REDACTED]"}""", capture.describe(json, null, true))
    }

    @Test
    fun `single and bulk writes preserve bytes without double counting`() {
        val body = """{"totalResults":0}""".toByteArray()
        val capture = HttpBodyCapture(1024)
        val destination = ByteArrayOutputStream()
        val output = CapturingOutputStream(destination, capture)
        output.write(body[0].toInt())
        output.write(body, 1, body.size - 1)
        output.flush()

        assertArrayEquals(body, destination.toByteArray())
        assertEquals(body.size.toLong(), capture.totalBytes)
        assertEquals(String(body), capture.describe(json, null, true))
    }

    @Test
    fun `oversized bodies still reach the client in full`() {
        val body = """{"password":"a-secret-that-crosses-the-capture-limit"}""".toByteArray()
        val capture = HttpBodyCapture(12)
        val destination = ByteArrayOutputStream()
        CapturingOutputStream(destination, capture).write(body)

        assertArrayEquals(body, destination.toByteArray())
        assertEquals(body.size.toLong(), capture.totalBytes)
        assertEquals("<body omitted: exceeds 12 bytes>", capture.describe(json, null, true))
    }

    @Test
    fun `exact limit is accepted and zero disables body content`() {
        val body = """{"active":true}""".toByteArray()
        val exact = HttpBodyCapture(body.size)
        exact.record(body, 0, body.size)
        val disabled = HttpBodyCapture(0)
        disabled.record(body, 0, body.size)

        assertEquals(String(body), exact.describe(json, null, true))
        assertEquals("<body logging disabled>", disabled.describe(json, null, true))
    }

    @Test
    fun `unread partial encoded and binary bodies have explicit markers`() {
        val capture = HttpBodyCapture(1024)
        assertEquals("<body not consumed>", capture.describe(json, null, false))
        val partial = "{\"password\":\"partial-secret".toByteArray()
        capture.record(partial, 0, partial.size)

        assertEquals("<incomplete body omitted>", capture.describe(json, null, false))
        assertEquals("<encoded body omitted>", capture.describe(json, "gzip", true))
        assertEquals("<non-JSON body omitted>", capture.describe(MediaType("application", "octet-stream"), null, true))
    }

    @Test
    fun `skips and resets preserve input semantics and suppress incomplete captures`() {
        val body = """{"active":true}""".toByteArray()
        val capture = HttpBodyCapture(1024)
        val input = CapturingInputStream(ByteArrayInputStream(body), capture)
        input.mark(1024)
        assertEquals(2L, input.skip(2))
        assertArrayEquals(body.copyOfRange(2, body.size), input.readAllBytes())
        assertEquals("<incomplete body omitted>", capture.describe(json, null, true))
        input.reset()
        assertArrayEquals(body, input.readAllBytes())
        assertEquals("<incomplete body omitted>", capture.describe(json, null, true))
    }
}
