package com.heracles.platform.domain

/**
 * What travels through the queue: an id, a type and identifiers. Never health or personal data (CLAUDE.md, section 4):
 * values are limited to the shape of an identifier, so free text, e-mails, decimals and formatted numbers are refused.
 * The refusal message never repeats the offending value.
 */
data class QueueMessage(val id: String, val type: String, val ids: Map<String, String>) {
    init {
        require(IDENTIFIER.matches(id)) { "message id is not an identifier" }
        require(TYPE.matches(type)) { "message type is not a dotted lowercase name" }
        ids.forEach { (key, value) ->
            require(KEY.matches(key)) { "an identifier key is not a name" }
            require(IDENTIFIER.matches(value)) { "the value of '$key' is not an identifier" }
        }
    }

    private companion object {
        val IDENTIFIER = Regex("^[A-Za-z0-9_-]{1,128}$")
        val TYPE = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)*$")
        val KEY = Regex("^[A-Za-z][A-Za-z0-9_]{0,63}$")
    }
}

/** A delivery of a message. [receiveCount] is how many times the queue has delivered it, this one included. */
data class ReceivedMessage(val message: QueueMessage, val receipt: String, val receiveCount: Int = 1)
