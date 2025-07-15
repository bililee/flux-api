package com.bililee.demo.fluxapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.bililee.demo.fluxapi.common.feign")
public class FluxApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FluxApiApplication.class, args);
    }

}
