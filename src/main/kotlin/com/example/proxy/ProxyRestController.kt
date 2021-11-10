package com.example.proxy

import org.apache.hc.client5.http.classic.methods.*
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ClassicHttpRequest
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.io.entity.BasicHttpEntity
import org.apache.hc.core5.http.message.BasicNameValuePair
import org.apache.hc.core5.net.URIBuilder
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import java.io.ByteArrayInputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
@CrossOrigin
class ProxyRestController {

    private val httpClient = HttpClients.custom().disableRedirectHandling().build()
    private val httpHost = HttpHost(
        System.getenv("PROXIED_SCHEME") ?: "http",
        System.getenv("PROXIED_HOST") ?: "localhost",
        (System.getenv("PROXIED_PORT") ?: "9200").toInt()
    )
    private val log = LoggerFactory.getLogger(ProxyRestController::class.java)

    init {
        Runtime.getRuntime().addShutdownHook(Thread { httpClient.close() })
    }

    @RequestMapping(
        method = [
            RequestMethod.GET,
            RequestMethod.HEAD,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.PATCH,
            RequestMethod.DELETE,
            RequestMethod.OPTIONS,
            RequestMethod.TRACE,
        ],
        path = ["/**"],
    )
    fun proxyAll(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {
        val downstreamRequest = when (servletRequest.method.uppercase()) {
            "GET" ->
                HttpGet(servletRequest.requestURI).apply {
                    copyUri(servletRequest)
                    copyRequestHeaders(servletRequest)
                    copyRequestBody(servletRequest)
                }
            "POST" ->
                HttpPost(servletRequest.requestURI).apply {
                    copyUri(servletRequest)
                    copyRequestHeaders(servletRequest)
                    copyRequestBody(servletRequest)
                }
            "PATCH" ->
                HttpPatch(servletRequest.requestURI).apply {
                    copyUri(servletRequest)
                    copyRequestHeaders(servletRequest)
                    copyRequestBody(servletRequest)
                }
            "DELETE" ->
                HttpDelete(servletRequest.requestURI).apply {
                    copyUri(servletRequest)
                    copyRequestHeaders(servletRequest)
                    copyRequestBody(servletRequest)
                }
            "PUT" ->
                HttpPut(servletRequest.requestURI).apply {
                    copyUri(servletRequest)
                    copyRequestHeaders(servletRequest)
                    copyRequestBody(servletRequest)
                }
            else
            -> error("...")
        }

        val downstreamResponse = httpClient.execute(httpHost, downstreamRequest)
        servletResponse.status = downstreamResponse.code
        copyResponseHeaders(downstreamResponse, servletResponse)
        log.info("${downstreamRequest.method} ${downstreamRequest.uri} returned ${servletResponse.status}")
        servletResponse.outputStream.write(downstreamResponse.entity.content.readAllBytes())
        downstreamRequest.reset()
    }

    private fun ClassicHttpRequest.copyUri(servletRequest: HttpServletRequest) {
        val parameters = servletRequest.parameterMap.flatMap { (paramName, paramValues) ->
            paramValues.map { paramValue ->
                BasicNameValuePair(paramName, paramValue)
            }
        }
        this.uri = URIBuilder(servletRequest.requestURI)
            .addParameters(parameters)
            .build()
    }

    private fun copyResponseHeaders(
        downstreamResponse: CloseableHttpResponse,
        servletResponse: HttpServletResponse
    ) {
        for (header in downstreamResponse.headers) {
            val headerName = header.name.lowercase()
            if (headerName != "content-length"
                && headerName != "transfer-encoding"
                && headerName != "content-security-policy"
            ) {
                log.info("${header.name}: ${header.value}")
                servletResponse.addHeader(header.name, header.value)
            }
        }
    }

    private fun ClassicHttpRequest.copyRequestHeaders(servletRequest: HttpServletRequest) {
        for (headerName in servletRequest.headerNames) {
            if (headerName.lowercase() != "content-length") {
                val headerValues = servletRequest.getHeaders(headerName)
                for (headerValue in headerValues) {
                    addHeader(headerName, headerValue)
                }
            }
        }
    }

    private fun ClassicHttpRequest.copyRequestBody(servletRequest: HttpServletRequest) {
        val allBytes = servletRequest.inputStream.readAllBytes()
        val inputStream = ByteArrayInputStream(allBytes)
        if (allBytes.isNotEmpty()) {
            entity = BasicHttpEntity(inputStream, ContentType.create(servletRequest.contentType))
        }
    }
}