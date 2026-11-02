package com.saritha.fashions;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;


@OpenAPIDefinition(
		info = @Info(
				title = " API Documentation",
				version = "1.0",
				description = "API documentation"
		)
)

@SpringBootApplication
public class SarithaFashionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SarithaFashionsApplication.class, args);
    }

}
