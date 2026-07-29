package com.alphagraph.bootstrap.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

/**
 * Spring Boot's Flyway auto-configuration only supports a single schema/location pair, but
 * AlphaGraph is schema-per-module (docs/003_Database_Architecture.md §5): each module owns
 * its own migration history under db/migration/<module> and its own Postgres schema. This
 * runs one independent Flyway instance per module instead of relying on Boot's default bean
 * — spring.flyway.enabled=false disables that default so the two don't collide.
 *
 * Runs as an ApplicationRunner (after context refresh), which is fine while no module has
 * JPA entities yet. Once a module needs Hibernate to validate against a migrated schema at
 * startup, this will need to move earlier (before EntityManagerFactory creation).
 */
@Configuration
public class FlywayMultiSchemaConfig {

    private static final List<String> MODULES_WITH_MIGRATIONS = List.of(
        "common", "reference", "scheduler", "api", "market", "ownership", "financial", "corporate", "technical", "sector"
    );

    @Bean
    public ApplicationRunner moduleFlywayMigrationsRunner(DataSource dataSource) {
        return args -> {
            for (String module : MODULES_WITH_MIGRATIONS) {
                Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(module)
                    .locations("classpath:db/migration/" + module)
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
            }
        };
    }
}
