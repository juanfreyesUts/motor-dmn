package com.example.dmnsidecar.controller;

import com.example.dmnsidecar.model.EvaluateRequest;
import com.example.dmnsidecar.model.EvaluateResponse;
import com.example.dmnsidecar.service.DmnService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class DmnController {

    private final DmnService dmnService;
    private final ObjectMapper objectMapper;

    public DmnController(DmnService dmnService, ObjectMapper objectMapper) {
        this.dmnService = dmnService;
        this.objectMapper = objectMapper;
    }

    // Endpoint original: JSON con el XML embebido como string
    @PostMapping(value = "/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EvaluateResponse> evaluate(@RequestBody EvaluateRequest request) {
        EvaluateResponse response = dmnService.evaluate(request);
        int status = response.success() ? 200 : 422;
        return ResponseEntity.status(status).body(response);
    }

    // Endpoint alternativo: XML como texto plano en form-data (no como archivo ni
    // embebido en JSON), para pegar el XML tal cual sin escapar comillas/saltos de línea.
    // En Postman: Body -> form-data
    //   xml          (tipo Text)   → el XML completo del DMN
    //   decisionName (tipo Text)   → nombre de la decisión (opcional)
    //   inputs       (tipo Text)   → {"edad": 25, "ingreso": 6000}
    @PostMapping(value = "/evaluate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvaluateResponse> evaluateXml(
            @RequestParam("xml") String xml,
            @RequestParam(value = "decisionName", required = false) String decisionName,
            @RequestParam(value = "inputs", defaultValue = "{}") String inputsJson) {
        return evaluateXmlPayload(xml, decisionName, inputsJson);
    }

    // Endpoint multipart: archivo .dmn + inputs como JSON aparte
    // En Postman: Body -> form-data
    //   file         (tipo File)   → seleccionar el .dmn
    //   decisionName (tipo Text)   → nombre de la decisión
    //   inputs       (tipo Text)   → {"edad": 25, "ingreso": 6000}
    @PostMapping(value = "/evaluate/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvaluateResponse> evaluateFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "decisionName", required = false) String decisionName,
            @RequestParam(value = "inputs", defaultValue = "{}") String inputsJson) {
        try {
            String xml = new String(file.getBytes(), StandardCharsets.UTF_8);
            return evaluateXmlPayload(xml, decisionName, inputsJson);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                EvaluateResponse.error("BAD_REQUEST", "inputs JSON inválido o archivo ilegible: " + e.getMessage())
            );
        }
    }

    private ResponseEntity<EvaluateResponse> evaluateXmlPayload(
            String xml, String decisionName, String inputsJson) {
        try {
            Map<String, Object> inputs = objectMapper.readValue(inputsJson, new TypeReference<>() {});
            EvaluateRequest request = new EvaluateRequest(xml, decisionName, inputs);
            EvaluateResponse response = dmnService.evaluate(request);
            int status = response.success() ? 200 : 422;
            return ResponseEntity.status(status).body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                EvaluateResponse.error("BAD_REQUEST", "inputs JSON inválido o archivo ilegible: " + e.getMessage())
            );
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
