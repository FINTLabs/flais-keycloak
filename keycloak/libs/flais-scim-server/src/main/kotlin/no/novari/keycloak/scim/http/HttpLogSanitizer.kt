package no.novari.keycloak.scim.http

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.ws.rs.core.MediaType
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

internal object HttpLogSanitizer {
    private const val REDACTED = "[REDACTED]"
    private val mapper = ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    private val sensitiveNames =
        listOf("password", "passwd", "secret", "token", "authorization", "cookie", "apikey", "privatekey", "credential")

    private fun sensitive(name: String): Boolean {
        val normalized = name.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
        return sensitiveNames.any(normalized::contains)
    }

    fun quote(value: String): String = singleLine(mapper.writeValueAsString(value))

    fun headers(headers: Map<String, List<*>>): String =
        singleLine(
            mapper.writeValueAsString(
                headers.mapValues { (name, values) ->
                    if (sensitive(name)) {
                        listOf(REDACTED)
                    } else {
                        values.map { value ->
                            val text = value.toString()
                            if (name.equals("Location", true) || name.equals("Referer", true)) {
                                runCatching { uri(URI.create(text)) }.getOrDefault("<invalid URI omitted>")
                            } else {
                                text
                            }
                        }
                    }
                },
            ),
        )

    fun uri(uri: URI): String {
        val base = uri.toString().substringBefore('?').substringBefore('#')
        val safeBase = if (uri.rawUserInfo == null) base else base.replace("${uri.rawUserInfo}@", "$REDACTED@")
        val query =
            uri.rawQuery?.split('&')?.joinToString("&") { parameter ->
                val name = parameter.substringBefore('=')
                val decoded = runCatching { URLDecoder.decode(name, UTF_8) }.getOrDefault(name)
                if (sensitive(decoded)) "$name=$REDACTED" else parameter
            }
        return safeBase + (query?.let { "?$it" } ?: "")
    }

    fun body(
        bytes: ByteArray,
        mediaType: MediaType,
    ): String =
        try {
            val charset = mediaType.parameters["charset"]?.let(Charset::forName) ?: UTF_8
            val json = mapper.readTree(String(bytes, charset))
            if (json == null) {
                "<empty>"
            } else {
                redact(json)
                singleLine(mapper.writeValueAsString(json))
            }
        } catch (_: Exception) {
            // Never fall back to raw text: malformed or truncated JSON can still contain credentials.
            "<invalid JSON body omitted>"
        }

    private fun redact(node: JsonNode) {
        when {
            node.isObject -> {
                val objectNode = node as ObjectNode
                val sensitivePatch =
                    node
                        .path("path")
                        .takeIf { it.isTextual }
                        ?.asText()
                        ?.let(::sensitive) == true
                val names = node.fieldNames().asSequence().toList()
                names.forEach { name ->
                    if (sensitive(name) || (sensitivePatch && name.equals("value", true))) {
                        objectNode.put(name, REDACTED)
                    } else {
                        redact(node.path(name))
                    }
                }
            }
            node.isArray -> node.forEach(::redact)
        }
    }

    private fun singleLine(value: String): String =
        value.replace("\u0085", "\\u0085").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
}
