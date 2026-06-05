package com.example.dmnsidecar.model;

import java.util.Map;

public record EvaluateRequest(
    String xml,
    String decisionName,
    Map<String, Object> inputs
) {}
