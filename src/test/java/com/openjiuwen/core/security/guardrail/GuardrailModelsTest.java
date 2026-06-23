/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's guardrail model tests in
 * {@code tests/unit_tests/core/security/guardrail/test_guardrail_models.py}.
 */
class GuardrailModelsTest {

    @Test
    void passReturnsSafeResult() {
        GuardrailResult result = GuardrailResult.pass_();

        assertTrue(result.isSafe());
        assertEquals(RiskLevel.SAFE, result.getRiskLevel());
        assertNull(result.getRiskType());
        assertNull(result.getDetails());
        assertNull(result.getModifiedData());
    }

    @Test
    void passWithDetailsPreservesPayload() {
        Map<String, Object> details = Map.of("scan_time", 0.5d, "tokens_scanned", 100);
        GuardrailResult result = GuardrailResult.pass_(details);

        assertTrue(result.isSafe());
        assertEquals(RiskLevel.SAFE, result.getRiskLevel());
        assertEquals(details, result.getDetails());
    }

    @Test
    void blockReturnsUnsafeResult() {
        GuardrailResult result = GuardrailResult.block(RiskLevel.HIGH, "prompt_injection");

        assertFalse(result.isSafe());
        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        assertEquals("prompt_injection", result.getRiskType());
        assertNull(result.getDetails());
    }

    @Test
    void blockSupportsDetailsAndModifiedData() {
        Map<String, Object> details = Map.of("matched_pattern", "ignore instructions", "confidence", 0.95d);
        Map<String, Object> modified = Map.of("sanitized_text", "***FILTERED***");
        GuardrailResult result = GuardrailResult.block(RiskLevel.CRITICAL, "jailbreak_attempt", details, modified);

        assertFalse(result.isSafe());
        assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        assertEquals("jailbreak_attempt", result.getRiskType());
        assertEquals(details, result.getDetails());
        assertEquals(modified, result.getModifiedData());
    }

    @Test
    void blockWithDetailsPreservesDetails() {
        Map<String, Object> details = Map.of("matched_pattern", "ignore instructions", "confidence", 0.95d);
        GuardrailResult result = GuardrailResult.block(RiskLevel.CRITICAL, "jailbreak_attempt", details);

        assertFalse(result.isSafe());
        assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        assertEquals("jailbreak_attempt", result.getRiskType());
        assertEquals(details, result.getDetails());
    }

    @Test
    void blockWithModifiedDataPreservesModifiedPayload() {
        Map<String, Object> modified = Map.of("sanitized_text", "***FILTERED***");
        GuardrailResult result = GuardrailResult.block(RiskLevel.MEDIUM, "sensitive_data", null, modified);

        assertEquals(modified, result.getModifiedData());
    }

    @Test
    void guardrailResultEqualityMatchesPythonDataclass() {
        GuardrailResult result1 = GuardrailResult.pass_(Map.of("key", "value"));
        GuardrailResult result2 = GuardrailResult.pass_(Map.of("key", "value"));
        GuardrailResult result3 = GuardrailResult.pass_(Map.of("key", "other"));

        assertEquals(result1, result2);
        assertNotEquals(result1, result3);
        assertNotEquals(result1, "not a result");
    }

    @Test
    void guardrailResultInequalityWithDifferentTypes() {
        GuardrailResult result = GuardrailResult.pass_();

        assertNotEquals(result, "not a result");
        assertNotEquals(null, result);
    }

    @Test
    void riskAssessmentSafeAssessmentDefaults() {
        RiskAssessment assessment = new RiskAssessment(false, RiskLevel.SAFE);

        assertFalse(assessment.isHasRisk());
        assertEquals(RiskLevel.SAFE, assessment.getRiskLevel());
        assertNull(assessment.getRiskType());
        assertEquals(0.0d, assessment.getConfidence());
        assertNull(assessment.getDetails());
    }

