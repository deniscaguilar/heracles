package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.QueueDepths
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.QueueAttributeName

/** Waiting plus being handled (in flight). In AWS the oldest-message age is also a CloudWatch metric, alarmed by OpenTofu (S8.2). */
class SqsQueueDepths(private val sqs: SqsClient, private val workUrl: String, private val deadLetterUrl: String) : QueueDepths {
    override fun workQueue(): Int = depth(workUrl)
    override fun deadLetterQueue(): Int = depth(deadLetterUrl)

    private fun depth(url: String): Int {
        val attributes = sqs.getQueueAttributes {
            it.queueUrl(url).attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
        }.attributes()
        return (attributes[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES]?.toIntOrNull() ?: 0) +
            (attributes[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE]?.toIntOrNull() ?: 0)
    }
}
