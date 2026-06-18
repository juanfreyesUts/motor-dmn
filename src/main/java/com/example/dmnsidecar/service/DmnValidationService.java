package com.example.dmnsidecar.service;

import com.example.dmnsidecar.model.ValidationMessage;
import com.example.dmnsidecar.model.ValidationResponse;
import com.example.dmnsidecar.transformer.DmnXmlTransformer;
import com.example.dmnsidecar.translator.DmnMessageTranslator;
import com.example.dmnsidecar.translator.MessageTranslationService;
import org.kie.dmn.validation.DMNValidator;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.kie.dmn.validation.DMNValidator.Validation.VALIDATE_COMPILATION;
import static org.kie.dmn.validation.DMNValidator.Validation.VALIDATE_MODEL;
import static org.kie.dmn.validation.DMNValidator.Validation.VALIDATE_SCHEMA;

@Service
public class DmnValidationService {

    private final DMNValidator validator;
    private final DmnXmlTransformer transformer;
    private final DmnMessageTranslator translator;
    private final MessageTranslationService translationService;

    public DmnValidationService(DMNValidator validator, DmnXmlTransformer transformer,
                                DmnMessageTranslator translator, MessageTranslationService translationService) {
        this.validator = validator;
        this.transformer = transformer;
        this.translator = translator;
        this.translationService = translationService;
    }

    /**
     * Valida el XML y traduce los mensajes al idioma destino solicitado.
     *
     * @param xml           XML del diagrama DMN
     * @param targetLanguage idioma destino (ISO-639-1); {@code null}/origen deja los mensajes en ingles
     */
    public ValidationResponse validate(String xml, String targetLanguage) {
        return translationService.translate(doValidate(xml), targetLanguage);
    }

    private ValidationResponse doValidate(String xml) {
        String transformed = transformer.transform(xml);

        DmnElementIndex index;
        try {
            index = DmnElementIndex.from(transformed);
        } catch (SAXException e) {
            return ValidationResponse.malformed(e.getMessage());
        } catch (Exception e) {
            return ValidationResponse.malformed("Could not parse XML: " + e.getMessage());
        }

        List<org.kie.dmn.api.core.DMNMessage> engineMessages;
        try {
            engineMessages = validator.validate(new StringReader(transformed), VALIDATE_SCHEMA, VALIDATE_MODEL, VALIDATE_COMPILATION);
        } catch (Exception e) {
            return ValidationResponse.engineError("Validator failed unexpectedly: " + e.getMessage());
        }

        Set<List<String>> seen = new LinkedHashSet<>();
        List<ValidationMessage> messages = new ArrayList<>();
        List<ValidationMessage> globalMessages = new ArrayList<>();

        for (var msg : engineMessages) {
            var mapped = translator.translate(msg, index);
            if (!seen.add(List.of(String.valueOf(mapped.sourceId()), String.valueOf(mapped.rawDetail())))) continue;

            if (mapped.drgElementId() != null) {
                messages.add(mapped);
            } else {
                globalMessages.add(mapped);
            }
        }

        boolean canSave = messages.stream().noneMatch(m -> "ERROR".equals(m.level()))
                && globalMessages.stream().noneMatch(m -> "ERROR".equals(m.level()));

        return new ValidationResponse(canSave, messages, globalMessages);
    }
}