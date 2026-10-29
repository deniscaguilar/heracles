package com.heracles.architecture

import com.heracles.HeraclesApplication
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/** `Modulith.verify()` (CLAUDE.md, seção 3): ciclos e acesso indevido entre módulos. */
class ModulithStructureTest {
    @Test
    fun `application modules are valid`() {
        ApplicationModules.of(HeraclesApplication::class.java, ArchitectureRules.fixtures).verify()
    }
}
