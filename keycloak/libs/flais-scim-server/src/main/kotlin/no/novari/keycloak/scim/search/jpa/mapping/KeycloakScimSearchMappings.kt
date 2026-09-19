package no.novari.keycloak.scim.search.jpa.mapping

import no.novari.keycloak.scim.endpoints.ScimUserEndpoint

internal object KeycloakScimSearchMappings {
    val users =
        ScimMappingRegistry.create(
            resourceTypeDefinition = ScimUserEndpoint.RESOURCE_TYPE_DEFINITION,
            configurators =
                listOf(
                    ScimUserMapping,
                    ScimFintUserMapping,
                    ScimEnterpriseUserMapping,
                ),
        )
}
