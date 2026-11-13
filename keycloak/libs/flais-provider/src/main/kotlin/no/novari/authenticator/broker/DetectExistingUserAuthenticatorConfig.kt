package no.novari.authenticator.broker

internal object DetectExistingUserAuthenticatorConfig {
    const val ERROR_MESSAGE_KEY = "errorMessageKey"
    const val DEFAULT_ERROR_MESSAGE_KEY = "accessDeniedMessage"
    const val MISSING_ERROR_MESSAGE_KEY_MESSAGE =
        "FLAIS Detect Existing User requires errorMessageKey to be configured."
}
