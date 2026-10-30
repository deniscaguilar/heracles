package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.TaskQueue
import com.heracles.platform.domain.QueueMessage
import com.heracles.platform.domain.ReceivedMessage
import software.amazon.awssdk.services.sqs.SqsClient
import tools.jackson.databind.ObjectMapper

class SqsTaskQueue(private val sqs: SqsClient, val queueUrl: String) : TaskQueue {
    private val json = ObjectMapper()

    override fun send(message: QueueMessage) {
        val body = json.writeValueAsString(mapOf("id" to message.id, "type" to message.type, "ids" to message.ids))
        sqs.sendMessage { it.queueUrl(queueUrl).messageBody(body) }
    }

    override fun receive(maxMessages: Int, waitSeconds: Int): List<ReceivedMessage> =
        sqs.receiveMessage {
            it.queueUrl(queueUrl).maxNumberOfMessages(maxMessages.coerceIn(1, 10)).waitTimeSeconds(waitSeconds.coerceIn(0, 20))
        }.messages().map { received ->
            val body = json.readTree(received.body())
            val ids = body.get("ids").propertyNames().associateWith { key -> body.get("ids").get(key).asString() }
            ReceivedMessage(QueueMessage(body.get("id").asString(), body.get("type").asString(), ids), received.receiptHandle())
        }

    override fun acknowledge(receipt: String) {
        sqs.deleteMessage { it.queueUrl(queueUrl).receiptHandle(receipt) }
    }
}
