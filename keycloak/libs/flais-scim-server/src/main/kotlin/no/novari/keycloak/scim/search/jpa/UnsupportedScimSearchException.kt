package no.novari.keycloak.scim.search.jpa

internal class UnsupportedScimSearchException(
    message: String,
) : RuntimeException(message)
