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
}
