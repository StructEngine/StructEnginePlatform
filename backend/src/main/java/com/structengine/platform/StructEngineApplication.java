package com.structengine.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StructEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(StructEngineApplication.class, args);
    }
}