/*
 * Copyright (c) 2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin. Unauthorized copying,
 * modification, distribution, reverse engineering, or use of the Software,
 * via any medium, is strictly prohibited without prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ch.lin.finance.hub.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The main entry point for the Finance Hub backend Spring Boot application.
 * <p>
 * This class initializes and runs the Spring application context. It is
 * configured with custom scan packages for components, entities, and
 * repositories to ensure all parts of the multi-module project are discovered.
 * <p>
 * It also extends {@link SpringBootServletInitializer} to support deployment as
 * a traditional WAR file in a servlet container.
 */
@SpringBootApplication(scanBasePackages = "ch.lin.finance.hub.backend")
@EntityScan("ch.lin.finance.hub.backend.api.domain.model")
@EnableJpaRepositories(basePackages = "ch.lin.finance.hub.backend")
@EnableScheduling
public class FinanceHubApplication extends SpringBootServletInitializer {

    /**
     * The main method which serves as the entry point for the Java application.
     *
     * @param args Command line arguments passed to the application.
     */
    public static void main(String[] args) {
        SpringApplication.run(FinanceHubApplication.class, args);
    }
}
