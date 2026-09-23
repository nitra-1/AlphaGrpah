// Stage 4 Cross-Domain Convergence (docs/008_Stage3_Sequence_Detection_Specification.md's own
// hand-off point). Self-contained, mirroring decision's exact pattern: not in
// ModuleBoundaryArchTest's DOMAIN_MODULES list, so it's allowed to depend on every domain module
// directly rather than needing its own separate bridge - and, because intelligence owns no
// schema/migration folder of its own, this module owns its own `discovery` schema too, the same
// way decision/learning already do, rather than splitting compute from persistence across two
// modules for no benefit.
dependencies {
    api(project(":common"))
    api(project(":reference"))
    implementation(project(":market"))
    implementation(project(":financial"))
    implementation(project(":ownership"))
    implementation(project(":sector"))
    implementation(project(":corporate")) // Capital Allocation lives here
    implementation(project(":risk"))      // Stage 2 contradiction overlay only
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-jdbc")
    implementation("org.slf4j:slf4j-api")
}
