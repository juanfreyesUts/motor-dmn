package com.example.dmnsidecar.translator;

import com.example.dmnsidecar.model.ValidationMessage;
import com.example.dmnsidecar.service.DmnElementIndex;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNMessageType;
import org.springframework.stereotype.Component;

@Component
public class DmnMessageTranslator {

    public ValidationMessage translate(DMNMessage msg, DmnElementIndex index) {
        String sourceId = msg.getSourceId();
        String drgElementId = null;
        String drgElementName = null;

        if (sourceId != null) {
            var ref = index.resolve(sourceId);
            if (ref.isPresent()) {
                drgElementId = ref.get().drgElementId();
                drgElementName = ref.get().drgElementName();
            }
        }

        String level = switch (msg.getSeverity()) {
            case ERROR -> "ERROR";
            case WARN -> "WARN";
            case INFO, TRACE -> "INFO";
        };

        String rawDetail = msg.getMessage();
        String text = buildText(rawDetail, msg);

        return new ValidationMessage(sourceId, drgElementId, drgElementName, level, mapType(msg.getMessageType()), text, rawDetail);
    }

    private static String buildText(String rawDetail, DMNMessage msg) {
        if (msg.getException() == null) return rawDetail;
        String exMsg = msg.getException().getMessage();
        if (exMsg == null || rawDetail.contains(exMsg)) return rawDetail;
        return rawDetail + " — " + exMsg;
    }

    private static String mapType(DMNMessageType type) {
        if (type == null) return "UNKNOWN";
        return switch (type) {
            case FAILED_XML_VALIDATION -> "SCHEMA";
            case ERR_COMPILING_FEEL, INVALID_SYNTAX, FEEL_EVALUATION_ERROR -> "FEEL_EXPRESSION";
            case MISSING_VARIABLE, REQ_NOT_FOUND -> "MISSING_VARIABLE";
            case TYPE_REF_NOT_FOUND, TYPE_DEF_NOT_FOUND, TYPEREF_MISMATCH, MISSING_TYPE_REF, ILLEGAL_USE_OF_TYPEREF -> "TYPE_ERROR";
            case DUPLICATE_NAME, DUPLICATED_ITEM_DEF, DUPLICATED_RELATION_COLUMN, DUPLICATED_PARAM -> "DUPLICATE";
            case MISSING_NAME, MISSING_EXPRESSION -> "MISSING_ELEMENT";
            case INVALID_NAME, ILLEGAL_USE_OF_NAME, VARIABLE_NAME_MISMATCH -> "NAMING";
            case DECISION_TABLE_OVERLAP,
                 DECISION_TABLE_OVERLAP_HITPOLICY_UNIQUE,
                 DECISION_TABLE_OVERLAP_HITPOLICY_ANY -> "TABLE_OVERLAP";
            case DECISION_TABLE_GAP -> "TABLE_GAP";
            case DECISION_TABLE_MASKED_RULE,
                 DECISION_TABLE_MISLEADING_RULE,
                 DECISION_TABLE_SUBSUMPTION_RULE,
                 DECISION_TABLE_CONTRACTION_RULE -> "TABLE_RULE";
            case DECISION_TABLE_ANALYSIS,
                 DECISION_TABLE_ANALYSIS_EMPTY,
                 DECISION_TABLE_ANALYSIS_ERROR,
                 DECISION_TABLE_HITPOLICY_FIRST,
                 DECISION_TABLE_HITPOLICY_RECOMMENDER,
                 DECISION_TABLE_1STNFVIOLATION,
                 DECISION_TABLE_2NDNFVIOLATION -> "TABLE_ANALYSIS";
            case FAILED_VALIDATOR, FAILED_VALIDATION -> "VALIDATION_ERROR";
            case UNSUPPORTED_ELEMENT -> "UNSUPPORTED";
            case IMPORT_NOT_FOUND -> "IMPORT_NOT_FOUND";
            case PARAMETER_MISMATCH -> "PARAMETER_MISMATCH";
            case INVALID_ATTRIBUTE_VALUE -> "INVALID_ATTRIBUTE";
            case MISSING_OUTPUT_VALUES -> "MISSING_OUTPUT_VALUES";
            case INVALID_HREF_SYNTAX -> "INVALID_HREF";
            case RELATION_CELL_NOT_LITERAL, RELATION_CELL_COUNT_MISMATCH -> "RELATION_ERROR";
            case DMNDI_MISSING_DIAGRAM, DMNDI_UNKNOWN_REF -> "DIAGRAM";
            default -> type.name();
        };
    }
}