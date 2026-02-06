package no.fintlabs.test.common.extensions

import org.junit.jupiter.api.extension.ExtensionContext

object SharedExtensionStore {
    val NS = ExtensionContext.Namespace.create("no.fintlabs.test.common")

    const val KC_ENV = "kc-env"
    const val KC_CFG = "kc-config"

    const val PW_ENV = "pw-env"
    const val PW_SESSION = "pw-session"
    const val PW_PAGE = "pw-page"
    const val PW_BROWSER_NAME = "pw-browser-name"
}
