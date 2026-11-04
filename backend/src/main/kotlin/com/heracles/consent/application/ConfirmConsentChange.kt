package com.heracles.consent.application

import com.heracles.platform.api.Notifier
import com.heracles.platform.api.TaskMessage
import java.util.UUID

/**
 * Consumer of `consent.accepted` and `consent.revoked`: tells the person, in the background, that the change was recorded
 * (spec RF7). The message carries identifiers only; the notification carries the template and the consent type.
 * The consumer runs in the person's scope and records the message id with its effect, so a repeat sends nothing.
 */
class ConfirmConsentChange(private val notifier: Notifier) {
    fun handle(message: TaskMessage) {
        val userId = UUID.fromString(message.ids.getValue("userId"))
        val template = if (message.type == com.heracles.consent.api.ConsentMessages.ACCEPTED) "consent_accepted" else "consent_revoked"
        notifier.send(userId, template, mapOf("consentType" to message.ids.getValue("consentType")))
    }
}
