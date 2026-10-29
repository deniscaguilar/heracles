package com.heracles.fixture.layers.domain

import com.heracles.fixture.layers.application.UseCase

/** Violação proposital: o domínio depende da camada application. */
class BadDomain(val useCase: UseCase)
