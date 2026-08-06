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
}
