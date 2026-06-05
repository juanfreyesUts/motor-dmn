package com.example.dmnsidecar.service;

import com.example.dmnsidecar.cache.DmnRuntimeCache;
import com.example.dmnsidecar.cache.DmnRuntimeCache.Entry;
import com.example.dmnsidecar.model.EvaluateRequest;
import com.example.dmnsidecar.model.EvaluateResponse;
import com.example.dmnsidecar.transformer.DmnXmlTransformer;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieRuntimeFactory;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DmnService {

    private static final Logger log = LoggerFactory.getLogger(DmnService.class);

    private final DmnRuntimeCache cache;
    private final DmnXmlTransformer transformer;

    public DmnService(DmnRuntimeCache cache, DmnXmlTransformer transformer) {
        this.cache = cache;
        this.transformer = transformer;
    }

    public EvaluateResponse evaluate(EvaluateRequest request) {
        long start = System.currentTimeMillis();

        if (request.xml() == null || request.xml().isBlank()) {
            return EvaluateResponse.error("INVALID_DMN", "El campo 'xml' es obligatorio");
        }

        try {
            String hash = cache.computeHash(request.xml());
            log.info("evaluate decisionName='{}' cacheEntries={} hash={}...",
                request.decisionName(), cache.size(), hash.substring(0, 8));

            Entry entry = cache.get(hash).orElseGet(() -> buildAndCache(hash, request.xml()));

            DMNRuntime runtime = entry.runtime();
            DMNModel model = entry.model();

            boolean hasDecisionName = request.decisionName() != null && !request.decisionName().isBlank();

            if (hasDecisionName) {
                boolean found = model.getDecisions().stream()
                    .anyMatch(d -> d.getName().equals(request.decisionName()));
                if (!found) {
                    return EvaluateResponse.error("DECISION_NOT_FOUND",
                        "Decision '" + request.decisionName() + "' no existe en el modelo");
                }
            }

            DMNContext context = runtime.newContext();
            if (request.inputs() != null) {
                request.inputs().forEach(context::set);
            }

            DMNResult dmnResult = hasDecisionName
                ? runtime.evaluateByName(model, context, request.decisionName())
                : runtime.evaluateAll(model, context);

            List<DMNMessage> errors = dmnResult.getMessages(DMNMessage.Severity.ERROR);
            if (!errors.isEmpty()) {
                String msg = errors.get(0).getText();
                String code = msg.contains("Missing") || msg.contains("Unmatched") ? "MISSING_VARIABLE" : "FEEL_ERROR";
                return EvaluateResponse.error(code, msg);
            }

            Map<String, Object> outputs = new LinkedHashMap<>();
            for (DMNDecisionResult dr : dmnResult.getDecisionResults()) {
                if (dr.getEvaluationStatus() == DMNDecisionResult.DecisionEvaluationStatus.SUCCEEDED) {
                    outputs.put(dr.getDecisionName(), dr.getResult());
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("evaluate OK outputs={} ms={}", outputs.keySet(), elapsed);
            return EvaluateResponse.ok(outputs, List.of(), elapsed);

        } catch (Exception e) {
            log.error("evaluate INTERNAL error", e);
            return EvaluateResponse.error("INTERNAL", e.getMessage());
        }
    }

    private Entry buildAndCache(String hash, String xml) {
        try {
            log.info("Compilando nuevo modelo DMN hash={}...", hash.substring(0, 8));
            String transformedXml = transformer.transform(xml);
            KieServices ks = KieServices.Factory.get();
            KieFileSystem kfs = ks.newKieFileSystem();

            kfs.write(
                "src/main/resources/dmn/" + hash + ".dmn",
                ks.getResources()
                    .newByteArrayResource(transformedXml.getBytes(StandardCharsets.UTF_8))
                    .setResourceType(ResourceType.DMN)
            );

            KieBuilder kb = ks.newKieBuilder(kfs);
            kb.buildAll();

            List<Message> buildErrors = kb.getResults().getMessages(Message.Level.ERROR);
            if (!buildErrors.isEmpty()) {
                String detail = buildErrors.stream().map(Object::toString).reduce("", (a, b) -> a + " | " + b);
                log.warn("Errores de compilacion DMN: {}", detail);
                throw new RuntimeException("BUILD_ERRORS: " + detail);
            }

            KieContainer kc = ks.newKieContainer(kb.getKieModule().getReleaseId());
            DMNRuntime runtime = KieRuntimeFactory.of(kc.getKieBase()).get(DMNRuntime.class);

            List<DMNModel> models = runtime.getModels();
            if (models.isEmpty()) {
                log.warn("El XML no contiene ningun modelo DMN valido");
                throw new RuntimeException("NO_MODELS_FOUND: el XML no contiene ningun modelo DMN");
            }

            DMNModel model = models.get(0);
            Entry entry = new Entry(kc, runtime, model);
            cache.put(hash, entry);
            log.info("Modelo DMN cacheado: namespace='{}' name='{}'", model.getNamespace(), model.getName());
            return entry;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("COMPILE_EXCEPTION: " + e.getMessage(), e);
        }
    }
}
