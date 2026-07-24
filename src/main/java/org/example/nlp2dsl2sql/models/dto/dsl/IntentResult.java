package org.example.nlp2dsl2sql.models.dto.dsl;

import lombok.Data;

@Data
public class IntentResult {
    private String intent;
    private double confidence;
    private String reason;

    public enum IntentType {
        METRIC_QUERY, DIMENSION_ANALYSIS, DETAIL_QUERY, NON_BUSINESS
    }

    public static IntentType parseIntentType(String intent) {
        if (intent == null || intent.isBlank()) {
            return IntentType.NON_BUSINESS;
        }
        try {
            return IntentType.valueOf(intent.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return IntentType.NON_BUSINESS;
        }
    }

    public IntentType resolveIntentType() {
        return parseIntentType(this.intent);
    }
}
