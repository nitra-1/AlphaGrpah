dependencies {
    api(project(":common"))
    api(project(":reference"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-jdbc")
    implementation("org.springframework:spring-web")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.slf4j:slf4j-api")
    // MultipartBodyBuilder (used by NlpSidecarClient) references org.reactivestreams.Publisher
    // internally even for blocking RestClient usage - not pulled in by plain spring-web alone.
    implementation("org.reactivestreams:reactive-streams")
    // Module 2.3: official Anthropic Java SDK for the Corporate Event Engine's real Claude API
    // call. Resolves credentials from ANTHROPIC_API_KEY at runtime (AnthropicOkHttpClient.fromEnv()) -
    // never hardcoded, never passed through Spring config.
    implementation("com.anthropic:anthropic-java:2.52.0")
    // Gemini free-tier pilot (NewsExtractor only, GeminiNewsExtractionClient) - official Google
    // Gen AI Java SDK. Resolves credentials from GOOGLE_API_KEY at runtime (new Client()) - same
    // "never hardcoded" precedent as the Anthropic client above.
    implementation("com.google.genai:google-genai:1.67.0")
}
