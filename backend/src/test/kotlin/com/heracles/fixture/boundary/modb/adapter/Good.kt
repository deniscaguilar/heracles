package com.heracles.fixture.boundary.modb.adapter

import com.heracles.fixture.boundary.moda.api.Facade

/** Correto: o módulo B usa o módulo A pela `api`. */
class Good(val facade: Facade)
