package com.heracles.platform.config

import com.heracles.platform.adapter.out.fake.FakeLlmClient
import com.heracles.platform.adapter.out.fake.FakeOcrClient
import com.heracles.platform.adapter.out.fake.FakePaymentGateway
import com.heracles.platform.adapter.out.fake.RecordingNotificationChannel
import com.heracles.platform.application.port.out.LlmClient
import com.heracles.platform.application.port.out.NotificationChannel
import com.heracles.platform.application.port.out.OcrClient
import com.heracles.platform.application.port.out.PaymentGateway
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Fakes for the ports that have no real adapter in this stage: language model, OCR, payment and notification.
 * Off unless `heracles.adapters.fake.enabled=true` (local compose and tests). Without the switch the bean does not
 * exist, so a production run that needs one fails at start instead of faking a charge. A real adapter, when it
 * arrives, takes the place of the fake by being a bean of the same port.
 */
@Configuration
@ConditionalOnProperty("heracles.adapters.fake.enabled", havingValue = "true")
class LocalAdaptersConfig {
    @Bean
    @ConditionalOnMissingBean
    fun llmClient(): LlmClient = FakeLlmClient()

    @Bean
    @ConditionalOnMissingBean
    fun ocrClient(): OcrClient = FakeOcrClient()

    @Bean
    @ConditionalOnMissingBean
    fun paymentGateway(): PaymentGateway = FakePaymentGateway()

    @Bean
    @ConditionalOnMissingBean
    fun notificationChannel(): NotificationChannel = RecordingNotificationChannel()
}
