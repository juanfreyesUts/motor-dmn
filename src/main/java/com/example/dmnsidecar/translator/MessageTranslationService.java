package com.example.dmnsidecar.translator;

import com.example.dmnsidecar.config.TranslationProperties;
import com.example.dmnsidecar.model.ValidationMessage;
import com.example.dmnsidecar.model.ValidationResponse;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.Translation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traduce el campo {@code text} de los mensajes de una {@link ValidationResponse}
 * usando Google Cloud Translation (API V2).
 *
 * <p>Caracteristicas:
 * <ul>
 *   <li><b>Dinamico:</b> el idioma destino llega por peticion (cabecera Accept-Language).</li>
 *   <li><b>Eficiente:</b> agrupa todos los textos unicos y los traduce en una sola llamada.</li>
 *   <li><b>Con cache:</b> reutiliza traducciones previas (mensajes de validacion se repiten mucho).</li>
 *   <li><b>Tolerante a fallos:</b> ante error, idioma no soportado o traduccion deshabilitada,
 *       devuelve la respuesta original en el idioma origen sin romper la validacion.</li>
 * </ul>
 *
 * <p>El campo {@code rawDetail} se deja intacto (mensaje tecnico original, util para logs);
 * tampoco se traducen {@code drgElementName} (nombre que define el usuario), {@code type} ni
 * {@code level} (codigos).
 */
@Service
public class MessageTranslationService {

    private static final Logger log = LoggerFactory.getLogger(MessageTranslationService.class);

    private final ObjectProvider<Translate> translateProvider;
    private final TranslationProperties props;

    /** Cache: clave "idioma|textoOrigen" -> textoTraducido. */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public MessageTranslationService(ObjectProvider<Translate> translateProvider, TranslationProperties props) {
        this.translateProvider = translateProvider;
        this.props = props;
    }

    /**
     * Devuelve una copia de la respuesta con los textos traducidos al idioma destino,
     * o la misma respuesta si no procede traducir.
     *
     * @param response respuesta de validacion en el idioma origen
     * @param targetLanguage idioma destino solicitado (ISO-639-1, puede ser null/blank)
     */
    public ValidationResponse translate(ValidationResponse response, String targetLanguage) {
        String target = resolveTarget(targetLanguage);
        if (target == null) {
            return response; // no procede traducir: devolver original
        }

        Translate client = translateProvider.getIfAvailable();
        if (client == null) {
            return response; // traduccion habilitada pero sin cliente: degradar
        }

        // 1) Recolectar textos unicos a traducir (no nulos / no vacios).
        Map<String, String> translations = new LinkedHashMap<>();
        collectTexts(response.messages(), target, translations);
        collectTexts(response.globalMessages(), target, translations);
        if (translations.isEmpty()) {
            return response;
        }

        // 2) Resolver desde cache lo que se pueda; traducir el resto en una sola llamada.
        try {
            resolveWithApi(client, target, translations);
        } catch (Exception e) {
            log.warn("Translation to '{}' failed, returning original messages: {}", target, e.getMessage());
            return response; // degradacion elegante
        }

        // 3) Reconstruir la respuesta con los textos traducidos.
        return new ValidationResponse(
                response.canSave(),
                applyTranslations(response.messages(), translations),
                applyTranslations(response.globalMessages(), translations)
        );
    }

    /**
     * Normaliza y valida el idioma destino. Devuelve null cuando no se debe traducir:
     * traduccion deshabilitada, destino vacio, destino == origen, o destino no soportado.
     */
    private String resolveTarget(String requested) {
        if (!props.isEnabled()) {
            return null;
        }
        String target = (requested == null || requested.isBlank())
                ? props.getDefaultLanguage()
                : requested.trim().toLowerCase(Locale.ROOT);

        // Tomar solo el codigo de idioma base (ej. "es-CO" -> "es").
        int dash = target.indexOf('-');
        if (dash > 0) {
            target = target.substring(0, dash);
        }

        if (target.equalsIgnoreCase(props.getSourceLanguage())) {
            return null;
        }
        boolean supported = props.getSupportedLanguages().stream().anyMatch(target::equalsIgnoreCase);
        return supported ? target : null;
    }

    /** Agrega a {@code out} los textos (clave) pendientes; el valor se rellena luego. */
    private void collectTexts(List<ValidationMessage> messages, String target, Map<String, String> out) {
        if (messages == null) {
            return;
        }
        for (ValidationMessage m : messages) {
            String text = m.text();
            if (text != null && !text.isBlank() && !out.containsKey(text)) {
                String cached = props.isCacheEnabled() ? cache.get(cacheKey(target, text)) : null;
                out.put(text, cached); // null = aun por traducir
            }
        }
    }

    /** Rellena en {@code translations} los textos que faltan (valor null) llamando a la API en batch. */
    private void resolveWithApi(Translate client, String target, Map<String, String> translations) {
        List<String> pending = new ArrayList<>();
        for (Map.Entry<String, String> e : translations.entrySet()) {
            if (e.getValue() == null) {
                pending.add(e.getKey());
            }
        }
        if (pending.isEmpty()) {
            return; // todo estaba en cache
        }

        List<Translation> results = client.translate(
                pending,
                Translate.TranslateOption.sourceLanguage(props.getSourceLanguage()),
                Translate.TranslateOption.targetLanguage(target),
                Translate.TranslateOption.format("text") // evita escapes HTML en el resultado
        );

        for (int i = 0; i < pending.size(); i++) {
            String original = pending.get(i);
            String translated = results.get(i).getTranslatedText();
            translations.put(original, translated);
            if (props.isCacheEnabled()) {
                putInCache(cacheKey(target, original), translated);
            }
        }
    }

    /** Crea copias de los mensajes con el texto traducido (cae al original si falta). */
    private List<ValidationMessage> applyTranslations(List<ValidationMessage> messages, Map<String, String> translations) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        List<ValidationMessage> out = new ArrayList<>(messages.size());
        for (ValidationMessage m : messages) {
            String translated = m.text() == null ? null : translations.get(m.text());
            if (translated == null || translated.equals(m.text())) {
                out.add(m);
            } else {
                out.add(new ValidationMessage(
                        m.sourceId(), m.drgElementId(), m.drgElementName(),
                        m.level(), m.type(), translated, m.rawDetail()));
            }
        }
        return out;
    }

    private void putInCache(String key, String value) {
        // Guarda simple contra crecimiento ilimitado: al tope, se vacia.
        if (cache.size() >= props.getCacheMaxEntries()) {
            cache.clear();
        }
        cache.put(key, value);
    }

    private static String cacheKey(String target, String text) {
        return target + '|' + text;
    }
}
