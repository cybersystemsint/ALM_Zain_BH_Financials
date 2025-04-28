package com.telkom.co.ke.almoptics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@ComponentScan(basePackages = {"com.telkom.co.ke.almoptics"})
@EnableScheduling
@EnableAsync
public class AlmOpticsApplication {

    /**
     * Main method to run the Spring Boot application.
     */
    public static void main(String[] args) {
        SpringApplication.run(AlmOpticsApplication.class, args);
    }

    /**
     * Set the default timezone to Africa/Nairobi to ensure consistent date/time handling.
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Nairobi"));
    }
}
