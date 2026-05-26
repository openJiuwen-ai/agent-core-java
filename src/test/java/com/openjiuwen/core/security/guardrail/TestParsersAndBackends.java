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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ParsersAndBackends.
 * Mirrors Python's tests/unit_tests/core/security/guardrail/test_parsers_and_backends.py
 */
class TestParsersAndBackends {

    @Nested
    @DisplayName("BertBinaryParser tests")
    class TestBertBinaryParser {

        @Test
        @DisplayName("test parse with predicted_class attack")
        void testParseWithPredictedClassAttack() {
            BertBinaryParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            output.put("predicted_class", 1);
            output.put("confidence", 0.95);
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        }

        @Test
        @DisplayName("test parse with predicted_class safe")
        void testParseWithPredictedClassSafe() {
            BertBinaryParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            output.put("predicted_class", 0);
            output.put("confidence", 0.95);
            RiskAssessment result = parser.parse(output);
            assertFalse(result.isHasRisk());
            assertEquals(RiskLevel.SAFE, result.getRiskLevel());
        }

        @Test
        @DisplayName("test parse with label attack")
        void testParseWithLabelAttack() {
            BertBinaryParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            output.put("label", 1);
            output.put("score", 0.97);
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        }

        @Test
        @DisplayName("test parse with logits attack")
        void testParseWithLogitsAttack() {
            BertBinaryParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            output.put("logits", Arrays.asList(-2.0, 5.0));
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        }

        @Test
        @DisplayName("test parse with logits safe")
        void testParseWithLogitsSafe() {
            BertBinaryParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            output.put("logits", Arrays.asList(2.0, 0.5));
            RiskAssessment result = parser.parse(output);
            assertFalse(result.isHasRisk());
            assertEquals(RiskLevel.SAFE, result.getRiskLevel());
        }

        @Test
        @DisplayName("test parse with probabilities attack")
        void testParseWithProbabilitiesAttack() {
            BertBinaryParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            output.put("probabilities", Arrays.asList(0.02, 0.98));
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        }

        @Test
        @DisplayName("test parse with probabilities safe")
        void testParseWithProbabilitiesSafe() {
            BertBinaryParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            output.put("probabilities", Arrays.asList(0.9, 0.1));
            RiskAssessment result = parser.parse(output);
            assertFalse(result.isHasRisk());
            assertEquals(RiskLevel.SAFE, result.getRiskLevel());
        }

        @Test
        @DisplayName("test parse with list attack")
        void testParseWithListAttack() {
            BertBinaryParser parser = new BertBinaryParser();
            List<Double> output = Arrays.asList(0.2, 0.8);
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(0.8, result.getConfidence(), 0.001);
        }

        @Test
        @DisplayName("test parse with list safe")
        void testParseWithListSafe() {
            BertBinaryParser parser = new BertBinaryParser();
            List<Double> output = Arrays.asList(0.8, 0.2);
            RiskAssessment result = parser.parse(output);
            assertFalse(result.isHasRisk());
            assertEquals(RiskLevel.SAFE, result.getRiskLevel());
        }

        @Test
        @DisplayName("test low confidence attack returns safe")
        void testLowConfidenceAttackReturnsSafe() {
            BertBinaryParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            output.put("predicted_class", 1);
            output.put("confidence", 0.6);
            RiskAssessment result = parser.parse(output);
            assertFalse(result.isHasRisk());
            assertEquals(RiskLevel.SAFE, result.getRiskLevel());
        }

        @Test
        @DisplayName("test confidence threshold low")
        void testConfidenceThresholdLow() {
            BertBinaryParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            output.put("predicted_class", 1);
            output.put("confidence", 0.75);
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.LOW, result.getRiskLevel());
        }

        @Test
        @DisplayName("test confidence threshold medium")
        void testConfidenceThresholdMedium() {
            BertBinaryParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            output.put("predicted_class", 1);
            output.put("confidence", 0.9);
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
        }

        @Test
        @DisplayName("test confidence threshold high")
        void testConfidenceThresholdHigh() {
            BertBinaryParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            output.put("predicted_class", 1);
            output.put("confidence", 0.97);
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        }

