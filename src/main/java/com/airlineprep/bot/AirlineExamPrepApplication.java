package com.airlineprep.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Persisted admin authentication is supplied explicitly; never generate a default user.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AirlineExamPrepApplication {

    public static void main(String[] args) {
        SpringApplication.run(AirlineExamPrepApplication.class, args);
    }
}
