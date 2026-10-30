package com.heracles.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** Regras do CLAUDE.md (seção 3) contra o código de produção. */
class ArchitectureTest {
    private val production = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.heracles")

    @Test
    fun `layers respect domain, application, adapter and config`() {
        ArchitectureRules.layers().check(production)
    }

    @Test
    fun `domain does not import Spring, JPA, Jackson or AWS`() {
        ArchitectureRules.domainIsPure().check(production)
    }

    @Test
    fun `application depends only on ports, not on Spring, AWS, JDBC or Jackson`() {
        ArchitectureRules.applicationDependsOnlyOnPorts().check(production)
    }

    @Test
    fun `there is no core, common or shared package`() {
        ArchitectureRules.noGenericPackages().check(production)
    }

    @Test
    fun `modules only use other modules through api`() {
        ArchitectureRules.moduleBoundaries("com.heracles").check(production)
    }
}

/** Prova de que cada regra falha quando há violação: roda contra classes de exemplo. */
class ArchitectureRulesFailOnViolationTest {
    private fun fixture(subPackage: String) =
        ClassFileImporter().importPackages("com.heracles.fixture.$subPackage")

    @Test
    fun `layers rule rejects domain depending on application`() {
        assertThatThrownBy { ArchitectureRules.layers().check(fixture("layers")) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("BadDomain")
    }

    @Test
    fun `domain purity rule rejects Spring in domain`() {
        assertThatThrownBy { ArchitectureRules.domainIsPure().check(fixture("pure")) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("SpringInDomain")
    }

    @Test
    fun `application rule rejects Spring in application`() {
        assertThatThrownBy { ArchitectureRules.applicationDependsOnlyOnPorts().check(fixture("appdeps")) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("SpringInApplication")
    }

    @Test
    fun `generic package rule rejects shared`() {
        assertThatThrownBy { ArchitectureRules.noGenericPackages().check(fixture("generic")) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("Util")
    }

    @Test
    fun `module boundary rule rejects access to another module internals but accepts api`() {
        val rule = ArchitectureRules.moduleBoundaries("com.heracles.fixture.boundary")
        assertThatThrownBy { rule.check(fixture("boundary")) }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("Bad").hasMessageNotContaining("Good")
        assertThat(rule.evaluate(fixture("boundary")).failureReport.details.joinToString()).doesNotContain("Good")
    }
}
