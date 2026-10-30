package com.heracles.fixture.appdeps.application

import org.springframework.stereotype.Component

/** Violação proposital: a camada application usando Spring em vez de depender só de ports. */
@Component
class SpringInApplication
