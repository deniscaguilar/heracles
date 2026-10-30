package com.heracles.platform.adapter.out

import com.heracles.platform.application.port.out.Clock
import java.time.Instant

class SystemClock : Clock {
    override fun instant(): Instant = Instant.now()
}
