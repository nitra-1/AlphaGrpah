// Assembles DTOs from every domain/intelligence/decision module's <module>.api package — see docs/004_API_Architecture.md §5.
dependencies {
    implementation(project(":common"))
    implementation(project(":reference"))
    implementation(project(":market"))
    implementation(project(":financial"))
    implementation(project(":ownership"))
    implementation(project(":corporate"))
    implementation(project(":sector"))
    implementation(project(":technical"))
    implementation(project(":risk"))
    implementation(project(":intelligence"))
    implementation(project(":decision"))
    implementation(project(":learning"))
    // Not in the original dependency list (see 001_System_Architecture.md §3/§4) - added because
    // the /pipeline-definitions/{id}/run and /pipeline-executions endpoints in
    // docs/004_API_Architecture.md §6 assemble DTOs from data scheduler owns.
    implementation(project(":scheduler"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework:spring-jdbc")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    testImplementation("org.springframework.security:spring-security-test")
}
