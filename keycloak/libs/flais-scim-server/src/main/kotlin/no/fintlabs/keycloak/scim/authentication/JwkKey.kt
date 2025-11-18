package no.fintlabs.keycloak.scim.authentication

import java.security.PublicKey

class JwkKey(
    val publicKey: PublicKey,
    val kid: String,
    val use: String
)
