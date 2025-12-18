package no.fintlabs.application.access

import no.fintlabs.access.ClientAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ClientAccessTest {
    @Test
    fun `CLIENT_WHITELIST_ORGANIZATIONS_ATTRIBUTE has expected value`() {
        assertEquals(
            "permission.whitelisted.organizations",
            ClientAccess.CLIENT_WHITELIST_ORGANIZATIONS_ATTRIBUTE,
        )
    }

    @Test
    fun `CLIENT_BLACKLIST_ORGANIZATIONS_ATTRIBUTE has expected value`() {
        assertEquals(
            "permission.blacklisted.organizations",
            ClientAccess.CLIENT_BLACKLIST_ORGANIZATIONS_ATTRIBUTE,
        )
    }

    @Test
    fun `constants are not blank`() {
        assertFalse(ClientAccess.CLIENT_WHITELIST_ORGANIZATIONS_ATTRIBUTE.isBlank())
        assertFalse(ClientAccess.CLIENT_BLACKLIST_ORGANIZATIONS_ATTRIBUTE.isBlank())
    }
}
