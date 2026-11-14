package cn.muxicloud.mop.server.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "cn.muxicloud.mop", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Mapper").definedBy("..mapper..")
            .layer("Seed").definedBy("..seed..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Seed")
            .whereLayer("Mapper").mayOnlyBeAccessedByLayers("Service", "Seed");

    @ArchTest
    static final ArchRule modelDoesNotDependOnDownstream = noClasses()
            .that().resideInAPackage("cn.muxicloud.mop.model..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.muxicloud.mop.runtime..",
                    "cn.muxicloud.mop.semantic..",
                    "cn.muxicloud.mop.ai..");

    @ArchTest
    static final ArchRule semanticDoesNotDependOnAiOrRuntime = noClasses()
            .that().resideInAPackage("cn.muxicloud.mop.semantic..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.muxicloud.mop.runtime..",
                    "cn.muxicloud.mop.ai..");

    @ArchTest
    static final ArchRule runtimeDoesNotDependOnSemanticOrAi = noClasses()
            .that().resideInAPackage("cn.muxicloud.mop.runtime..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.muxicloud.mop.semantic..",
                    "cn.muxicloud.mop.ai..");

    @ArchTest
    static final ArchRule aiDoesNotDependOnRuntime = noClasses()
            .that().resideInAPackage("cn.muxicloud.mop.ai..")
            .should().dependOnClassesThat().resideInAPackage("cn.muxicloud.mop.runtime..");

    @ArchTest
    static final ArchRule systemInternals = internalOnly("system");

    @ArchTest
    static final ArchRule basicInternals = internalOnly("basic");

    @ArchTest
    static final ArchRule modelInternals = internalOnly("model");

    @ArchTest
    static final ArchRule semanticInternals = internalOnly("semantic");

    @ArchTest
    static final ArchRule aiInternals = internalOnly("ai");

    @ArchTest
    static final ArchRule runtimeInternals = internalOnly("runtime");

    private static ArchRule internalOnly(String module) {
        return classes()
                .that().resideInAPackage("cn.muxicloud.mop." + module + "..")
                .and().resideOutsideOfPackage("cn.muxicloud.mop." + module + ".api..")
                .should().onlyBeAccessed().byAnyPackage(
                        "cn.muxicloud.mop." + module + "..",
                        "cn.muxicloud.mop.server..")
                .allowEmptyShould(true)
                .as(module + " 模块内部实现只能被本模块或启动模块访问");
    }
}
