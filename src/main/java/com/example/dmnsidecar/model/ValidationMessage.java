package com.example.dmnsidecar.model;

public record ValidationMessage(
        String sourceId,
        String drgElementId,
        String drgElementName,
        String level,
        String type,
        String text,
        String rawDetail
) {}
