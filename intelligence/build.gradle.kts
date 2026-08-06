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
    implementation("org.springframework:spring-jdbc")
    implementation("org.slf4j:slf4j-api")
    // Module 2.9: intelligence.analyst.AiAnalystClient compiles against the Anthropic SDK's own
    // types (MessageCreateParams, Model, error classes) directly - corporate's own dependency on
    // this same artifact is `implementation`-scoped, not transitive, so it must be declared here
    // too. The actual AnthropicClient bean is still produced once, by corporate.knowledge.
    // AnthropicClientConfig - this only adds compile-time visibility of the SDK's types.
    implementation("com.anthropic:anthropic-java:2.52.0")
}
