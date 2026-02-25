package no.fintlabs.test.integration.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
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
 * Keycloakify embeds a JavaScript variable `kcContext` into the rendered HTML.
 * This object contains information about the page (e.g. error state, IDP providers,
 * form actions). Tests use this information to assert correct information was returned.
 */
object KcContextParser {
    object HttpUrlAsStringSerializer : KSerializer<HttpUrl> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("HttpUrl", PrimitiveKind.STRING)

        override fun serialize(
            encoder: Encoder,
            value: HttpUrl,
        ) {
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
    data class Organization(
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
        val warning: Boolean,
    )

    @Serializable
    data class KcContext(
        val pageId: String,
        val url: Url,
        val message: Message? = null,
        val organizations: List<Organization>? = null,
        val providers: List<Provider>? = null,
    )

    fun parseKcContext(html: String): KcContext {
        val objRegex =
            Regex(
                pattern = """const\s+kcContext\s*=\s*(\{.*?})\s*;""",
                options = setOf(RegexOption.DOT_MATCHES_ALL),
            )
        val rawObj =
            objRegex.find(html)?.groupValues?.get(1)
                ?: error("Could not extract kcContext from HTML")

        val cleaned =
            rawObj
                .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("(?<!:)//.*?$", RegexOption.MULTILINE), "")
                .replace(Regex(""",\s*([}\]])"""), "$1")

        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString<KcContext>(cleaned)
    }
}
