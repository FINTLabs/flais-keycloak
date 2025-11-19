package no.fintlabs.keycloak.scim.authentication

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.keycloak.jose.jwk.JWKParser
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.PublicKey
import java.util.List
import java.util.Map

object JwksUtils {
    /**
     * Loads all public keys from JWKS URL
     *
     * @param jwksUrl JWKS endpoint URL
     * @return list of public keys
     */
    @Throws(URISyntaxException::class, IOException::class, InterruptedException::class)
    @JvmStatic
    fun getPublicKeysFromJwks(jwksUrl: String): List<JwkKey> {
        val result = mutableListOf<JwkKey>()

        HttpClient.newHttpClient().use { httpClient ->
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI(jwksUrl))
                    .GET()
                    .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

            if (response.statusCode() != 200) {
                throw RuntimeException("Failed to fetch JWKS: HTTP ${response.statusCode()}")
            }

            val objectMapper = ObjectMapper()

            val jwks: Map<String, Any> =
                objectMapper.readValue(response.body(), object : TypeReference<Map<String, Any>>() {})

            @Suppress("UNCHECKED_CAST")
            val keys =
                jwks["keys"] as? List<Map<String, Any>>
                    ?: throw RuntimeException("No keys found in JWKS")

            if (keys.isEmpty()) {
                throw RuntimeException("No keys found in JWKS")
            }

            for (jwk in keys) {
                val kid = jwk["kid"] as? String ?: continue
                val use = jwk["use"] as? String ?: "sig"

                val jwkJson = objectMapper.writeValueAsString(jwk)

                val publicKey: PublicKey =
                    JWKParser
                        .create()
                        .parse(jwkJson)
                        .toPublicKey()

                result.add(JwkKey(publicKey, kid, use))
            }

            return result
        }
    }
}
