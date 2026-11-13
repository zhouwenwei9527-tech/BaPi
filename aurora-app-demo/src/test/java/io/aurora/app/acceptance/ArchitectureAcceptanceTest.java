package io.aurora.app.acceptance;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.aurora.tool.ToolExecutor;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.aurora")
class ArchitectureAcceptanceTest {

    @ArchTest
    static final ArchRule rbacCoreHasNoAiDependencies = noClasses()
            .that().resideInAPackage("io.aurora.rbac..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.ai..", "io.aurora.tool..", "io.aurora.agent..",
                    "io.aurora.retrieval..");

    @ArchTest
    static final ArchRule agentRuntimeHasNoWebDependencies = noClasses()
            .that().resideInAPackage("io.aurora.agent..")
            .should().dependOnClassesThat().resideInAnyPackage("..web..", "..controller..");

    @ArchTest
    static final ArchRule retrievalDoesNotDependOnAgentRuntime = noClasses()
            .that().resideInAPackage("io.aurora.retrieval..")
            .should().dependOnClassesThat().resideInAPackage("io.aurora.agent..");

    @ArchTest
    static final ArchRule onlyGatewayCallsToolExecutor = noClasses()
            .that().resideOutsideOfPackage("io.aurora.agent.gateway..")
            .should().callMethodWhere(target(owner(assignableTo(ToolExecutor.class))).and(name("execute")));
}
