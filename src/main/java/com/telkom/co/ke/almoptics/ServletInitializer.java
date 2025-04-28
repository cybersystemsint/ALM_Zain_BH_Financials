package com.telkom.co.ke.almoptics;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Servlet initializer for WAR deployment of the Spring Boot application.
 */
public class ServletInitializer extends SpringBootServletInitializer {

    /**
     * Configure the application to use AlmOpticsApplication as the main source.
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(AlmOpticsApplication.class);
    }
}