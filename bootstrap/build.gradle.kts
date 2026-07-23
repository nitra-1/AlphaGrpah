plugins {
    id("org.springframework.boot") version "3.5.0"
}

dependencies {
    implementation(project(":api"))
    implementation(project(":scheduler"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("com.tngtech.archunit:archunit:1.3.0")
}
