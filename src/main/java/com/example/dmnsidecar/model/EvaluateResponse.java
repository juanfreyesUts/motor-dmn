package com.example.dmnsidecar.model;

import java.util.List;
import java.util.Map;

public record EvaluateResponse(
    boolean success,
    Map<String, Object> outputs,
    List<MatchedRule> matchedRules,
    long executionTimeMs,
    String errorCode,
    String message
) {
    public static EvaluateResponse ok(Map<String, Object> outputs, List<MatchedRule> matchedRules, long ms) {
        return new EvaluateResponse(true, outputs, matchedRules, ms, null, null);
    }

    public static EvaluateResponse error(String errorCode, String message) {
        return new EvaluateResponse(false, null, List.of(), 0, errorCode, message);
    }
}
