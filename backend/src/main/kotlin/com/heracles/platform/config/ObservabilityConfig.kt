package com.heracles.platform.config

import io.micrometer.common.KeyValue
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationFilter
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.observation.ServerRequestObservationContext

/**
 * What a span may carry (CLAUDE.md, section 4; plan section 8): route as a template, method, status and the user id.
 * Anything else (URLs with query strings, headers, exception messages) is dropped before it leaves the process.
 */
object SpanAttributeAllowlist {
    const val USER_ID = "user_id"
    const val USER_ID_REQUEST_ATTRIBUTE = "heracles.userId"

    val KEYS: Set<String> = setOf(
        "method", "status", "uri", "outcome", "exception", // Spring HTTP server observation
        "http.request.method", "http.response.status_code", "http.route", "error.type", // OpenTelemetry names
        USER_ID,
    )
}

/** Wraps every exporter so only allowlisted attributes are sent, with no events and no status text. */
class AllowlistSpanExporter(private val delegate: SpanExporter) : SpanExporter {
    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode = delegate.export(spans.map(::AllowlistedSpan))
    override fun flush(): CompletableResultCode = delegate.flush()
    override fun shutdown(): CompletableResultCode = delegate.shutdown()
}

private class AllowlistedSpan(private val span: SpanData) : SpanData by span {
    private val allowed: Attributes = Attributes.builder().also { builder ->
        span.attributes.forEach { key, value ->
            @Suppress("UNCHECKED_CAST")
            if (key.key in SpanAttributeAllowlist.KEYS) builder.put(key as AttributeKey<Any>, value)
        }
    }.build()

    override fun getAttributes(): Attributes = allowed
    override fun getTotalAttributeCount(): Int = allowed.size()
    override fun getEvents(): List<EventData> = emptyList()
    override fun getTotalRecordedEvents(): Int = 0
    override fun getStatus(): StatusData = StatusData.create(span.status.statusCode, "")
}

@Configuration
class ObservabilityConfig {

    companion object {
        /** Every SpanExporter bean (the OTLP one, and the test one) goes out through the allowlist. Static, so it is created early. */
        @Bean
        @JvmStatic
        fun allowlistSpanExporters(): BeanPostProcessor = object : BeanPostProcessor {
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any =
                if (bean is SpanExporter && bean !is AllowlistSpanExporter) AllowlistSpanExporter(bean) else bean
        }
    }

    /** Adds the platform user id (a UUID) to the HTTP server span, when the caller was authenticated. */
    @Bean
    fun userIdObservationFilter(): ObservationFilter = ObservationFilter { context: Observation.Context ->
        if (context is ServerRequestObservationContext) {
            (context.carrier?.getAttribute(SpanAttributeAllowlist.USER_ID_REQUEST_ATTRIBUTE) as? String)?.let {
                context.addHighCardinalityKeyValue(KeyValue.of(SpanAttributeAllowlist.USER_ID, it))
            }
        }
        context
    }
}
