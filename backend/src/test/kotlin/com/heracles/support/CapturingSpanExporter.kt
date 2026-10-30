package com.heracles.support

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/** Test-only exporter: keeps what would leave the process, so a test can inspect it. No network. */
@Component
class CapturedSpans {
    val spans = CopyOnWriteArrayList<SpanData>()
}

/** The production code wraps every exporter bean with the allowlist, so tests read the spans from [CapturedSpans]. */
@Component
class CapturingSpanExporter(private val captured: CapturedSpans) : SpanExporter {
    override fun export(batch: MutableCollection<SpanData>): CompletableResultCode {
        captured.spans.addAll(batch)
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
