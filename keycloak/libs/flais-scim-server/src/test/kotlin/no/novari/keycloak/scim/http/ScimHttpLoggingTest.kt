package no.novari.keycloak.scim.http

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.WriterInterceptorContext
import org.jboss.logging.Logger
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.net.URI

class ScimHttpLoggingTest {
    @Test
    fun `GET without an entity logs incoming request and serialized response with one id`() {
        val logger = RecordingLogger()
        val request = RequestFixture()
        val responseBody = """{"totalResults":0,"Resources":[]}""".toByteArray()
        val filter = ScimHttpLoggingFilter(logger)

        filter.filter(request.context)
        assertTrue(logger.lines.single().contains("SCIM HTTP REQUEST"))
        assertTrue(logger.lines.single().contains("method=\"GET\""))
        filter.filter(request.context, response(200))
        val writer = WriterFixture(request.properties) { it.write(responseBody) }
        BodyLoggingInterceptor(logger).aroundWriteTo(writer.context)

        assertArrayEquals(responseBody, writer.destination.toByteArray())
        assertEquals(3, logger.lines.size)
        assertTrue(logger.lines[1].contains("status=200"))
        assertTrue(logger.lines[2].contains(String(responseBody)))
        val id = (request.properties[HttpLogging.EXCHANGE_PROPERTY] as HttpExchangeLog).id
        assertTrue(logger.lines.all { it.contains("id=$id") })
    }

    @Test
    fun `POST captures request body without pre-reading it and leaves credentials intact for application`() {
        val body = """{"userName":"alice","password":"request-secret"}""".toByteArray()
        val logger = RecordingLogger()
        val request = RequestFixture(method = "POST", body = body)
        val filter = ScimHttpLoggingFilter(logger)
        request.headers.add("Authorization", "Bearer bearer-secret")

        filter.filter(request.context)
        assertEquals(body.size, request.original.available())
        assertArrayEquals(body, request.context.entityStream.readAllBytes())
        filter.filter(request.context, response(201))

        val logs = logger.lines.joinToString("\n")
        assertTrue(logs.contains("SCIM HTTP REQUEST BODY"))
        assertTrue(logs.contains("alice"))
        assertTrue(logs.contains("status=201"))
        assertFalse(logs.contains("request-secret"))
        assertFalse(logs.contains("bearer-secret"))
    }

    @Test
    fun `auth failures log unread request and error response`() {
        val logger = RecordingLogger()
        val request = RequestFixture(method = "PATCH", body = """{"active":true}""".toByteArray())
        val filter = ScimHttpLoggingFilter(logger)
        filter.filter(request.context)
        filter.filter(request.context, response(401))
        val writer = WriterFixture(request.properties) { it.write("""{"detail":"Invalid token"}""".toByteArray()) }
        BodyLoggingInterceptor(logger).aroundWriteTo(writer.context)

        assertTrue(logger.lines.any { it.contains("<body not consumed>") })
        assertTrue(logger.lines.any { it.contains("status=401") && it.contains("Invalid token") })
        assertEquals(request.original.available(), request.length)
    }

    @Test
    fun `unmatched SCIM routes and empty responses do not require a writer`() {
        for (status in listOf(204, 304, 404, 500)) {
            val logger = RecordingLogger()
            val request = RequestFixture(path = "realms/test/scim/v2/org/Unknown")
            val filter = ScimHttpLoggingFilter(logger)
            filter.filter(request.context)
            filter.filter(request.context, response(status, hasEntity = false))

            assertEquals(2, logger.lines.size)
            assertTrue(logger.lines.last().contains("status=$status"))
            assertTrue(logger.lines.last().contains("body=<none>"))
        }
    }

    @Test
    fun `HEAD suppresses entity logging even if response contains an entity`() {
        val logger = RecordingLogger()
        val request = RequestFixture(method = "HEAD")
        val filter = ScimHttpLoggingFilter(logger)
        filter.filter(request.context)
        filter.filter(request.context, response(200))

        assertTrue(logger.lines.last().contains("body=<none>"))
        assertTrue((request.properties[HttpLogging.EXCHANGE_PROPERTY] as HttpExchangeLog).responseHasNoBody)
    }

    @Test
    fun `disabled debug and non-SCIM requests pass through without wrapping or logging`() {
        for ((enabled, path) in listOf(false to "realms/test/scim/v2/org/Users", true to "realms/test/protocol/openid-connect/token")) {
            val logger = RecordingLogger(enabled)
            val request = RequestFixture(path = path, body = "{}".toByteArray())
            val filter = ScimHttpLoggingFilter(logger)
            filter.filter(request.context)
            filter.filter(request.context, response(200))
            val writer = WriterFixture(request.properties) { it.write("{}".toByteArray()) }
            BodyLoggingInterceptor(logger).aroundWriteTo(writer.context)

            assertSame(request.original, request.context.entityStream)
            assertTrue(request.properties.isEmpty())
            assertTrue(logger.lines.isEmpty())
            assertEquals("{}", writer.destination.toString())
        }
    }

