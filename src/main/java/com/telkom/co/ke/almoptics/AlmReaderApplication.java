package com.telkom.co.ke.almoptics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.telkom.co.ke.almoptics"})
@EnableScheduling
public class AlmReaderApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(new Class[]{com.telkom.co.ke.almoptics.AlmReaderApplication.class});
    }

    public static void main(String[] args) {
        SpringApplication.run(com.telkom.co.ke.almoptics.AlmReaderApplication.class, args);
    }
}
