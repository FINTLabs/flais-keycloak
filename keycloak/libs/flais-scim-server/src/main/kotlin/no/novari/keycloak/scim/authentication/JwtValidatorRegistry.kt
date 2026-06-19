package no.novari.keycloak.scim.authentication

import com.github.benmanes.caffeine.cache.Caffeine
import no.novari.keycloak.scim.config.ScimConfig
import org.jboss.logging.Logger

class JwtValidatorRegistry {
    private val logger: Logger = Logger.getLogger(JwtValidatorRegistry::class.java)

    private val validatorCache =
        Caffeine
            .newBuilder()
            .maximumSize(500)
            .build<String, JwtValidator>()

    fun getOrCreate(
        key: String,
        config: ScimConfig,
    ): JwtValidator {
        if (config.authenticationMode == ScimConfig.AuthenticationMode.KEYCLOAK) {
            logger.warnf("Unsupported SCIM authentication mode requested. authMode=%s", config.authenticationMode)
            error("Keycloak authentication mode is not supported yet")
        }
        return validatorCache.asMap().compute(key) { _, existing ->
            existing?.takeIf { it.matches(config) }
                ?: JwtValidator(
                    config.externalJwksUri!!,
                    config.externalIssuer,
                    config.externalAudience,
                ).also {
                    logger.debugf(
                        "Creating SCIM JWT validator. authMode=%s issuerConfigured=%s audienceConfigured=%s jwksHost=%s",
                        config.authenticationMode,
                        config.externalIssuer != null,
                        config.externalAudience != null,
                        config.externalJwksUri,
                    )
                }
        }!!
    }

    private fun JwtValidator.matches(config: ScimConfig) =
        jwksUrl == config.externalJwksUri &&
            expectedIssuer == config.externalIssuer &&
            expectedAudience == config.externalAudience
}
