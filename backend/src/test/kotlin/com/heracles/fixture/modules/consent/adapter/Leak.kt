package com.heracles.fixture.modules.consent.adapter

import com.heracles.fixture.modules.platform.application.Internal

/** Violação proposital: o `consent` usa `platform.application` (a regra tem de reprovar). */
class Leak(val internal: Internal)
