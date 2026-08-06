dependencies {
    api(project(":common"))
    api(project(":reference"))
    implementation(project(":intelligence"))
    // Module 3.1: DecisionScoringOrchestrator reads all six domain scores directly. "decision" is
    // not in ModuleBoundaryArchTest's DOMAIN_MODULES list, so - like intelligence - it's allowed
    // to depend on every domain module directly, rather than needing its own separate bridge.
    implementation(project(":technical"))
    implementation(project(":financial"))
    implementation(project(":ownership"))
    implementation(project(":sector"))
    implementation(project(":risk"))
    implementation(project(":corporate"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-jdbc")
    implementation("org.slf4j:slf4j-api")
    // Module 3.6: decision.report.DailyReportClient compiles against the Anthropic SDK's own
    // types directly, the same reason intelligence.analyst.AiAnalystClient needed this - corporate's
    // own dependency on this artifact is `implementation`-scoped, not transitive, so it must be
    // declared here too. The actual AnthropicClient bean is still produced once, by
    // corporate.knowledge.AnthropicClientConfig - this only adds compile-time visibility.
    implementation("com.anthropic:anthropic-java:2.52.0")
}
