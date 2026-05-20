package com.sashkolearn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SlApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlApplication.class, args);
    }
}
