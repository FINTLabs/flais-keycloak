package no.fintlabs.keycloak.scim.logging

import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.ext.Provider
import jakarta.ws.rs.ext.ReaderInterceptor
import jakarta.ws.rs.ext.ReaderInterceptorContext
import org.jboss.logging.Logger
import java.io.ByteArrayInputStream
import java.io.IOException

@Provider
class BodyLoggingInterceptor : ReaderInterceptor {

    companion object {
        private val logger: Logger = Logger.getLogger(BodyLoggingInterceptor::class.java.name)
    }

    @Throws(IOException::class, WebApplicationException::class)
    override fun aroundReadFrom(context: ReaderInterceptorContext): Any {
        val entityBytes = context.inputStream.readAllBytes()

        logger.info(String(entityBytes))

        context.inputStream = ByteArrayInputStream(entityBytes)

        return context.proceed()
    }
}