    @Test
    fun `write failure is logged rethrown and original output stream is restored`() {
        val logger = RecordingLogger()
        val request = RequestFixture()
        val filter = ScimHttpLoggingFilter(logger)
        filter.filter(request.context)
        filter.filter(request.context, response(200))
        val failure = IOException("exception-secret")
        val writer =
            WriterFixture(request.properties) {
                it.write("""{"password":"partial-secret"""".toByteArray())
                throw failure
            }

        assertSame(failure, assertThrows<IOException> { BodyLoggingInterceptor(logger).aroundWriteTo(writer.context) })
        assertSame(writer.destination, writer.context.outputStream)
        val logs = logger.lines.joinToString("\n")
        assertTrue(logs.contains("SCIM HTTP RESPONSE WRITE FAILED"))
        assertTrue(logs.contains("completed=false"))
        assertTrue(logs.contains("<incomplete body omitted>"))
        assertFalse(logs.contains("exception-secret"))
        assertFalse(logs.contains("partial-secret"))
    }

    @Test
    fun `SCIM path scope respects context prefixes and segment boundaries`() {
        listOf("realms/test/scim", "/auth/realms/test/scim/v2/org/Users", "realms/test/scim/").forEach {
            assertTrue(HttpLogging.isScimPath(it), it)
        }
        listOf("realms/test/scim-other", "realms/test/protocol/scim", "realms/scim/account").forEach {
            assertFalse(HttpLogging.isScimPath(it), it)
        }
    }

    @Test
    fun `every HTTP method reaches request and response logging`() {
        for (method in listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")) {
            val logger = RecordingLogger()
            val request = RequestFixture(method = method)
            val filter = ScimHttpLoggingFilter(logger)
            filter.filter(request.context)
            filter.filter(request.context, response(204, hasEntity = false))

            assertEquals(2, logger.lines.size)
            assertTrue(logger.lines.all { it.contains("method=\"$method\"") })
        }
    }

    private class RecordingLogger(
        private val enabled: Boolean = true,
    ) : Logger("scim-http-test") {
        val lines = mutableListOf<String>()

        override fun isEnabled(level: Level): Boolean = enabled

        override fun doLog(
            level: Level,
            loggerClassName: String?,
            message: Any?,
            parameters: Array<out Any?>?,
            thrown: Throwable?,
        ) {
            if (enabled) lines.add(message.toString())
        }

        override fun doLogf(
            level: Level,
            loggerClassName: String?,
            format: String,
            parameters: Array<out Any?>?,
            thrown: Throwable?,
        ) {
            if (enabled) lines.add(String.format(format, *(parameters ?: emptyArray())))
        }
    }

    private class RequestFixture(
        method: String = "GET",
        path: String = "realms/test/scim/v2/org/Users",
        body: ByteArray = byteArrayOf(),
    ) {
        val properties = mutableMapOf<String, Any>()
        val headers = MultivaluedHashMap<String, String>()
        val original = ByteArrayInputStream(body)
        val length = body.size
        private var stream: InputStream = original
        private val uri =
            stub<UriInfo> { name, _ ->
                when (name) {
                    "getPath" -> path
                    "getRequestUri" -> URI.create("https://example.test/$path?count=5")
                    else -> error("Unexpected UriInfo method $name")
                }
            }
        val context =
            stub<ContainerRequestContext> { name, args ->
                when (name) {
                    "getMethod" -> method
                    "getUriInfo" -> uri
                    "hasEntity" -> body.isNotEmpty()
                    "getLength" -> body.size
                    "getMediaType" -> MediaType("application", "scim+json")
                    "getHeaders" -> headers
                    "getHeaderString" -> headers.getFirst(args[0] as String)
                    "getEntityStream" -> stream
                    "setEntityStream" -> null.also { stream = args[0] as InputStream }
                    "getProperty" -> properties[args[0]]
                    "setProperty" -> null.also { properties[args[0] as String] = args[1]!! }
                    else -> error("Unexpected request method $name")
                }
            }
    }

    private class WriterFixture(
        properties: Map<String, Any>,
        write: (OutputStream) -> Unit,
    ) {
        val destination = ByteArrayOutputStream()
        private var output: OutputStream = destination
        val context =
            stub<WriterInterceptorContext> { name, args ->
                when (name) {
                    "getProperty" -> properties[args[0]]
                    "getOutputStream" -> output
                    "setOutputStream" -> null.also { output = args[0] as OutputStream }
                    "getMediaType" -> MediaType("application", "scim+json")
                    "getHeaders" -> MultivaluedHashMap<String, Any>()
                    "proceed" -> null.also { write(output) }
                    else -> error("Unexpected writer method $name")
                }
            }
    }

    companion object {
        private fun response(
            status: Int,
            hasEntity: Boolean = true,
        ): ContainerResponseContext =
            stub { name, _ ->
                when (name) {
                    "getStatus" -> status
                    "getStatusInfo" -> Response.Status.fromStatusCode(status)
                    "hasEntity" -> hasEntity
                    "getHeaders" -> MultivaluedHashMap<String, Any>()
                    else -> error("Unexpected response method $name")
                }
            }

        private inline fun <reified T> stub(crossinline call: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                call(method.name, args ?: emptyArray())
            } as T
    }
}
