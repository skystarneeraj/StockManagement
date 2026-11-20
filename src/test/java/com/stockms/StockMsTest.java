package com.stockms;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs against the stockdb_test database, rebuilt from the Flyway migrations when the test
 * context starts, so tests see exactly the schema production gets. On the empty database the
 * app creates company "My Company" (code MC) with a Head Office, the admin login and sample data.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/stockdb_test",
        "spring.flyway.clean-disabled=false"})
@Import(StockMsTest.CleanDatabase.class)
public @interface StockMsTest {

    @TestConfiguration
    class CleanDatabase {
        @Bean
        FlywayMigrationStrategy cleanThenMigrate() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }
}
