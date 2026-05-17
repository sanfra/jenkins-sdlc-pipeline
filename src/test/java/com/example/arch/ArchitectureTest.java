package com.example.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class ArchitectureTest {

    private static final JavaClasses ALL_CLASSES =
            new ClassFileImporter().importPackages("com.example");

    @Test
    void layering_is_respected() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy("com.example.controller..")
                .layer("Service").definedBy("com.example.service..")
                .layer("Repository").definedBy("com.example.repository..")
                .layer("Domain").definedBy("com.example.domain..")
                .whereLayer("Controller").mayOnlyAccessLayers("Service", "Domain")
                .whereLayer("Service").mayOnlyAccessLayers("Repository", "Domain")
                .whereLayer("Repository").mayOnlyAccessLayers("Domain")
                .check(ALL_CLASSES);
    }

    @Test
    void controllers_do_not_access_repositories_directly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.example.controller..")
                .should().accessClassesThat().resideInAPackage("com.example.repository..");
        rule.check(ALL_CLASSES);
    }

    @Test
    void service_classes_are_annotated_with_Service() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.example.service..")
                .and().areNotInterfaces()
                .should().beAnnotatedWith(Service.class);
        rule.check(ALL_CLASSES);
    }

    @Test
    void repository_types_are_annotated_with_Repository() {
        ArchRule rule = classes()
                .that().resideInAPackage("com.example.repository..")
                .should().beAnnotatedWith(Repository.class);
        rule.check(ALL_CLASSES);
    }
}
