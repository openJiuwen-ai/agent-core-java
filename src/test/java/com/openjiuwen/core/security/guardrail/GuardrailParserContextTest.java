/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's parser/context coverage in
 * {@code tests/unit_tests/core/security/guardrail/test_parsers_and_backends.py}.
 */
class GuardrailParserContextTest {

    @Test
    void bertParserSupportsPredictedClassFormat() {
        BertBinaryParser parser = new BertBinaryParser();

        RiskAssessment result = parser.parse(Map.of("predicted_class", 1, "confidence", 0.95d));

        assertTrue(result.isHasRisk());
        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        assertEquals("attack_detected", result.getRiskType());
    }

    @Test
    void bertParserSupportsSafePrediction() {
        BertBinaryParser parser = new BertBinaryParser();

        RiskAssessment result = parser.parse(Map.of("predicted_class", 0, "confidence", 0.95d));

        assertFalse(result.isHasRisk());
        assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    }

    @Test
    void bertParserSupportsLabelFormat() {
        BertBinaryParser parser = new BertBinaryParser();

        RiskAssessment result = parser.parse(Map.of("label", 1, "score", 0.97d));

        assertTrue(result.isHasRisk());
        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    void bertParserSupportsLogitsFormat() {
        BertBinaryParser parser = new BertBinaryParser();

        RiskAssessment result = parser.parse(Map.of("logits", List.of(-2.0d, 5.0d)));

        assertTrue(result.isHasRisk());
        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    void bertParserSupportsProbabilitiesFormat() {
        BertBinaryParser parser = new BertBinaryParser();

        RiskAssessment result = parser.parse(Map.of("probabilities", List.of(0.02d, 0.98d)));

        assertTrue(result.isHasRisk());
        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    void bertParserSupportsListFormat() {
        BertBinaryParser parser = new BertBinaryParser();

        RiskAssessment result = parser.parse(List.of(0.2d, 0.8d));

        assertTrue(result.isHasRisk());
        assertEquals(0.8d, result.getConfidence());
    }

    @Test
    void bertParserRespectsLowThresholdAsSafe() {
        BertBinaryParser parser = new BertBinaryParser();

        RiskAssessment result = parser.parse(Map.of("predicted_class", 1, "confidence", 0.6d));

        assertFalse(result.isHasRisk());
        assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    }

    @Test
    void bertParserRespectsCustomThresholds() {
        BertBinaryParser parser = new BertBinaryParser(
                "attack_detected",
                Map.of("low", 0.8d, "medium", 0.9d, "high", 0.98d),
                1
        );

        RiskAssessment result = parser.parse(Map.of("predicted_class", 1, "confidence", 0.85d));

        assertEquals(RiskLevel.LOW, result.getRiskLevel());
    }

    @Test
    void bertParserSupportsCustomAttackClassId() {
        BertBinaryParser parser = new BertBinaryParser("attack_detected", null, 0);

        RiskAssessment result = parser.parse(Map.of("predicted_class", 0, "confidence", 0.95d));

        assertTrue(result.isHasRisk());
        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    void qwenParserSupportsStandardUnsafeFormat() {
        QwenGuardParser parser = new QwenGuardParser();

        RiskAssessment result = parser.parse("Safety: Unsafe\nCategories: Violent");

        assertTrue(result.isHasRisk());
        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        assertEquals("Violent", result.getRiskType());
    }

    @Test
    void qwenParserSupportsSafeFormat() {
        QwenGuardParser parser = new QwenGuardParser();

        RiskAssessment result = parser.parse("Safety: Safe\nCategories:");

        assertFalse(result.isHasRisk());
        assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    }

    @Test
    void qwenParserSupportsMultipleCategoriesAndCaseInsensitiveKeys() {
        QwenGuardParser parser = new QwenGuardParser();

        RiskAssessment result = parser.parse("safety: unsafe\ncategories: Violent, Sexual, Hate");

        assertTrue(result.isHasRisk());
        assertEquals("Violent", result.getRiskType());
        @SuppressWarnings("unchecked")
        List<String> categories = (List<String>) result.getDetails().get("categories");
        assertEquals(3, categories.size());
    }

    @Test
    void qwenParserSupportsStandardDictFormat() {
        QwenGuardParser parser = new QwenGuardParser();

        RiskAssessment result = parser.parse(Map.of("safety", "Unsafe", "categories", List.of("Violent", "Hate")));

        assertTrue(result.isHasRisk());
        assertEquals("Violent", result.getRiskType());
    }

    @Test
    void qwenParserSupportsLegacyFormats() {
        QwenGuardParser parser = new QwenGuardParser();

        RiskAssessment full = parser.parse(Map.of(
                "analysis", Map.of(
                        "risk_level", "unsafe",
                        "risk_categories", List.of("hate_speech"),
                        "evidence", "Test evidence",
                        "language", "zh"
                ),
                "decision", "block",
                "version", "qwen3guard-gen-8b-v1.0"
        ));
        RiskAssessment simple = parser.parse(Map.of("judgment", "Unsafe", "reason", "Test reason", "language", "zh"));
        RiskAssessment api = parser.parse(Map.of("severity_level", "unsafe", "reason", "Test reason", "language", "zh"));

        assertEquals("hate_speech", full.getRiskType());
        assertEquals(RiskLevel.HIGH, simple.getRiskLevel());
        assertEquals(RiskLevel.HIGH, api.getRiskLevel());
    }

    @Test
    void qwenParserSupportsKeywordFallbackAndCustomRiskType() {
        QwenGuardParser parser = new QwenGuardParser("content_moderation");

        RiskAssessment keyword = parser.parse("This content is unsafe and should be blocked");
        RiskAssessment typed = parser.parse(Map.of("safety", "Unsafe", "categories", List.of()));

        assertTrue(keyword.isHasRisk());
        assertEquals("content_moderation", typed.getRiskType());
    }

    @Test
    void guardrailContextExposesTypedHelpers() {
        GuardrailContext textContext = new GuardrailContext(GuardrailContentType.TEXT, "Hello world", "test");
        GuardrailContext messagesContext = new GuardrailContext(
                GuardrailContentType.MESSAGES,
                List.of(Map.of("role", "user", "content", "Hello")),
                "test"
        );
        GuardrailContext toolContext = new GuardrailContext(
                GuardrailContentType.TOOL_CALL,
                Map.of("tool", "search"),
                "test",
                Map.of("tool_name", "search_tool")
        );

        assertEquals("Hello world", textContext.getText().orElseThrow());
        assertFalse(messagesContext.getText().isPresent());
        assertEquals(1, messagesContext.getMessages().orElseThrow().size());
        assertEquals("search_tool", toolContext.getToolName().orElseThrow());
    }

    @Test
    void guardrailContextBuilderCopiesMetadata() {
        GuardrailContext context = GuardrailContext.builder()
                .contentType(GuardrailContentType.TEXT)
                .content("payload")
                .event("event")
                .metadata(Map.of("key", "value"))
                .build();

        Map<String, Object> metadata = context.getMetadata();
        metadata.put("other", "ignored");

        assertEquals("value", context.getMetadata().get("key"));
        assertFalse(context.getMetadata().containsKey("other"));
        assertInstanceOf(String.class, context.getContent());
    }
}
