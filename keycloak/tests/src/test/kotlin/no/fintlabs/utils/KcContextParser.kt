package no.fintlabs.utils

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Utility for extracting and parsing the `kcContext` object from Keycloak login pages.
 *
 * Keycloak themes embed a JavaScript variable `kcContext` into the rendered HTML.
 * This object contains information about the page (e.g. error state, IDP providers,
 * form actions). Tests can use it to assert that the correct Keycloak page was shown
 * without needing to simulate a full browser.
 *
 * Workflow:
 * - Uses a regex to find `const kcContext = { ... };` inside the HTML.
 * - Cleans up the snippet to ensure it's valid JSON (e.g. removes trailing commas,
 *   replaces inline function references).
 * - Deserializes it into the [KcContext] data class using kotlinx.serialization.
 */
object KcContextParser {
    object HttpUrlAsStringSerializer : KSerializer<HttpUrl> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("HttpUrl", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: HttpUrl) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): HttpUrl {
            val s = decoder.decodeString()
            return s.toHttpUrlOrNull()
                ?: error("Invalid HttpUrl string: $s")
        }
    }

    @Serializable
    data class Provider(
        val name: String,
        val alias: String,
    )

    @Serializable
    data class Url(
        @Serializable(with = HttpUrlAsStringSerializer::class)
        val loginAction: HttpUrl? = null,
    )

    @Serializable
    data class Message(
        val summary: String,
        val type: String,
        val error: Boolean,
        val success: Boolean,
        val warning: Boolean
    )

    @Serializable
    data class KcContext(
        val pageId: String,
        val url: Url,
        val message: Message? = null,
        val providers: List<Provider>? = null,
    )

    fun parseKcContext(html: String): KcContext {
        val regex = Regex("""const\s+kcContext\s*=\s*(\{.*?});""", RegexOption.DOT_MATCHES_ALL)
        val rawJsonLike = regex.find(html)?.groupValues?.get(1)
            ?: error("Could not extract kcContext from HTML")

        val cleaned = rawJsonLike
            .replace(Regex(""",\s*([}\]])"""), "$1")
            .replace(Regex("""\bfunction\b[^{]+"""), "\"\"")

        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(cleaned)
    }
}