    @Test
    void riskAssessmentRiskyAssessmentPreservesFields() {
        Map<String, Object> details = Map.of("matched_terms", java.util.List.of("ignore", "system"));
        RiskAssessment assessment = new RiskAssessment(
                true,
                RiskLevel.HIGH,
                "prompt_injection",
                0.85d,
                details);

        assertTrue(assessment.isHasRisk());
        assertEquals(RiskLevel.HIGH, assessment.getRiskLevel());
        assertEquals("prompt_injection", assessment.getRiskType());
        assertEquals(0.85d, assessment.getConfidence());
        assertEquals(details, assessment.getDetails());
    }

    @Test
    void riskAssessmentDefaultsMatchPythonModel() {
        RiskAssessment assessment = new RiskAssessment(true, RiskLevel.LOW);

        assertTrue(assessment.isHasRisk());
        assertEquals(RiskLevel.LOW, assessment.getRiskLevel());
        assertNull(assessment.getRiskType());
        assertEquals(0.0d, assessment.getConfidence());
        assertNull(assessment.getDetails());
    }

    @Test
    void riskAssessmentSupportsAllFieldsAndEquality() {
        Map<String, Object> details = Map.of("scan_id", "123", "model", "security-v1");
        RiskAssessment assessment1 = new RiskAssessment(true, RiskLevel.CRITICAL, "data_leakage", 0.99d, details);
        RiskAssessment assessment2 = new RiskAssessment(true, RiskLevel.CRITICAL, "data_leakage", 0.99d, details);
        RiskAssessment assessment3 = new RiskAssessment(true, RiskLevel.CRITICAL, "other", 0.99d, details);

        assertTrue(assessment1.isHasRisk());
        assertEquals(RiskLevel.CRITICAL, assessment1.getRiskLevel());
        assertEquals("data_leakage", assessment1.getRiskType());
        assertEquals(0.99d, assessment1.getConfidence());
        assertEquals(details, assessment1.getDetails());
        assertEquals(assessment1, assessment2);
        assertNotEquals(assessment1, assessment3);
    }

    @Test
    void riskAssessmentEqualityMatchesPythonDataclass() {
        RiskAssessment assessment1 = new RiskAssessment(true, RiskLevel.MEDIUM, "test");
        RiskAssessment assessment2 = new RiskAssessment(true, RiskLevel.MEDIUM, "test");
        RiskAssessment assessment3 = new RiskAssessment(true, RiskLevel.MEDIUM, "other");

        assertEquals(assessment1, assessment2);
        assertNotEquals(assessment1, assessment3);
    }

    @Test
    void riskLevelValuesMatchPythonEnumValues() {
        assertEquals("safe", RiskLevel.SAFE.value());
        assertEquals("low", RiskLevel.LOW.value());
        assertEquals("medium", RiskLevel.MEDIUM.value());
        assertEquals("high", RiskLevel.HIGH.value());
        assertEquals("critical", RiskLevel.CRITICAL.value());
    }

    @Test
    void riskLevelOrderingMatchesPythonEnumDeclaration() {
        assertEquals(
                java.util.List.of(RiskLevel.SAFE, RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL),
                Arrays.asList(RiskLevel.values()));
    }

    @Test
    void riskLevelFromStringUsesSerializedValue() {
        assertEquals(RiskLevel.SAFE, RiskLevel.fromValue("safe"));
        assertEquals(RiskLevel.HIGH, RiskLevel.fromValue("high"));
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromValue("critical"));
    }

    @Test
    void riskLevelContainsAllSerializedValues() {
        Set<String> values = Arrays.stream(RiskLevel.values()).map(RiskLevel::value).collect(Collectors.toSet());

        assertTrue(values.containsAll(Set.of("safe", "low", "medium", "high", "critical")));
    }

    @Test
    void riskLevelNamesMatchPythonEnumNames() {
        assertEquals("SAFE", RiskLevel.SAFE.name());
        assertEquals("HIGH", RiskLevel.HIGH.name());
        assertEquals("CRITICAL", RiskLevel.CRITICAL.name());
    }

    @Test
    void riskLevelValuePropertyReturnsSerializedValue() {
        assertEquals("safe", RiskLevel.SAFE.value());
        assertEquals("medium", RiskLevel.MEDIUM.value());
    }
}
