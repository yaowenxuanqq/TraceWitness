package com.tracewitness.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TraceWitnessApplication {
    public static void main(String[] args) {
        SpringApplication.run(TraceWitnessApplication.class, args);
    }
}
