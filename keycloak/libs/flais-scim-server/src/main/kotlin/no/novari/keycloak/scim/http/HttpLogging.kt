package no.novari.keycloak.scim.http

import jakarta.ws.rs.core.MediaType
import org.jboss.logging.Logger
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

internal object HttpLogging {
    const val EXCHANGE_PROPERTY = "no.novari.keycloak.scim.http.exchange"
    val logger: Logger = Logger.getLogger("no.novari.keycloak.scim.http")
    private val scimPath = Regex("(?:^|/)realms/[^/]+/scim(?:/|$)")

    fun isScimPath(path: String): Boolean = scimPath.containsMatchIn(path)

    fun maxBodyBytes(): Int =
        (System.getProperty("scim.http.log.max-body-bytes") ?: System.getenv("SCIM_HTTP_LOG_MAX_BODY_BYTES"))
            ?.toIntOrNull()
            ?.takeIf { it in 0..16_777_216 }
            ?: 65_536
}

internal class HttpExchangeLog(
    val method: String,
    val uri: String,
    val maxBodyBytes: Int,
    val requestHasEntity: Boolean,
    val requestLength: Int,
    val requestMediaType: MediaType?,
    val requestEncoding: String?,
) {
    val id: String = UUID.randomUUID().toString()
    private val startedNanos = System.nanoTime()
    val requestBody = HttpBodyCapture(maxBodyBytes)
    var status: Int = 0
    var responseHasNoBody: Boolean = false

    fun elapsedMillis(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
}

/** Keeps a bounded copy only; streams continue to flow to the original readers and writers. */
internal class HttpBodyCapture(
    private val limit: Int,
) {
    private val bytes = ByteArrayOutputStream(minOf(limit, 4096))
    var totalBytes: Long = 0
        private set
    var endOfInput: Boolean = false
    private var incomplete = false

    fun record(value: Int) {
        if (value == -1) {
            endOfInput = true
        } else {
            if (bytes.size() < limit) bytes.write(value)
            totalBytes++
        }
    }

    fun record(
        source: ByteArray,
        offset: Int,
        length: Int,
    ) {
        val kept = minOf(length, limit - bytes.size())
        if (kept > 0) bytes.write(source, offset, kept)
        totalBytes += length
    }

    fun skipped(count: Long) {
        if (count > 0) {
            totalBytes += count
            incomplete = true
        }
    }

    fun reset() {
        incomplete = true
    }

    fun describe(
        mediaType: MediaType?,
        contentEncoding: String?,
        complete: Boolean,
    ): String =
        when {
            limit == 0 -> "<body logging disabled>"
            !contentEncoding.isNullOrBlank() && !contentEncoding.equals("identity", ignoreCase = true) -> "<encoded body omitted>"
            totalBytes == 0L && !complete -> "<body not consumed>"
            !complete || incomplete -> "<incomplete body omitted>"
            totalBytes == 0L -> "<empty>"
            totalBytes > limit -> "<body omitted: exceeds $limit bytes>"
            mediaType == null ||
                !(mediaType.subtype.equals("json", ignoreCase = true) || mediaType.subtype.endsWith("+json", ignoreCase = true)) ->
                "<non-JSON body omitted>"
            else -> HttpLogSanitizer.body(bytes.toByteArray(), mediaType)
        }
}

internal class CapturingInputStream(
    input: InputStream,
    private val capture: HttpBodyCapture,
) : FilterInputStream(input) {
    override fun read(): Int = `in`.read().also(capture::record)

    override fun read(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Int =
        `in`.read(bytes, offset, length).also { count ->
            if (count == -1) capture.endOfInput = true else capture.record(bytes, offset, count)
        }

    override fun skip(count: Long): Long = `in`.skip(count).also(capture::skipped)

    override fun reset() {
        `in`.reset()
        capture.reset()
    }
}

internal class CapturingOutputStream(
    output: OutputStream,
    private val capture: HttpBodyCapture,
) : FilterOutputStream(output) {
    override fun write(value: Int) {
        out.write(value)
        capture.record(value and 0xff)
    }

    override fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        out.write(bytes, offset, length)
        capture.record(bytes, offset, length)
    }
}
