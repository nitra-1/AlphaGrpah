dependencies {
    api(project(":common"))
    api(project(":reference"))
    implementation(project(":market"))
    implementation(project(":financial"))
    implementation(project(":ownership"))
    implementation(project(":corporate"))
    implementation(project(":sector"))
    implementation(project(":technical"))
    implementation(project(":risk"))
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")
}
