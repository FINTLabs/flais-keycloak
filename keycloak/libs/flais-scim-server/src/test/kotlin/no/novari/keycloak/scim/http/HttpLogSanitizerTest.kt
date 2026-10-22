package no.novari.keycloak.scim.http

import jakarta.ws.rs.core.MediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class HttpLogSanitizerTest {
    private val json = MediaType("application", "scim+json")

    @Test
    fun `nested credentials and password patch values are redacted`() {
        val body =
            """
            {
                "userName":"alice",
                "password":"first-secret",
                "extension":{"client_secret":"second-secret"},
                "Operations":[
                    {"op":"replace","path":"password","value":"third-secret"},
                    {"op":"replace","value":{"Password":"fourth-secret"}},
                    {"op":"replace","path":"name.givenName","value":"Alice"}
                ]
            }
            """.trimIndent()

        val logged = HttpLogSanitizer.body(body.toByteArray(), json)

        listOf("first-secret", "second-secret", "third-secret", "fourth-secret").forEach {
            assertFalse(logged.contains(it))
        }
        assertTrue(logged.contains("[REDACTED]"))
        assertTrue(logged.contains("alice"))
        assertTrue(logged.contains("Alice"))
    }

    @Test
    fun `sensitive headers and redirect query parameters are redacted case insensitively`() {
        val logged =
            HttpLogSanitizer.headers(
                mapOf(
                    "aUtHoRiZaTiOn" to listOf("Bearer header-secret"),
                    "Set-Cookie" to listOf("session=cookie-secret"),
                    "X-API-Key" to listOf("api-secret"),
                    "Location" to listOf("https://example.test/callback?access_token=redirect-secret&count=10"),
                    "Content-Type" to listOf("application/scim+json"),
                ),
            )

        listOf("header-secret", "cookie-secret", "api-secret", "redirect-secret").forEach {
            assertFalse(logged.contains(it))
        }
        assertTrue(logged.contains("count=10"))
        assertTrue(logged.contains("application/scim+json"))
    }

    @Test
    fun `encoded sensitive query names and URI user info are redacted`() {
        val logged =
            HttpLogSanitizer.uri(
                URI.create("https://user:uri-secret@example.test/Users?%61ccess_token=query-secret&count=5#fragment-secret"),
            )

        assertFalse(logged.contains("uri-secret"))
        assertFalse(logged.contains("query-secret"))
        assertFalse(logged.contains("fragment-secret"))
        assertTrue(logged.contains("count=5"))
    }

    @Test
    fun `malformed and trailing JSON never fall back to raw payloads`() {
        listOf("""{"password":"malformed-secret"""", """{"userName":"alice"} {"password":"trailing-secret"}""").forEach {
            assertEquals("<invalid JSON body omitted>", HttpLogSanitizer.body(it.toByteArray(), json))
        }
    }

    @Test
    fun `payload and header line breaks cannot forge log records`() {
        val logged = HttpLogSanitizer.body("""{"userName":"alice\nFORGED\rRECORD"}""".toByteArray(), json)
        val headers = HttpLogSanitizer.headers(mapOf("X-Request-ID" to listOf("one\r\nFORGED\u2028LINE")))

        listOf(logged, headers, HttpLogSanitizer.quote("a\nb\u2029c")).forEach {
            assertFalse(it.any { char -> char == '\n' || char == '\r' || char == '\u2028' || char == '\u2029' })
        }
        assertTrue(logged.contains("\\n"))
    }

    @Test
    fun `declared JSON charset is honored`() {
        val mediaType = MediaType("application", "json", mapOf("charset" to "UTF-16LE"))
        val body = """{"displayName":"Bjørn"}"""

        assertEquals(body, HttpLogSanitizer.body(body.toByteArray(Charsets.UTF_16LE), mediaType))
    }
}
