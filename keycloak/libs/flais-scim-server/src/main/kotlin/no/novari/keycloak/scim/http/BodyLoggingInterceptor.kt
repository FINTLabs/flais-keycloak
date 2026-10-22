package no.novari.keycloak.scim.http

import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.ext.Provider
import jakarta.ws.rs.ext.WriterInterceptor
import jakarta.ws.rs.ext.WriterInterceptorContext
import org.jboss.logging.Logger

/** Captures the bytes produced by the real message body writer without buffering the response. */
@Provider
@Priority(Priorities.ENTITY_CODER + 100)
class BodyLoggingInterceptor internal constructor(
    private val logger: Logger,
) : WriterInterceptor {
    constructor() : this(HttpLogging.logger)

    override fun aroundWriteTo(context: WriterInterceptorContext) {
        val exchange = context.getProperty(HttpLogging.EXCHANGE_PROPERTY) as? HttpExchangeLog
        if (exchange == null || exchange.responseHasNoBody) {
            context.proceed()
            return
        }

        val original = context.outputStream
        val capture = HttpBodyCapture(exchange.maxBodyBytes)
        context.outputStream = CapturingOutputStream(original, capture)
        var completed = false
        try {
            context.proceed()
            completed = true
        } catch (exception: Exception) {
            logger.debugf(
                "SCIM HTTP RESPONSE WRITE FAILED id=%s status=%d error=%s elapsedMs=%d",
                exchange.id,
                exchange.status,
                exception.javaClass.name,
                exchange.elapsedMillis(),
            )
            throw exception
        } finally {
            context.outputStream = original
            logger.debugf(
                "SCIM HTTP RESPONSE BODY id=%s status=%d bytesWritten=%d completed=%s elapsedMs=%d body=%s",
                exchange.id,
                exchange.status,
                capture.totalBytes,
                completed,
                exchange.elapsedMillis(),
                capture.describe(context.mediaType, context.headers.getFirst("Content-Encoding")?.toString(), completed),
            )
        }
    }
}
