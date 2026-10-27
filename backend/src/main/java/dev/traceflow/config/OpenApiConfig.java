package dev.traceflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI traceflowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TraceFlow API")
                .version("1.0.0")
                .description("TraceFlow - API & Distributed Log Analyzer. Import log files, run demo scenarios and "
                        + "investigate traces, events, errors and analytics.")
                .license(new License().name("MIT")));
    }
}
