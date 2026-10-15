package no.novari.test.common.utils

import no.novari.test.common.config.KcConfig
import org.junit.jupiter.api.extension.ExtensionConfigurationException
import java.nio.file.Files
import java.nio.file.Path

object ConfigLoader {
    private const val PROJECT_ROOT_PROPERTY = "project.rootDir"
    private val KEYCLOAK_CONFIG_DIRECTORY = Path.of("config", "kc")

    fun loadKeycloakRealm(realmName: String): KcConfig {
        val relativePath =
            KEYCLOAK_CONFIG_DIRECTORY.resolve("$realmName-realm.json")

        return loadConfig(relativePath, KcConfig::fromFile)
    }

    fun <T> loadConfig(
        requestedPath: Path,
        parser: (Path) -> T,
    ): T {
        val resolvedPath =
            findFile(requestedPath)
                ?: throw ExtensionConfigurationException(
                    "Could not find config file '$requestedPath'. " +
                        "Checked the absolute path and '$PROJECT_ROOT_PROPERTY'.",
                )

        return try {
            parser(resolvedPath)
        } catch (exception: Exception) {
            throw ExtensionConfigurationException(
                "Failed to load config from '$resolvedPath'.",
                exception,
            )
        }
    }

    fun findFile(requestedPath: Path): Path? {
        if (requestedPath.isAbsolute) {
            return requestedPath
                .normalize()
                .takeIf(Files::isRegularFile)
        }

        val projectRoot =
            System
                .getProperty(PROJECT_ROOT_PROPERTY)
                ?.let(Path::of)
                ?: return null

        return projectRoot
            .resolve(requestedPath)
            .normalize()
            .takeIf(Files::isRegularFile)
    }
}
