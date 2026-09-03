package no.novari.keycloak.scim.store

import com.unboundid.scim2.common.filters.Filter
import java.security.MessageDigest
import java.util.Base64

/**
 * Opaque, stateless cursor for SCIM cursor pagination.
 *
 * The payload carries the position (`lastId`) plus a hash of the query it was produced for. The
 * hash is what stops a client pairing a cursor with a *different* organization or filter and
 * silently receiving a nonsensical page; the position would be interpreted against a result set it
 * never came from.
 *
 * No encryption: the payload is a Keycloak user id, which is already exposed as the SCIM `id`.
 * No server-side state, so cursors never expire and survive restarts and rolling deploys.
 */
internal data class ScimCursor(
    val queryHash: String,
    val lastId: String,
) {
    fun encode(): String {
        val payload = "$VERSION:$queryHash:$lastId"
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    internal companion object {
        private const val VERSION = "v1"

        /** Length of the hex-encoded query hash prefix retained in the cursor. */
        private const val HASH_LENGTH = 16

        /**
         * Decodes [value], returning null when it is malformed, of an unknown version, or was
         * issued for a different query. All three cases are client errors.
         */
        fun decode(
            value: String,
            queryHash: String,
        ): ScimCursor? {
            val decoded =
                runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrNull() ?: return null

            val parts = decoded.split(':', limit = 3)
            if (parts.size != 3) return null
            if (parts[0] != VERSION) return null
            if (parts[1] != queryHash) return null
            if (parts[2].isEmpty()) return null

            return ScimCursor(parts[1], parts[2])
        }

        /**
         * Identifies the query a cursor belongs to. Uses the parsed filter's normalized string so
         * that cosmetically different but equivalent filters share a cursor.
         */
        fun queryHash(
            filter: Filter?,
            organizationGroupId: String,
        ): String {
            val normalized = "$organizationGroupId\n${filter?.toString().orEmpty()}"
            val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(HASH_LENGTH)
        }
    }
}
