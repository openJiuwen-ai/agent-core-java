/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.security.guardrail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GuardrailModels.
 * Mirrors Python's tests/unit_tests/core/security/guardrail/test_guardrail_models.py
 */
class TestGuardrailModels {

    @Nested
    @DisplayName("GuardrailResult tests")
    class TestGuardrailResult {

        @Test
        @DisplayName("test_pass_returns_safe_result")
        void testPassReturnsSafeResult() {
            GuardrailResult result = GuardrailResult.pass();

            assertTrue(result.isSafe());
            assertEquals(RiskLevel.SAFE, result.getRiskLevel());
            assertNull(result.getRiskType());
            assertNull(result.getDetails());
            assertNull(result.getModifiedData());
        }

        @Test
        @DisplayName("test_pass_with_details")
        void testPassWithDetails() {
            Map<String, Object> details = new HashMap<>();
            details.put("scan_time", 0.5);
            details.put("tokens_scanned", 100);

            GuardrailResult result = GuardrailResult.pass(details);

            assertTrue(result.isSafe());
            assertEquals(RiskLevel.SAFE, result.getRiskLevel());
            assertEquals(details, result.getDetails());
        }

        @Test
        @DisplayName("test_block_returns_unsafe_result")
        void testBlockReturnsUnsafeResult() {
            GuardrailResult result = GuardrailResult.block(
                    RiskLevel.HIGH,
                    "prompt_injection",
                    null,
                    null
            );

            assertFalse(result.isSafe());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
            assertEquals("prompt_injection", result.getRiskType());
            assertNull(result.getDetails());
        }

        @Test
        @DisplayName("test_block_with_details")
        void testBlockWithDetails() {
            Map<String, Object> details = new HashMap<>();
            details.put("matched_pattern", "ignore instructions");
            details.put("confidence", 0.95);

            GuardrailResult result = GuardrailResult.block(
                    RiskLevel.CRITICAL,
                    "jailbreak_attempt",
                    details,
                    null
            );

            assertFalse(result.isSafe());
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertEquals("jailbreak_attempt", result.getRiskType());
            assertEquals(details, result.getDetails());
        }

        @Test
        @DisplayName("test_block_with_modified_data")
        void testBlockWithModifiedData() {
            Map<String, Object> modified = new HashMap<>();
            modified.put("sanitized_text", "***FILTERED***");

            GuardrailResult result = GuardrailResult.block(
                    RiskLevel.MEDIUM,
                    "sensitive_data",
                    null,
                    modified
            );

            assertEquals(modified, result.getModifiedData());
        }

        @Test
        @DisplayName("test_guardrail_result_equality")
        void testGuardrailResultEquality() {
            Map<String, Object> details = new HashMap<>();
            details.put("key", "value");

            GuardrailResult result1 = GuardrailResult.pass(details);
            GuardrailResult result2 = GuardrailResult.pass(details);

            Map<String, Object> otherDetails = new HashMap<>();
            otherDetails.put("key", "other");
            GuardrailResult result3 = GuardrailResult.pass(otherDetails);

            assertEquals(result1, result2);
            assertNotEquals(result1, result3);
        }

