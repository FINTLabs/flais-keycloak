package no.novari.keycloak.scim.utils

import com.google.common.net.InternetDomainName

internal class UserPrincipalNameDomainMatcher private constructor(
    private val userPrincipalNameDomain: InternetDomainName,
) {
    fun matches(configuredDomain: String?): Boolean {
        val configured = configuredDomain?.trim() ?: return false
        if (configured.equals("ANY", ignoreCase = true)) return true

        val wildcardPrefix = "*."
        val isWildcard = configured.startsWith(wildcardPrefix)
        val idpDomain =
            configured
                .removePrefix(wildcardPrefix)
                .toInternetDomainNameOrNull()
                ?: return false

        return if (isWildcard) {
            userPrincipalNameDomain.hasDomainSuffix(idpDomain)
        } else {
            userPrincipalNameDomain == idpDomain
        }
    }

    private fun InternetDomainName.hasDomainSuffix(suffix: InternetDomainName): Boolean =
        parts().size >= suffix.parts().size && parts().takeLast(suffix.parts().size) == suffix.parts()

    companion object {
        fun from(userPrincipalName: String?): UserPrincipalNameDomainMatcher? {
            val domain =
                userPrincipalName
                    ?.substringAfter('@', missingDelimiterValue = "")
                    ?.toInternetDomainNameOrNull()
                    ?: return null

            return UserPrincipalNameDomainMatcher(domain)
        }
    }
}

private fun String.toInternetDomainNameOrNull(): InternetDomainName? = runCatching { InternetDomainName.from(trim()) }.getOrNull()
