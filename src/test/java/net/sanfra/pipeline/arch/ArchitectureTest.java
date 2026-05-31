package net.sanfra.pipeline.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class ArchitectureTest {

    private static final JavaClasses ALL_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("net.sanfra.pipeline");

    @Test
    void layering_is_respected() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy("net.sanfra.pipeline.controller..")
                .layer("Service").definedBy("net.sanfra.pipeline.service..")
                .layer("Repository").definedBy("net.sanfra.pipeline.repository..")
                .layer("Domain").definedBy("net.sanfra.pipeline.domain..")
                .whereLayer("Controller").mayOnlyAccessLayers("Service", "Domain")
                .whereLayer("Service").mayOnlyAccessLayers("Repository", "Domain")
                .whereLayer("Repository").mayOnlyAccessLayers("Domain")
                .check(ALL_CLASSES);
    }

    @Test
    void controllers_do_not_access_repositories_directly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("net.sanfra.pipeline.controller..")
                .should().accessClassesThat().resideInAPackage("net.sanfra.pipeline.repository..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void service_classes_are_annotated_with_Service() {
        ArchRule rule = classes()
                .that().resideInAPackage("net.sanfra.pipeline.service..")
                .and().areNotInterfaces()
                .should().beAnnotatedWith(Service.class);
        rule.check(ALL_CLASSES);
    }

    @Test
    void repository_types_are_annotated_with_Repository() {
        ArchRule rule = classes()
                .that().resideInAPackage("net.sanfra.pipeline.repository..")
                .should().beAnnotatedWith(Repository.class);
        rule.check(ALL_CLASSES);
    }
}
