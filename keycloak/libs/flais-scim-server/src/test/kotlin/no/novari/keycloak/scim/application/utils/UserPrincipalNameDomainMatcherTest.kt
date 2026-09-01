package no.novari.keycloak.scim.application.utils

import no.novari.keycloak.scim.utils.UserPrincipalNameDomainMatcher
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserPrincipalNameDomainMatcherTest {
    @Test
    fun `from returns null for missing or malformed userPrincipalName domains`() {
        assertNull(UserPrincipalNameDomainMatcher.from(null))
        assertNull(UserPrincipalNameDomainMatcher.from("alice.basic"))
        assertNull(UserPrincipalNameDomainMatcher.from("alice.basic@invalid domain"))
    }

    @Test
    fun `from creates matcher for valid userPrincipalName domain`() {
        assertNotNull(UserPrincipalNameDomainMatcher.from("alice.basic@test.com"))
    }

    @Test
    fun `matches any configured domain`() {
        val matcher = UserPrincipalNameDomainMatcher.from("alice.basic@test.com")!!

        assertTrue(matcher.matches("ANY"))
        assertTrue(matcher.matches(" aNy "))
    }

    @Test
    fun `matches exact configured domain case insensitively`() {
        val matcher = UserPrincipalNameDomainMatcher.from("alice.basic@test.com")!!

        assertTrue(matcher.matches("TEST.COM"))
        assertFalse(matcher.matches("other.com"))
    }

    @Test
    fun `matches exact configured domains outside Telemark fixtures`() {
        assertTrue(UserPrincipalNameDomainMatcher.from("alice.basic@rogaland.no")!!.matches("rogaland.no"))
        assertTrue(UserPrincipalNameDomainMatcher.from("alice.basic@novari.no")!!.matches("novari.no"))
    }

    @Test
    fun `matches wildcard configured domain for base and nested domains`() {
        assertTrue(UserPrincipalNameDomainMatcher.from("alice.basic@test.com")!!.matches("*.test.com"))
        assertTrue(UserPrincipalNameDomainMatcher.from("alice.basic@whatever.test.com")!!.matches("*.test.com"))
        assertTrue(UserPrincipalNameDomainMatcher.from("alice.basic@a.b.test.com")!!.matches("*.test.com"))
    }

    @Test
    fun `matches domains when userPrincipalName local part contains Microsoft external user markers`() {
        val externalTenantUser = UserPrincipalNameDomainMatcher.from("john.doe_example.com#EXT#@tenant.onmicrosoft.com")!!

        assertTrue(externalTenantUser.matches("tenant.onmicrosoft.com"))
        assertTrue(externalTenantUser.matches("*.onmicrosoft.com"))
        assertTrue(UserPrincipalNameDomainMatcher.from("external_user#EXT#@a.b.test.com")!!.matches("*.test.com"))
    }

    @Test
    fun `does not match wildcard configured domain outside suffix`() {
        val matcher = UserPrincipalNameDomainMatcher.from("alice.basic@other.com")!!

        assertFalse(matcher.matches("*.test.com"))
    }

    @Test
    fun `ignores missing or malformed configured domains`() {
        val matcher = UserPrincipalNameDomainMatcher.from("alice.basic@test.com")!!

        assertFalse(matcher.matches(null))
        assertFalse(matcher.matches("invalid domain"))
    }
}
