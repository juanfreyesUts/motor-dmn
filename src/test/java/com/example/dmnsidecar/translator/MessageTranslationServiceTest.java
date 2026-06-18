package com.example.dmnsidecar.translator;

import com.example.dmnsidecar.config.TranslationProperties;
import com.example.dmnsidecar.model.ValidationMessage;
import com.example.dmnsidecar.model.ValidationResponse;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.Translation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del traductor de mensajes con un cliente {@link Translate} mockeado.
 * No se realiza ninguna llamada real a Google Cloud.
 */
class MessageTranslationServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<Translate> provider = mock(ObjectProvider.class);
    private final Translate translate = mock(Translate.class);
    private TranslationProperties props;

    @BeforeEach
    void setUp() {
        props = new TranslationProperties();
        props.setEnabled(true);
        props.setSourceLanguage("en");
        props.setDefaultLanguage("en");
        props.setSupportedLanguages(List.of("en", "es", "pt"));
        props.setCacheEnabled(true);
        props.setCacheMaxEntries(5000);

        when(provider.getIfAvailable()).thenReturn(translate);
        // Devuelve "ES:<texto>" por cada texto de entrada, preservando el orden del batch.
        when(translate.translate(anyList(), any(), any(), any())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(t -> {
                Translation tr = mock(Translation.class);
                when(tr.getTranslatedText()).thenReturn("ES:" + t);
                return tr;
            }).toList();
        });
    }

    private MessageTranslationService service() {
        return new MessageTranslationService(provider, props);
    }

    private static ValidationResponse responseWith(String... texts) {
        List<ValidationMessage> msgs = java.util.Arrays.stream(texts)
                .map(t -> new ValidationMessage("src", "drg", "MiDecision", "ERROR", "FEEL_EXPRESSION", t, "raw:" + t))
                .toList();
        return new ValidationResponse(false, msgs, List.of());
    }

    @Test
    void traduceElTextoYDejaIntactoElResto() {
        ValidationResponse result = service().translate(responseWith("Decision is missing"), "es");

        ValidationMessage m = result.messages().get(0);
        assertThat(m.text()).isEqualTo("ES:Decision is missing");
        assertThat(m.rawDetail()).isEqualTo("raw:Decision is missing"); // no se traduce
        assertThat(m.drgElementName()).isEqualTo("MiDecision");           // no se traduce
        assertThat(m.level()).isEqualTo("ERROR");
        assertThat(m.type()).isEqualTo("FEEL_EXPRESSION");
        verify(translate, times(1)).translate(anyList(), any(), any(), any());
    }

    @Test
    void noTraduceCuandoElDestinoEsElOrigen() {
        ValidationResponse original = responseWith("Decision is missing");
        ValidationResponse result = service().translate(original, "en");

        assertThat(result).isSameAs(original);
        verify(translate, never()).translate(anyList(), any(), any(), any());
    }

    @Test
    void noTraduceCuandoEstaDeshabilitado() {
        props.setEnabled(false);
        ValidationResponse original = responseWith("Decision is missing");

        ValidationResponse result = service().translate(original, "es");

        assertThat(result).isSameAs(original);
        verify(translate, never()).translate(anyList(), any(), any(), any());
    }

    @Test
    void noTraduceIdiomaNoSoportado() {
        ValidationResponse original = responseWith("Decision is missing");

        ValidationResponse result = service().translate(original, "ru");

        assertThat(result).isSameAs(original);
        verify(translate, never()).translate(anyList(), any(), any(), any());
    }

    @Test
    void usaLaCacheEnLaSegundaLlamada() {
        MessageTranslationService svc = service();
        svc.translate(responseWith("Decision is missing"), "es");
        svc.translate(responseWith("Decision is missing"), "es");

        // Solo una llamada a la API: la segunda se resuelve desde la cache.
        verify(translate, times(1)).translate(anyList(), any(), any(), any());
    }

    @Test
    void agrupaTextosUnicosEnUnaSolaLlamada() {
        ValidationResponse result = service().translate(
                responseWith("A", "B", "A"), "es"); // "A" duplicado

        assertThat(result.messages()).extracting(ValidationMessage::text)
                .containsExactly("ES:A", "ES:B", "ES:A");
        // Una sola llamada batch para los 3 mensajes (con textos unicos A, B).
        verify(translate, times(1)).translate(anyList(), any(), any(), any());
    }

    @Test
    void devuelveOriginalSiLaApiFalla() {
        when(translate.translate(anyList(), any(), any(), any()))
                .thenThrow(new RuntimeException("quota exceeded"));
        ValidationResponse original = responseWith("Decision is missing");

        ValidationResponse result = service().translate(original, "es");

        assertThat(result).isSameAs(original); // degradacion elegante, sin excepcion
    }

    @Test
    void normalizaElCodigoDeRegion() {
        ValidationResponse result = service().translate(responseWith("Hello"), "es-CO");

        assertThat(result.messages().get(0).text()).isEqualTo("ES:Hello");
    }
}
