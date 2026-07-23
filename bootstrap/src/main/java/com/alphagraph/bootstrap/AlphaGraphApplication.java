package com.alphagraph.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.alphagraph")
public class AlphaGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlphaGraphApplication.class, args);
    }
}
