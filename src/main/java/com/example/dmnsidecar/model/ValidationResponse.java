package com.example.dmnsidecar.model;

import java.util.List;

public record ValidationResponse(
        boolean canSave,
        List<ValidationMessage> messages,
        List<ValidationMessage> globalMessages
) {
    public static ValidationResponse malformed(String detail) {
        var msg = new ValidationMessage(null, null, null, "ERROR", "MALFORMED_XML", detail, detail);
        return new ValidationResponse(false, List.of(), List.of(msg));
    }

    public static ValidationResponse engineError(String detail) {
        var msg = new ValidationMessage(null, null, null, "ERROR", "VALIDATION_ERROR", detail, detail);
        return new ValidationResponse(false, List.of(), List.of(msg));
    }
}
