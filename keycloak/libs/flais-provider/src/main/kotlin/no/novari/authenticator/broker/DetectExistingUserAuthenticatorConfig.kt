package no.novari.authenticator.broker

object DetectExistingUserAuthenticatorConfig {
    const val REQUIRED_ROLE = "requiredRole"
    const val MISSING_REQUIRED_ROLE_MESSAGE =
        "FLAIS Detect Existing User requires requiredRole to be configured."
    const val ERROR_MESSAGE_KEY = "errorMessageKey"
    const val DEFAULT_ERROR_MESSAGE_KEY = "accessDeniedMessage"
    const val MISSING_ERROR_MESSAGE_KEY_MESSAGE =
        "FLAIS Detect Existing User requires errorMessageKey to be configured."
}
