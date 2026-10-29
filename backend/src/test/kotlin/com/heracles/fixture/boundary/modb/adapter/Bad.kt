package com.heracles.fixture.boundary.modb.adapter

import com.heracles.fixture.boundary.moda.application.Internal

/** Violação proposital: o módulo B acessa o interior do módulo A. */
class Bad(val internal: Internal)
