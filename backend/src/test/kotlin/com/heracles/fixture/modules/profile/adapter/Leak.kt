package com.heracles.fixture.modules.profile.adapter

import com.heracles.fixture.modules.platform.application.Internal

/** Violação proposital: o `profile` usa `platform.application` (a regra tem de reprovar). */
class Leak(val internal: Internal)
