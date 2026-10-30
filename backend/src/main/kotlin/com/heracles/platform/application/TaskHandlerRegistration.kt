package com.heracles.platform.application

import com.heracles.platform.application.port.out.TaskHandler

/** A module registers the handler of its message type as a bean of this type; the consumer picks them all up. */
class TaskHandlerRegistration(val type: String, val handler: TaskHandler)
