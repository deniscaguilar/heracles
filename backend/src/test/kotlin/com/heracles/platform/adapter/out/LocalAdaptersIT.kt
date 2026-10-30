package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.LlmClient
import com.heracles.platform.application.port.out.NotificationChannel
import com.heracles.platform.application.port.out.OcrClient
import com.heracles.platform.application.port.out.PaymentGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.test.context.SpringBootTest

/**
 * E1-S6.10 (CA17): the fakes stand in for the ports that have no real adapter yet, only when asked for. Without the
 * switch there is no PaymentGateway bean at all, so a production run that needs one fails at start instead of faking a charge.
 */
class LocalAdaptersIT {
    @Nested
    @SpringBootTest(properties = ["heracles.adapters.fake.enabled=true"])
    inner class WithFakes(
        @Autowired private val llm: ObjectProvider<LlmClient>,
        @Autowired private val ocr: ObjectProvider<OcrClient>,
        @Autowired private val payments: ObjectProvider<PaymentGateway>,
        @Autowired private val notifications: ObjectProvider<NotificationChannel>,
    ) {
        @Test
        fun `every port without a real adapter has a fake`() {
            assertThat(llm.getIfAvailable()).isNotNull()
            assertThat(ocr.getIfAvailable()).isNotNull()
            assertThat(payments.getIfAvailable()).isNotNull()
            assertThat(notifications.getIfAvailable()).isNotNull()
        }
    }

    @Nested
    @SpringBootTest(properties = ["heracles.adapters.fake.enabled=false"])
    inner class WithoutFakes(
        @Autowired private val llm: ObjectProvider<LlmClient>,
        @Autowired private val ocr: ObjectProvider<OcrClient>,
        @Autowired private val payments: ObjectProvider<PaymentGateway>,
        @Autowired private val notifications: ObjectProvider<NotificationChannel>,
    ) {
        @Test
        fun `nothing pretends to be a payment gateway, a model, an OCR or a channel`() {
            assertThat(llm.getIfAvailable()).isNull()
            assertThat(ocr.getIfAvailable()).isNull()
            assertThat(payments.getIfAvailable()).isNull()
            assertThat(notifications.getIfAvailable()).isNull()
        }
    }
}
