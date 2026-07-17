package com.example.thresholdservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.example.thresholdservice", "com.example.common"})
@EnableFeignClients
@EnableScheduling
public class ThresholdServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThresholdServiceApplication.class, args);
    }
}
