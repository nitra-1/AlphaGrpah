// Orchestrates pipelines registered by domain modules — owns no domain logic itself, see docs/002_Engine_Architecture.md §6.
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
}
