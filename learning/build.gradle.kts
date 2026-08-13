dependencies {
    api(project(":common"))
    api(project(":reference"))
    implementation(project(":intelligence"))
    implementation(project(":decision"))
    // Only for the com.alphagraph.corporate.api.CorporateAction type returned by
    // PriceAdjustmentService.findPriceAffectingActions() (intelligence) - learning never queries
    // corporate.corporate_actions itself, it only reads the DTO intelligence's published bridge
    // returns, the same "depend on the module for its published .api type" pattern decision's own
    // build.gradle.kts already establishes for all six domain modules.
    implementation(project(":corporate"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-jdbc")
    implementation("org.slf4j:slf4j-api")
}