        @Test
        @DisplayName("test_guardrail_result_inequality")
        void testGuardrailResultInequality() {
            GuardrailResult result = GuardrailResult.pass();

            assertNotEquals(result, "not a result");
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("RiskAssessment tests")
    class TestRiskAssessment {

        @Test
        @DisplayName("test_safe_assessment")
        void testSafeAssessment() {
            RiskAssessment assessment = RiskAssessment.builder()
                    .hasRisk(false)
                    .riskLevel(RiskLevel.SAFE)
                    .build();

            assertFalse(assessment.isHasRisk());
            assertEquals(RiskLevel.SAFE, assessment.getRiskLevel());
            assertNull(assessment.getRiskType());
            assertEquals(0.0, assessment.getConfidence());
            assertNull(assessment.getDetails());
        }

        @Test
        @DisplayName("test_risky_assessment")
        void testRiskyAssessment() {
            Map<String, Object> details = new HashMap<>();
            details.put("matched_terms", Arrays.asList("ignore", "system"));

            RiskAssessment assessment = RiskAssessment.builder()
                    .hasRisk(true)
                    .riskLevel(RiskLevel.HIGH)
                    .riskType("prompt_injection")
                    .confidence(0.85)
                    .details(details)
                    .build();

            assertTrue(assessment.isHasRisk());
            assertEquals(RiskLevel.HIGH, assessment.getRiskLevel());
            assertEquals("prompt_injection", assessment.getRiskType());
            assertEquals(0.85, assessment.getConfidence());
            assertEquals(details, assessment.getDetails());
        }

        @Test
        @DisplayName("test_assessment_default_values")
        void testAssessmentDefaultValues() {
            RiskAssessment assessment = RiskAssessment.builder()
                    .hasRisk(true)
                    .riskLevel(RiskLevel.LOW)
                    .build();

            assertNull(assessment.getRiskType());
            assertEquals(0.0, assessment.getConfidence());
            assertNull(assessment.getDetails());
        }

        @Test
        @DisplayName("test_assessment_equality")
        void testAssessmentEquality() {
            RiskAssessment assessment1 = RiskAssessment.builder()
                    .hasRisk(true)
                    .riskLevel(RiskLevel.MEDIUM)
                    .riskType("test")
                    .build();

            RiskAssessment assessment2 = RiskAssessment.builder()
                    .hasRisk(true)
                    .riskLevel(RiskLevel.MEDIUM)
                    .riskType("test")
                    .build();

            RiskAssessment assessment3 = RiskAssessment.builder()
                    .hasRisk(true)
                    .riskLevel(RiskLevel.MEDIUM)
                    .riskType("other")
                    .build();

            assertEquals(assessment1, assessment2);
            assertNotEquals(assessment1, assessment3);
        }

        @Test
        @DisplayName("test_assessment_with_all_fields")
        void testAssessmentWithAllFields() {
            Map<String, Object> details = new HashMap<>();
            details.put("scan_id", "123");
            details.put("model", "security-v1");

            RiskAssessment assessment = RiskAssessment.builder()
                    .hasRisk(true)
                    .riskLevel(RiskLevel.CRITICAL)
                    .riskType("data_leakage")
                    .confidence(0.99)
                    .details(details)
                    .build();

            assertTrue(assessment.isHasRisk());
            assertEquals(RiskLevel.CRITICAL, assessment.getRiskLevel());
            assertEquals("data_leakage", assessment.getRiskType());
            assertEquals(0.99, assessment.getConfidence());
            assertEquals(details, assessment.getDetails());
        }
    }

    @Nested
    @DisplayName("RiskLevelEnum tests")
    class TestRiskLevelEnum {

        @Test
        @DisplayName("test_risk_level_values")
        void testRiskLevelValues() {
            assertEquals("safe", RiskLevel.SAFE.getValue());
            assertEquals("low", RiskLevel.LOW.getValue());
            assertEquals("medium", RiskLevel.MEDIUM.getValue());
            assertEquals("high", RiskLevel.HIGH.getValue());
            assertEquals("critical", RiskLevel.CRITICAL.getValue());
        }

        @Test
        @DisplayName("test_risk_level_ordering")
        void testRiskLevelOrdering() {
            List<RiskLevel> levels = Arrays.asList(RiskLevel.values());

            assertEquals(RiskLevel.SAFE, levels.get(0));
            assertEquals(RiskLevel.LOW, levels.get(1));
            assertEquals(RiskLevel.MEDIUM, levels.get(2));
            assertEquals(RiskLevel.HIGH, levels.get(3));
            assertEquals(RiskLevel.CRITICAL, levels.get(4));
        }

        @Test
        @DisplayName("test_risk_level_from_string")
        void testRiskLevelFromString() {
            assertEquals(RiskLevel.SAFE, RiskLevel.valueOf("SAFE"));
            assertEquals(RiskLevel.HIGH, RiskLevel.valueOf("HIGH"));
            assertEquals(RiskLevel.CRITICAL, RiskLevel.valueOf("CRITICAL"));
        }

        @Test
        @DisplayName("test_risk_level_contains_all_values")
        void testRiskLevelContainsAllValues() {
            List<String> values = Arrays.stream(RiskLevel.values())
                    .map(RiskLevel::getValue)
                    .collect(Collectors.toList());

            assertTrue(values.contains("safe"));
            assertTrue(values.contains("low"));
            assertTrue(values.contains("medium"));
            assertTrue(values.contains("high"));
            assertTrue(values.contains("critical"));
        }

        @Test
        @DisplayName("test_risk_level_name")
        void testRiskLevelName() {
            assertEquals("SAFE", RiskLevel.SAFE.name());
            assertEquals("HIGH", RiskLevel.HIGH.name());
            assertEquals("CRITICAL", RiskLevel.CRITICAL.name());
        }

        @Test
        @DisplayName("test_risk_level_value_property")
        void testRiskLevelValueProperty() {
            assertEquals("safe", RiskLevel.SAFE.getValue());
            assertEquals("medium", RiskLevel.MEDIUM.getValue());
        }
    }
}
