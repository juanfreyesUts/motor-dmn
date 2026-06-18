package com.example.dmnsidecar.config;

import org.kie.dmn.validation.DMNValidator;
import org.kie.dmn.validation.DMNValidatorFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DmnValidatorConfig {

    @Bean
    public DMNValidator dmnValidator() {
        return DMNValidatorFactory.newValidator();
    }
}