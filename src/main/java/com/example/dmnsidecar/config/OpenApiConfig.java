package com.example.dmnsidecar.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configura la informacion que muestra Swagger UI (equivalente a la config de
 * Swashbuckle en .NET). La UI queda disponible en /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dmnSidecarOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("DMN Sidecar API")
                .description("Microservicio de evaluacion de decisiones DMN con Drools / Apache KIE. "
                        + "Consumido via REST por control.bpm.api.")
                .version("0.0.1"));
    }
}
