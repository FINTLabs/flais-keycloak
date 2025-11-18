package no.fintlabs.keycloak.scim.authentication

import org.jboss.logging.Logger
import org.keycloak.jose.jws.JWSInput
import org.keycloak.jose.jws.JWSInputException
import org.keycloak.jose.jws.crypto.RSAProvider
import org.keycloak.representations.AccessToken
import org.keycloak.util.JsonSerialization
import java.io.IOException
import java.net.URISyntaxException
import java.security.PublicKey

/**
 * Verifies externally issued JWT tokens
 */
class ExternalTokenVerifier(
    private val expectedIssuer: String,
    private val jwksUrl: String,
    private val expectedAudience: String
) {

    companion object {
        private val logger: Logger = Logger.getLogger(ExternalTokenVerifier::class.java)
    }

    /**
     * Verifies the given token.
     *
     * @param tokenString JWT token string
     * @return true if the token is valid, false otherwise
     */
    @Throws(URISyntaxException::class, IOException::class, InterruptedException::class, JWSInputException::class)
    fun verify(tokenString: String): Boolean {
        for (jwkKey in JwksUtils.getPublicKeysFromJwks(jwksUrl)) {
            if (verify(tokenString, jwkKey.publicKey)) {
                logger.info("Token verification succeeded with key: ${jwkKey.kid}")
                return true
            }

            logger.warn("Token verification failed with key: ${jwkKey.kid}")
        }

        return false
    }

    /**
     * Verifies the given token.
     *
     * @param tokenString JWT token string
     * @return true if the token is valid, false otherwise
     */
    @Throws(JWSInputException::class, IOException::class)
    private fun verify(tokenString: String, publicKey: PublicKey): Boolean {
        val jwsInput = JWSInput(tokenString)

        val validSignature = RSAProvider.verify(jwsInput, publicKey)

        if (!validSignature) {
            logger.warn("Token signature verification failed")
            return false
        }

        val token = JsonSerialization.readValue(jwsInput.content, AccessToken::class.java)

        if (token == null) {
            logger.warn("Token could not be parsed")
            return false
        }

        if (token.issuer != expectedIssuer) {
            if ("*" == expectedIssuer) {
                logger.warn(
                    "Token issuer is wildcard, skipping issuer check. " +
                        "This is insecure and should not be used in production. " +
                        "Found issuer is: ${token.issuer}"
                )
            } else {
                logger.warnf(
                    "Token issuer mismatch. Expected: %s, Found: %s",
                    expectedIssuer,
                    token.issuer
                )
                return false
            }
        }

        val audience = token.audience

        if (!arrayContains(audience, expectedAudience)) {
            val audienceStr = java.lang.String.join(",", *audience)

            if ("*" == expectedAudience) {
                logger.warn(
                    "Token audience is wildcard, skipping audience check. " +
                        "This is insecure and should not be used in production. " +
                        "Found audience is: $audienceStr"
                )
            } else {
                logger.warnf(
                    "Token audience mismatch. Expected to contain: %s, Found: %s",
                    expectedAudience,
                    audienceStr
                )
                return false
            }
        }

        return true
    }

    /**
     * Checks if the given array contains the given value
     *
     * @param array array
     * @param value value
     * @return true if the array contains the value, false otherwise
     */
    private fun arrayContains(array: Array<String>, value: String): Boolean {
        for (s in array) {
            if (s == value) {
                return true
            }
        }
        return false
    }
}
