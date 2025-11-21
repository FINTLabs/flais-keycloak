package no.fintlabs.keycloak.scim.authentication

import com.github.benmanes.caffeine.cache.Caffeine
import no.fintlabs.keycloak.scim.config.ScimConfig

class JwtValidatorRegistry {
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
            error("Keycloak authentication mode is not supported yet")
        }
        return validatorCache.asMap().compute(key) { _, existing ->
            existing?.takeIf { it.matches(config) }
                ?: JwtValidator(
                    config.externalJwksUri!!,
                    config.externalIssuer,
                    config.externalAudience,
                )
        }!!
    }

    private fun JwtValidator.matches(config: ScimConfig) =
        jwksUrl == config.externalJwksUri &&
            expectedIssuer == config.externalIssuer &&
            expectedAudience == config.externalAudience
}