        @Test
        @DisplayName("test custom thresholds")
        void testCustomThresholds() {
            Map<String, Double> customThresholds = new HashMap<>();
            customThresholds.put("low", 0.8);
            customThresholds.put("medium", 0.9);
            customThresholds.put("high", 0.98);
            BertBinaryParser parser = new BertBinaryParser("attack_detected", customThresholds, 1);
            Map<String, Object> output = new HashMap<>();
            output.put("predicted_class", 1);
            output.put("confidence", 0.85);
            RiskAssessment result = parser.parse(output);
            assertEquals(RiskLevel.LOW, result.getRiskLevel());
        }

        @Test
        @DisplayName("test custom risk type")
        void testCustomRiskType() {
            BertBinaryParser parser = new BertBinaryParser("prompt_injection");
            Map<String, Object> output = new HashMap<>();
            output.put("predicted_class", 1);
            output.put("confidence", 0.95);
            RiskAssessment result = parser.parse(output);
            assertEquals("prompt_injection", result.getRiskType());
        }

        @Test
        @DisplayName("test parse empty output")
        void testParseEmptyOutput() {
            BertBinaryParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            RiskAssessment result = parser.parse(output);
            assertFalse(result.isHasRisk());
            assertEquals(RiskLevel.SAFE, result.getRiskLevel());
        }

