dependencies {
    api(project(":common"))
    api(project(":reference"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-jdbc")
    implementation("org.slf4j:slf4j-api")
}
