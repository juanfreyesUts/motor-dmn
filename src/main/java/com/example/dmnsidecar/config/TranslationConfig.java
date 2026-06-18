package com.example.dmnsidecar.config;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Crea el cliente de Google Cloud Translation (API V2) solo cuando la traduccion
 * esta habilitada ({@code translation.enabled=true}). Si la traduccion esta apagada
 * no se crea el bean y el servicio degrada al idioma origen sin tocar la API.
 */
@Configuration
@EnableConfigurationProperties(TranslationProperties.class)
public class TranslationConfig {

    /**
     * Cliente {@link Translate}. Usa la API key si se configuro; en caso contrario
     * cae en las Application Default Credentials (env {@code GOOGLE_APPLICATION_CREDENTIALS}).
     */
    @Bean
    @ConditionalOnProperty(prefix = "translation", name = "enabled", havingValue = "true")
    public Translate googleTranslate(TranslationProperties props) {
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            return TranslateOptions.newBuilder()
                    .setApiKey(props.getApiKey())
                    .build()
                    .getService();
        }
        // Application Default Credentials (cuenta de servicio via GOOGLE_APPLICATION_CREDENTIALS).
        return TranslateOptions.getDefaultInstance().getService();
    }
}
