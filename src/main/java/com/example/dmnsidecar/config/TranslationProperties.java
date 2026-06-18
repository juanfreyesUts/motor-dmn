package com.example.dmnsidecar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuracion del traductor de mensajes de validacion (prefijo "translation").
 *
 * <p>Permite habilitar/deshabilitar la traduccion, definir el idioma origen (el que
 * emite el motor DMN, ingles por defecto), el idioma por defecto cuando el cliente no
 * pide uno valido, y la lista de idiomas soportados. Las credenciales de Google Cloud
 * se resuelven por defecto via Application Default Credentials (variable de entorno
 * {@code GOOGLE_APPLICATION_CREDENTIALS}); como alternativa simple se puede fijar una
 * {@code api-key}.
 */
@ConfigurationProperties(prefix = "translation")
public class TranslationProperties {

    /** Habilita la traduccion. Si es false, las respuestas se devuelven en el idioma origen. */
    private boolean enabled = false;

    /** Idioma en el que el motor DMN emite los mensajes (ISO-639-1). No se traduce si destino == origen. */
    private String sourceLanguage = "en";

    /** Idioma usado cuando el cliente no envia uno valido en Accept-Language. */
    private String defaultLanguage = "en";

    /** Idiomas permitidos (ISO-639-1). Un destino fuera de esta lista se ignora (queda en origen). */
    private List<String> supportedLanguages = List.of("en");

    /** API key de Google Cloud (opcional). Si esta vacia se usan las Application Default Credentials. */
    private String apiKey;

    /** Habilita la cache en memoria de traducciones para evitar llamadas repetidas a la API. */
    private boolean cacheEnabled = true;

    /** Tope de entradas de la cache; al superarse se limpia para evitar crecimiento ilimitado. */
    private int cacheMaxEntries = 5000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public List<String> getSupportedLanguages() {
        return supportedLanguages;
    }

    public void setSupportedLanguages(List<String> supportedLanguages) {
        this.supportedLanguages = supportedLanguages;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(int cacheMaxEntries) {
        this.cacheMaxEntries = cacheMaxEntries;
    }
}
