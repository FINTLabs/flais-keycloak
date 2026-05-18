package no.novari.test.common.annotations

import no.novari.test.common.environment.kc.KcEnvironmentExtension
import no.novari.test.common.environment.pw.PwArtifactsOnFailureExtension
import no.novari.test.common.environment.pw.PwEnvironmentExtension
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Convenience test annotation that wires up Keycloak and Playwright test extensions.
 * Sets up a Keycloak test environment, a Playwright browser session, and automatically
 * collects Playwright artifacts when a test fails.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(
    KcEnvironmentExtension::class,
    PwArtifactsOnFailureExtension::class,
    PwEnvironmentExtension::class,
)
annotation class PwTest
