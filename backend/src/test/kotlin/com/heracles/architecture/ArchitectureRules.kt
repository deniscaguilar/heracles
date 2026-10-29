package com.heracles.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture

/**
 * Regras de arquitetura do CLAUDE.md (seção 3), em funções para poderem rodar contra o código real
 * e contra classes de exemplo com violação proposital (prova de que a regra falha).
 */
object ArchitectureRules {

    /** domain <- application <- adapter; config liga tudo; api é a fachada do módulo. */
    fun layers(): ArchRule =
        layeredArchitecture().consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("domain").definedBy("..domain..")
            .layer("application").definedBy("..application..")
            .layer("adapter").definedBy("..adapter..")
            .layer("config").definedBy("..config..")
            .whereLayer("domain").mayOnlyBeAccessedByLayers("application", "adapter", "config")
            .whereLayer("application").mayOnlyBeAccessedByLayers("adapter", "config")
            .whereLayer("adapter").mayOnlyBeAccessedByLayers("config")
            .whereLayer("config").mayNotBeAccessedByAnyLayer()

    /** O domínio não importa Spring, JPA, Jackson nem AWS. */
    fun domainIsPure(): ArchRule =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "jakarta.persistence..", "javax.persistence..",
                "com.fasterxml.jackson..", "tools.jackson..", "software.amazon.awssdk..",
            )
            .allowEmptyShould(true)

    /** Não existe módulo `core`, `common` ou `shared`: código sem lugar vai para o módulo dono do conceito. */
    fun noGenericPackages(): ArchRule =
        noClasses().should().resideInAnyPackage("..core..", "..common..", "..shared..")
            .allowEmptyShould(true)

    /** Um módulo só usa outro módulo pela sua `api`. `base` é o pacote raiz dos módulos. */
    fun moduleBoundaries(base: String): ArchRule =
        classes().that().resideInAPackage("$base..")
            .should(object : ArchCondition<JavaClass>("access other modules only through their api package") {
                override fun check(item: JavaClass, events: ConditionEvents) {
                    val own = moduleOf(item.packageName, base) ?: return
                    item.directDependenciesFromSelf.forEach { dependency ->
                        val target = dependency.targetClass
                        val targetModule = moduleOf(target.packageName, base) ?: return@forEach
                        if (targetModule != own && !isApi(target.packageName, base, targetModule)) {
                            events.add(
                                SimpleConditionEvent.violated(
                                    item,
                                    "${item.name} depends on ${target.name}, which is not in $base.$targetModule.api",
                                ),
                            )
                        }
                    }
                }
            })
            .allowEmptyShould(true)

    private fun moduleOf(packageName: String, base: String): String? =
        if (packageName.startsWith("$base.")) packageName.removePrefix("$base.").substringBefore('.') else null

    private fun isApi(packageName: String, base: String, module: String): Boolean =
        packageName == "$base.$module.api" || packageName.startsWith("$base.$module.api.")

    /** Pacotes de exemplo com violação proposital ficam fora do Modulith e das regras do código real. */
    val fixtures: DescribedPredicate<JavaClass> =
        DescribedPredicate.describe("classes de exemplo de arquitetura") { it.packageName.startsWith("com.heracles.fixture") }
}
