package no.fintlabs.keycloak.scim.authentication

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector.fromJWKSetURL
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.jboss.logging.Logger
import java.net.URI

class JwtValidator(
    val jwksUrl: String,
    val expectedIssuer: String? = null,
    val expectedAudience: String? = null,
) {
    private val logger = Logger.getLogger(JwtValidator::class.java)

    private val selector = fromJWKSetURL<SecurityContext>(URI.create(jwksUrl).toURL())
    private val processor =
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = selector
            jwtClaimsSetVerifier =
                DefaultJWTClaimsVerifier(
                    JWTClaimsSet
                        .Builder()
                        .apply {
                            if (!expectedIssuer.isNullOrEmpty()) {
                                issuer(expectedIssuer)
                            }
                            if (!expectedAudience.isNullOrEmpty()) {
                                audience(expectedAudience)
                            }
                        }.build(),
                    null,
                )
        }

    fun isValid(token: String) =
        runCatching {
            processor.process(token, null)
        }.onFailure { e ->
            if (e is JOSEException) {
                logger.error("Unexpected JWT validation exception", e)
            }
        }.isSuccess
}
