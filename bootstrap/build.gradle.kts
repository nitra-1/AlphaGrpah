plugins {
    id("org.springframework.boot") version "3.5.0"
}

dependencies {
    implementation(project(":api"))
    implementation(project(":scheduler"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.1")

    testImplementation("com.tngtech.archunit:archunit:1.3.0")
}
