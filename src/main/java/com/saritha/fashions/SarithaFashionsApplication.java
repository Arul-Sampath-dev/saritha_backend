package com.saritha.fashions;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

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
@SecurityScheme(
        name = "Bearer Authentication",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste the raw access token from sign-in. Swagger adds the Bearer prefix."
)

@SpringBootApplication
public class SarithaFashionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SarithaFashionsApplication.class, args);
    }

}
