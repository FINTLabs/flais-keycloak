package no.fintlabs.application.dtos

import no.fintlabs.dtos.OrgDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OrgDtoTest {
    @Test
    fun `constructor sets properties correctly`() {
        val orgAlias = "org-alias"
        val orgName = "org"
        val orgLogo = "logo.png"

        val dto =
            OrgDto(
                alias = orgAlias,
                name = orgName,
                logo = orgLogo,
            )

        assertEquals(orgAlias, dto.alias)
        assertEquals(orgName, dto.name)
        assertEquals(orgLogo, dto.logo)
    }

    @Test
    fun `logo can be null`() {
        val orgAlias = "org-alias"
        val orgName = "org"
        val orgLogo = null

        val dto =
            OrgDto(
                alias = orgAlias,
                name = orgName,
                logo = orgLogo,
            )

        assertNull(dto.logo)
    }
}
