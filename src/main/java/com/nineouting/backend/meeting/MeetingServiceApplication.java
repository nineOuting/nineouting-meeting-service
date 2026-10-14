package com.nineouting.backend.meeting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MeetingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetingServiceApplication.class, args);
    }
}
