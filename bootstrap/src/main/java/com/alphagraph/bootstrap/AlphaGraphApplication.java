package com.alphagraph.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.alphagraph")
@EnableScheduling
public class AlphaGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlphaGraphApplication.class, args);
    }
}
