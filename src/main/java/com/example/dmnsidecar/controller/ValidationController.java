package com.example.dmnsidecar.controller;

import com.example.dmnsidecar.model.ValidationRequest;
import com.example.dmnsidecar.model.ValidationResponse;
import com.example.dmnsidecar.service.DmnValidationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
public class ValidationController {

    private final DmnValidationService validationService;

    public ValidationController(DmnValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidationResponse> validateJson(@RequestBody ValidationRequest request, Locale locale) {
        if (request.xml() == null || request.xml().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(validationService.validate(request.xml(), locale.getLanguage()));
    }

    @PostMapping(value = "/validate", consumes = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<ValidationResponse> validateXml(@RequestBody String xml, Locale locale) {
        if (xml == null || xml.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(validationService.validate(xml, locale.getLanguage()));
    }
}