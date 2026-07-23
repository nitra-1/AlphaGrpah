package com.alphagraph.bootstrap.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Encodes the dependency rules from docs/001_System_Architecture.md §4 as executable checks,
 * so a violation is caught at build time instead of at review time. Phase 0 ships only
 * package-info.java in every module, so these rules pass vacuously today — they start
 * doing real work the moment Phase 1 adds classes.
 */
class ModuleBoundaryArchTest {

    private static final String[] DOMAIN_MODULES = {
        "market", "financial", "ownership", "corporate", "sector", "technical", "risk"
    };

    private static final JavaClasses ALL_CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.alphagraph");

    @Test
    void commonHasNoDependencyOnAnyOtherModule() {
        noClasses().that().resideInAPackage("com.alphagraph.common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.alphagraph.reference..", "com.alphagraph.market..", "com.alphagraph.financial..",
                "com.alphagraph.ownership..", "com.alphagraph.corporate..", "com.alphagraph.sector..",
                "com.alphagraph.technical..", "com.alphagraph.risk..", "com.alphagraph.intelligence..",
                "com.alphagraph.decision..", "com.alphagraph.learning..", "com.alphagraph.api..",
                "com.alphagraph.scheduler.."
            )
            .check(ALL_CLASSES);
    }

    @Test
    void domainModulesDoNotDependOnEachOther() {
        for (String module : DOMAIN_MODULES) {
            noClasses().that().resideInAPackage("com.alphagraph." + module + "..")
                .should().dependOnClassesThat().resideInAnyPackage(otherDomains(module))
                .check(ALL_CLASSES);
        }
    }

    @Test
    void noModuleDependsOnTheApiModule() {
        noClasses().that().resideOutsideOfPackage("com.alphagraph.api..")
            .and().resideOutsideOfPackage("com.alphagraph.bootstrap..")
            .should().dependOnClassesThat().resideInAPackage("com.alphagraph.api..")
            .check(ALL_CLASSES);
    }

    @Test
    void schedulerIsAPureOrchestratorNothingDependsOnIt() {
        noClasses().that().resideOutsideOfPackage("com.alphagraph.scheduler..")
            .and().resideOutsideOfPackage("com.alphagraph.bootstrap..")
            .should().dependOnClassesThat().resideInAPackage("com.alphagraph.scheduler..")
            .check(ALL_CLASSES);
    }

    private static String[] otherDomains(String module) {
        return Arrays.stream(DOMAIN_MODULES)
            .filter(m -> !m.equals(module))
            .map(m -> "com.alphagraph." + m + "..")
            .toArray(String[]::new);
    }
}
