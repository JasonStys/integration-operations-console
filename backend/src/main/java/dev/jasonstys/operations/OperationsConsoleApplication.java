/*
 * File: OperationsConsoleApplication.java
 * Purpose: Spring Boot process entry point and shared clock configuration.
 * Symbols: OperationsConsoleApplication and clock(); exact lines are indexed in docs/code-index.md.
 * State: no mutable global variables; Clock is injected to keep time-dependent tests deterministic.
 */
package dev.jasonstys.operations;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** Starts the API and exposes an injectable UTC clock. */
@SpringBootApplication
public class OperationsConsoleApplication {

    /**
     * Starts the application.
     *
     * @param args standard Spring command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(OperationsConsoleApplication.class, args);
    }

    /**
     * Provides UTC time to services so tests can substitute a fixed clock.
     *
     * @return system UTC clock
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
