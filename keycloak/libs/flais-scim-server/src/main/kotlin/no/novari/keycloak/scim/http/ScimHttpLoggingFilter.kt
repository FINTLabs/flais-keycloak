package no.novari.keycloak.scim.http

import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.container.PreMatching
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.Logger

/** Unbound so authentication failures, unmatched routes and empty responses are logged too. */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
class ScimHttpLoggingFilter internal constructor(
    private val logger: Logger,
) : ContainerRequestFilter,
    ContainerResponseFilter {
    constructor() : this(HttpLogging.logger)

    override fun filter(request: ContainerRequestContext) {
        if (!logger.isDebugEnabled || !HttpLogging.isScimPath(request.uriInfo.requestUri.path)) {
            return
        }

        val exchange =
            HttpExchangeLog(
                method = request.method,
                uri = HttpLogSanitizer.uri(request.uriInfo.requestUri),
                maxBodyBytes = HttpLogging.maxBodyBytes(),
                requestHasEntity = request.hasEntity(),
                requestLength = runCatching { request.length }.getOrDefault(-1),
                requestMediaType = runCatching { request.mediaType }.getOrNull(),
                requestEncoding = request.getHeaderString("Content-Encoding"),
            )
        request.setProperty(HttpLogging.EXCHANGE_PROPERTY, exchange)
        logger.debugf(
            "SCIM HTTP REQUEST id=%s method=%s uri=%s headers=%s body=%s",
            exchange.id,
            HttpLogSanitizer.quote(exchange.method),
            HttpLogSanitizer.quote(exchange.uri),
            HttpLogSanitizer.headers(request.headers),
            if (exchange.requestHasEntity) "<pending>" else "<none>",
        )

        if (exchange.requestHasEntity) {
            // Pre-matching filters may run on the event loop. Never read or drain the stream here.
            request.entityStream = CapturingInputStream(request.entityStream, exchange.requestBody)
        }
    }

    override fun filter(
        request: ContainerRequestContext,
        response: ContainerResponseContext,
    ) {
        val exchange = request.getProperty(HttpLogging.EXCHANGE_PROPERTY) as? HttpExchangeLog ?: return
        if (exchange.requestHasEntity) {
            logger.debugf(
                "SCIM HTTP REQUEST BODY id=%s bytesRead=%d body=%s",
                exchange.id,
                exchange.requestBody.totalBytes,
                exchange.requestBody.describe(
                    exchange.requestMediaType,
                    exchange.requestEncoding,
                    exchange.requestBody.endOfInput ||
                        (exchange.requestLength >= 0 && exchange.requestBody.totalBytes == exchange.requestLength.toLong()),
                ),
            )
        }

        exchange.status = response.status
        exchange.responseHasNoBody =
            !response.hasEntity() ||
            request.method == "HEAD" ||
            response.status in 100..199 ||
            response.status == 204 ||
            response.status == 205 ||
            response.status == 304
        logger.debugf(
            "SCIM HTTP RESPONSE id=%s method=%s uri=%s status=%d reason=%s elapsedMs=%d headers=%s body=%s",
            exchange.id,
            HttpLogSanitizer.quote(exchange.method),
            HttpLogSanitizer.quote(exchange.uri),
            response.status,
            HttpLogSanitizer.quote(response.statusInfo.reasonPhrase),
            exchange.elapsedMillis(),
            HttpLogSanitizer.headers(response.headers),
            if (exchange.responseHasNoBody) "<none>" else "<pending>",
        )
    }
}
