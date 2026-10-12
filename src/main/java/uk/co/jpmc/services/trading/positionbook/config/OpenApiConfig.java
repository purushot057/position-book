package uk.co.jpmc.services.trading.positionbook.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI positionBookOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Position Book API")
                                .version("1.0")
                                .description(
                                        "Process trade events and retrieve positions by account and security"));
    }
}