        @Test
        @DisplayName("test custom attack class id")
        void testCustomAttackClassId() {
            BertBinaryParser parser = new BertBinaryParser("attack_detected", null, 0);
            Map<String, Object> output = new HashMap<>();
            output.put("predicted_class", 0);
            output.put("confidence", 0.95);
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        }
    }

    @Nested
    @DisplayName("QwenGuardParser tests")
    class TestQwenGuardParser {

        @Test
        @DisplayName("test parse standard format unsafe")
        void testParseStandardFormatUnsafe() {
            QwenGuardParser parser = new QwenGuardParser();
            String output = "Safety: Unsafe\nCategories: Violent";
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
            assertEquals("Violent", result.getRiskType());
            assertEquals("Unsafe", result.getDetails().get("safety"));
            @SuppressWarnings("unchecked")
            List<String> categories = (List<String>) result.getDetails().get("categories");
            assertTrue(categories.contains("Violent"));
        }

        @Test
        @DisplayName("test parse standard format safe")
        void testParseStandardFormatSafe() {
            QwenGuardParser parser = new QwenGuardParser();
            String output = "Safety: Safe\nCategories:";
            RiskAssessment result = parser.parse(output);
            assertFalse(result.isHasRisk());
            assertEquals(RiskLevel.SAFE, result.getRiskLevel());
            assertEquals("Safe", result.getDetails().get("safety"));
        }

        @Test
        @DisplayName("test parse standard format controversial")
        void testParseStandardFormatControversial() {
            QwenGuardParser parser = new QwenGuardParser();
            String output = "Safety: Controversial\nCategories: Political";
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
            assertEquals("Political", result.getRiskType());
        }

        @Test
        @DisplayName("test parse standard format multiple categories")
        void testParseStandardFormatMultipleCategories() {
            QwenGuardParser parser = new QwenGuardParser();
            String output = "Safety: Unsafe\nCategories: Violent, Sexual, Hate";
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
            assertEquals("Violent", result.getRiskType());
            @SuppressWarnings("unchecked")
            List<String> categories = (List<String>) result.getDetails().get("categories");
            assertEquals(3, categories.size());
        }

        @Test
        @DisplayName("test parse dict standard format")
        void testParseDictStandardFormat() {
            QwenGuardParser parser = new QwenGuardParser();
            Map<String, Object> output = new HashMap<>();
            output.put("safety", "Unsafe");
            output.put("categories", Arrays.asList("Violent", "Hate"));
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
            assertEquals("Violent", result.getRiskType());
        }

        @Test
        @DisplayName("test parse dict standard format string categories")
        void testParseDictStandardFormatStringCategories() {
            QwenGuardParser parser = new QwenGuardParser();
            Map<String, Object> output = new HashMap<>();
            output.put("safety", "Unsafe");
            output.put("categories", "Violent, Sexual");
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals("Violent", result.getRiskType());
            @SuppressWarnings("unchecked")
            List<String> categories = (List<String>) result.getDetails().get("categories");
            assertEquals(2, categories.size());
        }

        @Test
        @DisplayName("test parse standard format case insensitive")
        void testParseStandardFormatCaseInsensitive() {
            QwenGuardParser parser = new QwenGuardParser();
            String output = "safety: unsafe\ncategories: violent";
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        }

        @Test
        @DisplayName("test parse legacy full format")
        void testParseLegacyFullFormat() {
            QwenGuardParser parser = new QwenGuardParser();
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("risk_level", "unsafe");
            analysis.put("risk_categories", Arrays.asList("hate_speech"));
            analysis.put("evidence", "Test evidence");
            analysis.put("language", "zh");
            Map<String, Object> output = new HashMap<>();
            output.put("analysis", analysis);
            output.put("decision", "block");
            output.put("version", "qwen3guard-gen-8b-v1.0");
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
            assertEquals("hate_speech", result.getRiskType());
        }

        @Test
        @DisplayName("test parse legacy simple format")
        void testParseLegacySimpleFormat() {
            QwenGuardParser parser = new QwenGuardParser();
            Map<String, Object> output = new HashMap<>();
            output.put("judgment", "Unsafe");
            output.put("reason", "Test reason");
            output.put("language", "zh");
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        }

        @Test
        @DisplayName("test parse legacy api format")
        void testParseLegacyApiFormat() {
            QwenGuardParser parser = new QwenGuardParser();
            Map<String, Object> output = new HashMap<>();
            output.put("severity_level", "unsafe");
            output.put("reason", "Test reason");
            output.put("language", "zh");
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        }

        @Test
        @DisplayName("test parse text with keywords")
        void testParseTextWithKeywords() {
            QwenGuardParser parser = new QwenGuardParser();
            String output = "This content is unsafe and should be blocked";
            RiskAssessment result = parser.parse(output);
            assertTrue(result.isHasRisk());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        }

        @Test
        @DisplayName("test parse fallback")
        void testParseFallback() {
            QwenGuardParser parser = new QwenGuardParser();
            String output = "Unknown format without keywords";
            RiskAssessment result = parser.parse(output);
            assertFalse(result.isHasRisk());
            assertEquals(RiskLevel.SAFE, result.getRiskLevel());
        }

        @Test
        @DisplayName("test custom risk type")
        void testCustomRiskType() {
            QwenGuardParser parser = new QwenGuardParser("content_moderation");
            Map<String, Object> output = new HashMap<>();
            output.put("safety", "Unsafe");
            output.put("categories", Arrays.asList());
            RiskAssessment result = parser.parse(output);
            assertEquals("content_moderation", result.getRiskType());
        }
    }

    @Nested
    @DisplayName("GuardrailContext tests")
    class TestGuardrailContext {

        @Test
        @DisplayName("test get text")
        void testGetText() {
            GuardrailContext ctx = new GuardrailContext(
                    GuardrailContentType.TEXT,
                    "Hello world",
                    "test"
            );
            Optional<String> text = ctx.getText();
            assertTrue(text.isPresent());
            assertEquals("Hello world", text.get());
        }

        @Test
        @DisplayName("test get text non text type")
        void testGetTextNonTextType() {
            GuardrailContext ctx = new GuardrailContext(
                    GuardrailContentType.MESSAGES,
                    Arrays.asList(Map.of("role", "user", "content", "Hello")),
                    "test"
            );
            Optional<String> text = ctx.getText();
            assertFalse(text.isPresent());
        }

        @Test
        @DisplayName("test get messages")
        void testGetMessages() {
            List<Map<String, String>> messages = Arrays.asList(
                    Map.of("role", "user", "content", "Hello")
            );
            GuardrailContext ctx = new GuardrailContext(
                    GuardrailContentType.MESSAGES,
                    messages,
                    "test"
            );
            Optional<List<?>> result = ctx.getMessages();
            assertTrue(result.isPresent());
            assertEquals(messages, result.get());
        }

        @Test
        @DisplayName("test get tool name")
        void testGetToolName() {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tool_name", "search_tool");
            GuardrailContext ctx = new GuardrailContext(
                    GuardrailContentType.TOOL_CALL,
                    Map.of("tool", "search"),
                    "test",
                    metadata
            );
            Optional<String> toolName = ctx.getToolName();
            assertTrue(toolName.isPresent());
            assertEquals("search_tool", toolName.get());
        }

        @Test
        @DisplayName("test builder pattern")
        void testBuilderPattern() {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("key", "value");
            GuardrailContext ctx = GuardrailContext.builder()
                    .contentType(GuardrailContentType.TEXT)
                    .content("Test content")
                    .event("test_event")
                    .metadata(metadata)
                    .build();
            assertEquals(GuardrailContentType.TEXT, ctx.getContentType());
            assertEquals("Test content", ctx.getContent());
            assertEquals("test_event", ctx.getEvent());
            assertTrue(ctx.getMetadata().containsKey("key"));
        }
    }

    @Nested
    @DisplayName("ModelOutputParser interface tests")
    class TestModelOutputParser {

        @Test
        @DisplayName("test BertBinaryParser implements ModelOutputParser")
        void testBertBinaryParserImplementsModelOutputParser() {
            ModelOutputParser parser = new BertBinaryParser();
            Map<String, Object> output = new HashMap<>();
            output.put("predicted_class", 1);
            output.put("confidence", 0.95);
            RiskAssessment result = parser.parse(output);
            assertNotNull(result);
            assertTrue(result.isHasRisk());
        }

        @Test
        @DisplayName("test QwenGuardParser implements ModelOutputParser")
        void testQwenGuardParserImplementsModelOutputParser() {
            ModelOutputParser parser = new QwenGuardParser();
            String output = "Safety: Unsafe\nCategories: Violent";
            RiskAssessment result = parser.parse(output);
            assertNotNull(result);
            assertTrue(result.isHasRisk());
        }
    }

    @Nested
    @DisplayName("RiskLevel tests")
    class TestRiskLevel {

        @Test
        @DisplayName("test risk level values")
        void testRiskLevelValues() {
            assertEquals("safe", RiskLevel.SAFE.getValue());
            assertEquals("low", RiskLevel.LOW.getValue());
            assertEquals("medium", RiskLevel.MEDIUM.getValue());
            assertEquals("high", RiskLevel.HIGH.getValue());
            assertEquals("critical", RiskLevel.CRITICAL.getValue());
        }

        @Test
        @DisplayName("test risk level ordering")
        void testRiskLevelOrdering() {
            RiskLevel[] levels = RiskLevel.values();
            assertEquals(5, levels.length);
            assertEquals(RiskLevel.SAFE, levels[0]);
            assertEquals(RiskLevel.LOW, levels[1]);
            assertEquals(RiskLevel.MEDIUM, levels[2]);
            assertEquals(RiskLevel.HIGH, levels[3]);
            assertEquals(RiskLevel.CRITICAL, levels[4]);
        }
    }

    @Nested
    @DisplayName("RiskAssessment tests")
    class TestRiskAssessment {

        @Test
        @DisplayName("test builder creates assessment")
        void testBuilderCreatesAssessment() {
            Map<String, Object> details = new HashMap<>();
            details.put("key", "value");
            RiskAssessment assessment = RiskAssessment.builder()
                    .hasRisk(true)
                    .riskLevel(RiskLevel.HIGH)
                    .riskType("test_risk")
                    .confidence(0.95)
                    .details(details)
                    .build();
            assertTrue(assessment.isHasRisk());
            assertEquals(RiskLevel.HIGH, assessment.getRiskLevel());
            assertEquals("test_risk", assessment.getRiskType());
            assertEquals(0.95, assessment.getConfidence(), 0.001);
            assertEquals(details, assessment.getDetails());
        }

        @Test
        @DisplayName("test default values")
        void testDefaultValues() {
            RiskAssessment assessment = RiskAssessment.builder()
                    .hasRisk(false)
                    .build();
            assertFalse(assessment.isHasRisk());
            assertEquals(RiskLevel.SAFE, assessment.getRiskLevel());
            assertEquals(0.0, assessment.getConfidence(), 0.001);
        }
    }

    @Nested
    @DisplayName("GuardrailContentType tests")
    class TestGuardrailContentType {

        @Test
        @DisplayName("test content type values")
        void testContentTypeValues() {
            assertEquals("text", GuardrailContentType.TEXT.getValue());
            assertEquals("messages", GuardrailContentType.MESSAGES.getValue());
            assertEquals("tool_call", GuardrailContentType.TOOL_CALL.getValue());
            assertEquals("raw", GuardrailContentType.RAW.getValue());
        }

        @Test
        @DisplayName("test all content types exist")
        void testAllContentTypesExist() {
            GuardrailContentType[] types = GuardrailContentType.values();
            assertEquals(4, types.length);
        }
    }
}
