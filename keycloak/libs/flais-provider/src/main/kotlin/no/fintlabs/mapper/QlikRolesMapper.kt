package no.fintlabs.mapper

import no.fintlabs.utils.OrgAttributeUtils.getAttributeValue
import org.jboss.logging.Logger
import org.keycloak.models.ClientSessionContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.ProtocolMapperModel
import org.keycloak.models.UserSessionModel
import org.keycloak.protocol.ProtocolMapperUtils
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.representations.IDToken

class QlikRolesMapper :
    AbstractOIDCProtocolMapper(),
    OIDCAccessTokenMapper,
    OIDCIDTokenMapper {
    private val logger: Logger = Logger.getLogger(QlikRolesMapper::class.java)

    companion object {
        private const val PROVIDER_ID = "qlik-roles-mapper"

        const val CFG_TENANT_COUNTY_MAP = "tenantToCountyMap"
        const val CFG_PASSTHROUGH_COUNTIES = "passthroughCounty"
        const val CFG_TENANT_ATTRIBUTE = "tenantAttribute"
        const val CFG_ROLES_ATTRIBUTE = "rolesAttribute"
        const val CFG_ALLOWED_ROLE_PREFIXES = "allowedRolePrefixes"

        private val CONFIG_PROPERTIES: List<ProviderConfigProperty> =
            buildList {
                add(
                    ProviderConfigProperty().apply {
                        name = CFG_TENANT_COUNTY_MAP
                        label = "Tenant→County (key=value)"
                        helpText = "Multiline list of mapping tenant id to county. One entry per line. Format: <tenant-uuid>=<value>"
                        type = ProviderConfigProperty.TEXT_TYPE
                        isRequired = true
                    },
                )

                add(
                    ProviderConfigProperty().apply {
                        name = CFG_PASSTHROUGH_COUNTIES
                        label = "Passthrough counties"
                        helpText = "Multiline list of counties for which roles should NOT be prefixed. One entry per line."
                        type = ProviderConfigProperty.TEXT_TYPE
                        isRequired = false
                    },
                )

                add(
                    ProviderConfigProperty().apply {
                        name = CFG_ALLOWED_ROLE_PREFIXES
                        label = "Allowed role prefixes"
                        helpText =
                            "Multiline list of allowed role prefixes. One entry per line. Only roles starting with one of these prefixes will be included."
                        type = ProviderConfigProperty.TEXT_TYPE
                        isRequired = true
                    },
                )

                add(
                    ProviderConfigProperty().apply {
                        name = CFG_TENANT_ATTRIBUTE
                        label = "Tenant ID attribute"
                        helpText = "Name of the attribute on the Organization that is containing tenant ID."
                        type = ProviderConfigProperty.STRING_TYPE
                        isRequired = true
                    },
                )

                add(
                    ProviderConfigProperty().apply {
                        name = CFG_ROLES_ATTRIBUTE
                        label = "Roles attribute"
                        helpText = "Name of the attribute on the User that is containing roles."
                        type = ProviderConfigProperty.STRING_TYPE
                        isRequired = true
                    },
                )

                add(
                    ProviderConfigProperty().apply {
                        name = ProtocolMapperUtils.MULTIVALUED
                        label = ProtocolMapperUtils.MULTIVALUED_LABEL
                        helpText = ProtocolMapperUtils.MULTIVALUED_HELP_TEXT
                        type = ProviderConfigProperty.BOOLEAN_TYPE
                        defaultValue = "true"
                    },
                )

                OIDCAttributeMapperHelper.addTokenClaimNameConfig(this)
                OIDCAttributeMapperHelper.addIncludeInTokensConfig(
                    this,
                    QlikRolesMapper::class.java,
                )
            }
    }

    override fun getDisplayCategory(): String = "Qlik"

    override fun getDisplayType(): String = "Qlik roles"

    override fun getHelpText(): String = "Converts tenant ID and user roles into roles that is formatted for Qlik"

    override fun getId(): String = PROVIDER_ID

    override fun getConfigProperties(): List<ProviderConfigProperty> = CONFIG_PROPERTIES

    override fun setClaim(
        token: IDToken,
        mappingModel: ProtocolMapperModel,
        userSession: UserSessionModel,
        keycloakSession: KeycloakSession,
        clientSessionCtx: ClientSessionContext,
    ) {
        logger.debugf("Adding Qlik roles claim for user=%s", userSession.user.username)

        val tenantId =
            getAttributeValue(
                keycloakSession,
                userSession.user,
                mappingModel.config[CFG_TENANT_ATTRIBUTE] ?: return,
            ) ?: return
        val rolesAttrName = mappingModel.config[CFG_ROLES_ATTRIBUTE]
        val userRoles: List<String> = userSession.user.attributes[rolesAttrName] ?: emptyList()

        val county: String = tenantToCounty(mappingModel, tenantId) ?: return
        val passthroughCounties = passthroughCounties(mappingModel)
        val allowedRolePrefixes = allowedRolePrefixes(mappingModel)

        val formattedRoles: List<String> = formatRoles(userRoles, county, passthroughCounties, allowedRolePrefixes)

        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, formattedRoles)
    }

    fun tenantToCounty(
        mappingModel: ProtocolMapperModel,
        tenantId: String,
    ): String? =
        mappingModel.config[CFG_TENANT_COUNTY_MAP]
            ?.lineSequence()
            ?.mapNotNull { rawLine ->
                val line = rawLine.trim()

                val parts = line.split('=', limit = 2)
                if (parts.size != 2) return@mapNotNull null

                if (parts[0] == tenantId) parts[1] else null
            }?.firstOrNull()

    fun formatRoles(
        roles: List<String>,
        county: String,
        passthroughCounties: Set<String>,
        allowedPrefixes: List<String>,
    ): List<String> {
        if (roles.isEmpty()) return emptyList()

        if (county in passthroughCounties) {
            return roles
        }

        val result = ArrayList<String>()

        for (role in roles) {
            if (allowedPrefixes.none { role.startsWith(it) }) {
                continue
            }

            result.add("${county}_$role")
            result.add(role)
        }

        return result
    }

    fun passthroughCounties(mappingModel: ProtocolMapperModel): Set<String> =
        mappingModel.config[CFG_PASSTHROUGH_COUNTIES]
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    fun allowedRolePrefixes(mappingModel: ProtocolMapperModel): List<String> =
        mappingModel.config[CFG_ALLOWED_ROLE_PREFIXES]
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            ?: emptyList()
}
