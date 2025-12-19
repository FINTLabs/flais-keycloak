package no.fintlabs.application.dtos

import no.fintlabs.dtos.IdpDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdpDtoTest {
    @Test
    fun `constructor sets properties correctly`() {
        val idpAlias = "idp-alias"
        val idpName = "idp"

        val dto =
            IdpDto(
                alias = idpAlias,
                name = idpName,
            )

        assertEquals(idpAlias, dto.alias)
        assertEquals(idpName, dto.name)
    }
}
