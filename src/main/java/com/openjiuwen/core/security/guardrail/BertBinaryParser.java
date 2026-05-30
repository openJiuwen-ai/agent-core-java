/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BERT binary classification model output parser.
 * 
 * Output has two classes:
 * - Class 0: SAFE (non-attack)
 * - Class 1: ATTACK
 * 
 * To reduce false positives, we use:
 * 1. Predicted class as primary indicator
 * 2. High confidence threshold for attack classification
 * 3. Conservative risk level mapping
 * 
 * Risk level mapping (class=1, attack):
 * - confidence < 0.7: SAFE (too uncertain, avoid false positive)
 * - 0.7 <= confidence < 0.85: LOW
 * - 0.85 <= confidence < 0.95: MEDIUM
 * - confidence >= 0.95: HIGH
 * 
 * When class=0 (safe), always return SAFE regardless of confidence.
 * 
 * Mirrors Python's openjiuwen.core.security.guardrail.context.BertBinaryParser
 */
public class BertBinaryParser implements ModelOutputParser {
    
    private static final Map<String, Double> DEFAULT_THRESHOLDS = new HashMap<>();
    static {
        DEFAULT_THRESHOLDS.put("low", 0.7);
        DEFAULT_THRESHOLDS.put("medium", 0.85);
        DEFAULT_THRESHOLDS.put("high", 0.95);
    }
    
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
        this.thresholds = confidenceThresholds != null ? confidenceThresholds : new HashMap<>(DEFAULT_THRESHOLDS);
        this.attackClassId = attackClassId;
    }
    
    @Override
    public RiskAssessment parse(Object modelOutput) {
        Prediction predicted = extractPrediction(modelOutput);
        int predictedClass = predicted.predictedClass();
        double confidence = predicted.confidence();
        
        RiskLevel riskLevel = determineRiskLevel(predictedClass, confidence);
        boolean hasRisk = riskLevel != RiskLevel.SAFE;
        
        Map<String, Object> details = new HashMap<>();
        details.put("predicted_class", predictedClass);
        details.put("attack_confidence", predictedClass == attackClassId ? confidence : 1 - confidence);
        
        return RiskAssessment.builder()
                .hasRisk(hasRisk)
                .riskLevel(riskLevel)
                .riskType(hasRisk ? riskType : null)
                .confidence(confidence)
                .details(details)
                .build();
    }
    
    /**
     * Extract prediction from various model output formats.
     * 
     * @param modelOutput Raw model output
     * @return prediction with predicted class and confidence
     */
    private Prediction extractPrediction(Object modelOutput) {
        if (modelOutput instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) modelOutput;
            
            // Format 1: {predicted_class: int, confidence: float}
            if (map.containsKey("predicted_class")) {
                int predictedClass = toInt(map.get("predicted_class"));
                double confidence = toDouble(map.containsKey("confidence") ? map.get("confidence") : 0.0);
                return new Prediction(predictedClass, confidence);
            }
            
            // Format 2: {label: int, score/confidence: float}
            if (map.containsKey("label")) {
                int predictedClass = toInt(map.get("label"));
                double confidence = toDouble(map.containsKey("score") ? map.get("score") : (map.containsKey("confidence") ? map.get("confidence") : 0.0));
                return new Prediction(predictedClass, confidence);
            }
            
            // Format 3: {probabilities: [float, float]}
            if (map.containsKey("probabilities")) {
                Object probs = map.get("probabilities");
                if (probs instanceof List && ((List<?>) probs).size() >= 2) {
                    List<?> probList = (List<?>) probs;
                    double p0 = toDouble(probList.get(0));
                    double p1 = toDouble(probList.get(1));
                    int predictedClass = p1 > p0 ? 1 : 0;
                    double confidence = Math.max(p0, p1);
                    return new Prediction(predictedClass, confidence);
                }
            }
            
            // Format 4: {logits: [float, float]}
            if (map.containsKey("logits")) {
                Object logits = map.get("logits");
                if (logits instanceof List && ((List<?>) logits).size() >= 2) {
                    List<?> logitsList = (List<?>) logits;
                    double l0 = toDouble(logitsList.get(0));
                    double l1 = toDouble(logitsList.get(1));
                    double sumExp = Math.exp(l0) + Math.exp(l1);
                    double p0 = sumExp > 0 ? Math.exp(l0) / sumExp : 0.5;
                    double p1 = sumExp > 0 ? Math.exp(l1) / sumExp : 0.5;
                    int predictedClass = p1 > p0 ? 1 : 0;
                    double confidence = Math.max(p0, p1);
                    return new Prediction(predictedClass, confidence);
                }
            }
        }
        
        // Format 5: [float, float] or (float, float)
        if (modelOutput instanceof List && ((List<?>) modelOutput).size() >= 2) {
            List<?> list = (List<?>) modelOutput;
            double v0 = toDouble(list.get(0));
            double v1 = toDouble(list.get(1));
            int predictedClass = v1 > v0 ? 1 : 0;
            double confidence = Math.max(v0, v1);
            return new Prediction(predictedClass, confidence);
        }
        
        // Format 6: single float/int
        if (modelOutput instanceof Number) {
            double confidence = ((Number) modelOutput).doubleValue();
            int predictedClass = confidence > 0.5 ? attackClassId : 0;
            return new Prediction(predictedClass, confidence);
        }
        
        // Default: no prediction
        return new Prediction(0, 0.0);
    }

    private record Prediction(int predictedClass, double confidence) {
    }
    
    private RiskLevel determineRiskLevel(int predictedClass, double confidence) {
        if (predictedClass != attackClassId) {
            return RiskLevel.SAFE;
        }
        
        double lowThreshold = thresholds.getOrDefault("low", 0.7);
        double mediumThreshold = thresholds.getOrDefault("medium", 0.85);
        double highThreshold = thresholds.getOrDefault("high", 0.95);
        
        if (confidence < lowThreshold) {
            return RiskLevel.SAFE;
        }
        if (confidence < mediumThreshold) {
            return RiskLevel.LOW;
        }
        if (confidence < highThreshold) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.HIGH;
    }
    
    private int toInt(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private double toDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
