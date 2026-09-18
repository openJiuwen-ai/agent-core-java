/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BERT binary classification model output parser.
 * <p>
 * Mirrors Python's {@code BertBinaryParser} in
 * {@code openjiuwen/core/security/guardrail/context.py}.
 */
public final class BertBinaryParser implements ModelOutputParser {

    private static final Map<String, Double> DEFAULT_THRESHOLDS = Map.of(
            "low", 0.7d,
            "medium", 0.85d,
            "high", 0.95d
    );

    private final String riskType;
    private final Map<String, Double> thresholds;
    private final int attackClassId;

    public BertBinaryParser() {
        this("attack_detected", null, 1);
    }

    public BertBinaryParser(String riskType) {
        this(riskType, null, 1);
    }

    public BertBinaryParser(String riskType, Map<String, Double> confidenceThresholds, int attackClassId) {
        this.riskType = riskType;
        this.thresholds = confidenceThresholds == null
                ? new LinkedHashMap<>(DEFAULT_THRESHOLDS)
                : new LinkedHashMap<>(confidenceThresholds);
        this.attackClassId = attackClassId;
    }

    @Override
    public RiskAssessment parse(Object modelOutput) {
        Prediction prediction = extractPrediction(modelOutput);
        RiskLevel riskLevel = determineRiskLevel(prediction.predictedClass(), prediction.confidence());
        boolean hasRisk = riskLevel != RiskLevel.SAFE;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("predicted_class", prediction.predictedClass());
        details.put(
                "attack_confidence",
                prediction.predictedClass() == attackClassId ? prediction.confidence() : 1.0d - prediction.confidence()
        );
        return new RiskAssessment(hasRisk, riskLevel, hasRisk ? riskType : null, prediction.confidence(), details);
    }

    private Prediction extractPrediction(Object modelOutput) {
        if (modelOutput instanceof Map<?, ?> map) {
            if (map.containsKey("predicted_class")) {
                Object confidence = map.containsKey("confidence") ? map.get("confidence") : 0.0d;
                return new Prediction(toInt(map.get("predicted_class")), toDouble(confidence));
            }
            if (map.containsKey("label")) {
                Object score = map.containsKey("score")
                        ? map.get("score")
                        : (map.containsKey("confidence") ? map.get("confidence") : 0.0d);
                return new Prediction(toInt(map.get("label")), toDouble(score));
            }
            if (map.get("probabilities") instanceof List<?> probabilities && probabilities.size() >= 2) {
                double p0 = toDouble(probabilities.get(0));
                double p1 = toDouble(probabilities.get(1));
                return new Prediction(p1 > p0 ? 1 : 0, Math.max(p0, p1));
            }
            if (map.get("logits") instanceof List<?> logits && logits.size() >= 2) {
                double l0 = toDouble(logits.get(0));
                double l1 = toDouble(logits.get(1));
                double exp0 = Math.exp(l0);
                double exp1 = Math.exp(l1);
                double sum = exp0 + exp1;
                double p0 = sum > 0.0d ? exp0 / sum : 0.5d;
                double p1 = sum > 0.0d ? exp1 / sum : 0.5d;
                return new Prediction(p1 > p0 ? 1 : 0, Math.max(p0, p1));
            }
        }
        if (modelOutput instanceof List<?> list && list.size() >= 2) {
            double v0 = toDouble(list.get(0));
            double v1 = toDouble(list.get(1));
            return new Prediction(v1 > v0 ? 1 : 0, Math.max(v0, v1));
        }
        if (modelOutput instanceof Number number) {
            double confidence = number.doubleValue();
            return new Prediction(confidence > 0.5d ? attackClassId : 0, confidence);
        }
        return new Prediction(0, 0.0d);
    }

    private RiskLevel determineRiskLevel(int predictedClass, double confidence) {
        if (predictedClass != attackClassId) {
            return RiskLevel.SAFE;
        }
        if (confidence < thresholds.getOrDefault("low", 0.7d)) {
            return RiskLevel.SAFE;
        }
        if (confidence < thresholds.getOrDefault("medium", 0.85d)) {
            return RiskLevel.LOW;
        }
        if (confidence < thresholds.getOrDefault("high", 0.95d)) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.HIGH;
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0.0d;
        }
    }

    private record Prediction(int predictedClass, double confidence) {
    }
}
