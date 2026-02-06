package no.fintlabs.test.common.annotations

import no.fintlabs.test.common.extensions.kc.KcEnvExtension
import no.fintlabs.test.common.extensions.pw.PwArtifactsOnFailureExtension
import no.fintlabs.test.common.extensions.pw.PwEnvExtension
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Convenience test annotation that wires up Keycloak and Playwright test extensions.
 * Sets up a Keycloak test environment, a Playwright browser session, and automatically
 * collects Playwright artifacts when a test fails.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(
    KcEnvExtension::class,
    PwArtifactsOnFailureExtension::class,
    PwEnvExtension::class,
)
annotation class PwTest
