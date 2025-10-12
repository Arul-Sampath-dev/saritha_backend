package com.saritha.fashions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@SpringBootApplication
public class SarithaFashionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SarithaFashionsApplication.class, args);
    }

}
