/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionContext;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.agent_evolving.signal.EvolutionCategory;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for SkillExperienceOptimizer (skill_call).
 *
 * <p>Mirrors Python's
 * {@code tests.unit_tests.agent_evolving.optimizer.skill_call.test_experience_optimizer}.
 */
class SkillExperienceOptimizerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testBuildConversationSnippetHandlesMixedContent() {
        List<Map<String, Object>> messages = List.of(
                message("user", List.of("line1", Map.of("text", "line2"))),
                Map.of(
                        "role", "assistant",
                        "content", "",
                        "tool_calls", List.of(Map.of("name", "read_file"), Map.of("name", "bash"))
                )
        );

        String snippet = SkillExperienceOptimizer.buildConversationSnippet(messages, 30, 300, "cn");

        assertTrue(snippet.contains("[user] line1\nline2"));
        assertTrue(snippet.contains("(tool_calls: read_file, bash)"));
        assertTrue(snippet.contains("无文本"));
    }

    @Test
    void testBuildConversationSnippetLimitsMessages() {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(message("user", "m" + i));
        }

        String snippet = SkillExperienceOptimizer.buildConversationSnippet(messages, 2, 300, "en");

        assertFalse(snippet.contains("[user] m0"));
        assertTrue(snippet.contains("[user] m3"));
        assertTrue(snippet.contains("[user] m4"));
    }

    @Test
    void testGenerateRecordsLlmPolicyPropertyReturnsConfiguredPolicy() {
        LlmResilience.LLMInvokePolicy policy = new LlmResilience.LLMInvokePolicy(12, 36, 2, 0.0, true);
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(mock(Model.class), "dummy", "en", policy);

        assertSame(policy, optimizer.getGenerateRecordsLlmPolicy());
    }

    @Test
    void testGenerateReturnsEmptyWhenNoSignals() {
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(mock(Model.class), "dummy", "cn");
        EvolutionContext ctx = new EvolutionContext("skill-a", List.of(), "# skill",
                List.of(), List.of(), List.of());

        assertEquals(List.of(), optimizer.generateRecords(ctx));
    }

    @Test
    void testGenerateReturnsEmptyOnLlmException() throws Exception {
        Model llm = llmWith(List.of(new RuntimeException("network failed")), new ArrayList<>(), new ArrayList<>());
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "cn");

        assertEquals(List.of(), optimizer.generateRecords(defaultContext(List.of(makeSignal()))));
    }

    @Test
    void testGenerateFiltersSkipEmptyAndTruncatesToTwo() throws Exception {
        List<String> prompts = new ArrayList<>();
        List<Float> timeouts = new ArrayList<>();
        Model llm = llmWith(List.of("""
                [
                  {"action":"skip","skip_reason":"duplicate"},
                  {"action":"append","target":"body","section":"Troubleshooting","content":"A","merge_target":null},
                  {"action":"append","target":"description","section":"Instructions","content":"B","merge_target":null},
                  {"action":"append","target":"body","section":"Examples","content":"C","merge_target":null},
                  {"action":"append","target":"body","section":"Examples","content":"   ","merge_target":null}
                ]
                """), prompts, timeouts);
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "en");
        EvolutionContext ctx = new EvolutionContext(
                "skill-a",
                List.of(makeSignal("s1"), makeSignal("s2")),
                "# skill",
                List.of(message("user", "hello")),
                List.of(makeRecord("ev_d1", "desc old")),
                List.of(makeRecord("ev_b1", "body old"))
        );

        List<EvolutionRecord> records = optimizer.generateRecords(ctx);

        assertEquals(2, records.size());
        assertEquals("A", records.get(0).getChange().getContent());
        assertEquals("B", records.get(1).getChange().getContent());
        assertEquals(60f, timeouts.get(0));
    }

    @Test
    void testGenerateRetriesWithShorterPromptAfterTimeout() throws Exception {
        List<String> prompts = new ArrayList<>();
        Model llm = llmWith(List.of(
                new TimeoutException("request timed out"),
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"A\"}]"
        ), prompts, new ArrayList<>());
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "cn");
        EvolutionContext ctx = largeContext();

        List<EvolutionRecord> records = optimizer.generateRecords(ctx);

        assertEquals(1, records.size());
        assertEquals(2, prompts.size());
        assertTrue(prompts.get(1).length() < prompts.get(0).length());
    }

    @Test
    void testUpdateLlmUpdatesRuntimeReferences() {
        Model oldLlm = mock(Model.class);
        Model newLlm = mock(Model.class);
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(oldLlm, "m1", "cn");

        optimizer.updateLlm(newLlm, "m2");

        assertSame(newLlm, optimizer.getLlm());
        assertEquals("m2", optimizer.getModelName());
    }

    @Test
    void testGenerateUsesCustomLlmPolicy() throws Exception {
        List<Float> timeouts = new ArrayList<>();
        Model llm = llmWith(List.of(
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"A\"}]"
        ), new ArrayList<>(), timeouts);
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                llm,
                "dummy",
                "en",
                new LlmResilience.LLMInvokePolicy(12, 36, 2, 0.0, true)
        );

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal("s1"))));

        assertEquals(1, records.size());
        assertEquals(12f, timeouts.get(0));
    }

    @Test
    void testParseLlmResponseSupportsJsonCodeblockAndFallback() {
        String codeblock = """
                ```json
                [
                  {"action":"append","target":"body","section":"Troubleshooting","content":"A","merge_target":"null"}
                ]
                ```
                """;
        List<EvolutionPatch> patches = SkillExperienceOptimizer.parseLlmResponse(codeblock);

        assertEquals(1, patches.size());
        assertNull(patches.get(0).getMergeTarget());

        String mixed = "prefix text {\"action\":\"append\",\"target\":\"invalid\","
                + "\"section\":\"NotExist\",\"content\":\"X\"} suffix";
        List<EvolutionPatch> patches2 = SkillExperienceOptimizer.parseLlmResponse(mixed);
        assertEquals(1, patches2.size());
        assertEquals("Troubleshooting", patches2.get(0).getSection());
        assertEquals(EvolutionTarget.BODY, patches2.get(0).getTarget());
    }

    @Test
    void testParseLlmResponseInvalidReturnsNone() {
        assertNull(SkillExperienceOptimizer.parseLlmResponse("not json at all"));
    }

    @Test
    void testParseSinglePatchSkip() {
        EvolutionPatch patch = SkillExperienceOptimizer.parseSinglePatch(
                Map.of("action", "skip", "skip_reason", "irrelevant"));

        assertEquals("skip", patch.getAction());
        assertEquals("irrelevant", patch.getSkipReason());
    }

    @Test
    void testParseSinglePatchWithScriptFields() {
        EvolutionPatch patch = SkillExperienceOptimizer.parseSinglePatch(Map.of(
                "action", "append",
                "target", "script",
                "section", "Scripts",
                "content", "import os",
                "script_filename", "setup.py",
                "script_language", "python",
                "script_purpose", "environment setup"
        ));

        assertEquals(EvolutionTarget.SCRIPT, patch.getTarget());
        assertEquals("setup.py", patch.getScriptFilename());
        assertEquals("python", patch.getScriptLanguage());
        assertEquals("environment setup", patch.getScriptPurpose());
    }

    @Test
    void testParseLlmResponseWithTrailingComma() {
        String raw = "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"fix\",},]";
        List<EvolutionPatch> patches = SkillExperienceOptimizer.parseLlmResponse(raw);

        assertNotNull(patches);
        assertEquals(1, patches.size());
    }

    @Test
    void testParseLlmResponseWithComments() {
        String raw = """
                [
                  // this is a comment
                  {"action":"append","target":"body","section":"Troubleshooting","content":"fix"}
                ]
                """;
        List<EvolutionPatch> patches = SkillExperienceOptimizer.parseLlmResponse(raw);

        assertNotNull(patches);
        assertEquals(1, patches.size());
    }

    @Test
    void testShortContentUnchanged() {
        String raw = "# Skill\nshort content";
        assertEquals(raw, SkillExperienceOptimizer.summarizeSkillContent(raw));
    }

    @Test
    void testLongContentSummarized() {
        StringBuilder raw = new StringBuilder("# Intro\n").append("a".repeat(500));
        for (int i = 0; i < 10; i++) {
            raw.append("\n## Section ").append(i).append("\n").append("b".repeat(1000));
        }

        String result = SkillExperienceOptimizer.summarizeSkillContent(raw.toString(), 2000);

        assertTrue(result.length() <= 2100);
        assertTrue(result.contains("# Intro"));
        assertTrue(result.contains("## Section 0"));
        assertTrue(result.contains("以下章节仅保留标题与开头摘要"));
    }

    @Test
    void testSplitsOnHeadings() {
        String text = "# A\ncontent a\n## B\ncontent b\n### C\ncontent c";
        List<String> sections = SkillExperienceOptimizer.splitIntoSections(text);

        assertEquals(3, sections.size());
        assertTrue(sections.get(0).startsWith("# A"));
        assertTrue(sections.get(1).startsWith("## B"));
    }

    @Test
    void testNoHeadings() {
        String text = "just plain text\nno headings";
        assertEquals(1, SkillExperienceOptimizer.splitIntoSections(text).size());
    }

    @Test
    void testShortBodyUnchanged() {
        String section = "## Title\nShort body";
        assertEquals(section, SkillExperienceOptimizer.previewSection(section));
    }

    @Test
    void testLongBodyTruncated() {
        String section = "## Title\n" + "x".repeat(500);
        String result = SkillExperienceOptimizer.previewSection(section, 100);

        assertTrue(result.startsWith("## Title"));
        assertTrue(result.endsWith("..."));
        assertTrue(result.length() < section.length());
    }

    @Test
    void testHeadingOnly() {
        assertEquals("## Empty", SkillExperienceOptimizer.previewSection("## Empty"));
    }

    @Test
    void testRemovesMarkdownFences() {
        String text = "```json\n[{\"a\": 1}]\n```";
        assertEquals("[{\"a\": 1}]", SkillExperienceOptimizer.fixJsonText(text));
    }

    @Test
    void testRemovesCommentsAndTrailingCommas() throws Exception {
        String text = "[{\"a\": 1}, // comment\n]";
        String fixed = SkillExperienceOptimizer.fixJsonText(text);

        assertFalse(fixed.contains("//"));
        assertEquals(List.of(Map.of("a", 1)), MAPPER.readValue(fixed, List.class));
    }

    @Test
    void testDirectParse() {
        assertEquals(List.of(1, 2), SkillExperienceOptimizer.extractJson("[1, 2]"));
    }

    @Test
    void testWithMarkdownFence() {
        assertEquals(Map.of("a", 1), SkillExperienceOptimizer.extractJson("```json\n{\"a\": 1}\n```"));
    }

    @Test
    void testEmbeddedJsonExtraction() {
        Object result = SkillExperienceOptimizer.extractJson("Some text before [{\"action\":\"append\"}] some text after");
        assertEquals(List.of(Map.of("action", "append")), result);
    }

    @Test
    void testEmptyString() {
        assertNull(SkillExperienceOptimizer.extractJson(""));
        assertNull(SkillExperienceOptimizer.extractJson("   "));
    }

    @Test
    void testCompletelyBroken() {
        assertNull(SkillExperienceOptimizer.extractJson("no json here at all!!!"));
    }

    @Test
    void testEmptySignals() {
        assertEquals("", SkillExperienceOptimizer.buildContext(List.of()));
    }

    @Test
    void testBudgetSplitting() {
        List<EvolutionSignal> signals = List.of(
                makeSignal("x".repeat(1000), "a"),
                makeSignal("y".repeat(1000), "b")
        );

        String result = SkillExperienceOptimizer.buildContext(signals, 500);

        assertTrue(result.contains("[a]"));
        assertTrue(result.contains("[b]"));
        assertTrue(result.contains("..."));
    }

    @Test
    void testShortSignalsNoTruncation() {
        List<EvolutionSignal> signals = List.of(makeSignal("short", "err"));
        assertEquals("[err] short", SkillExperienceOptimizer.buildContext(signals));
    }

    @Test
    void testBalancedNotTruncated() {
        assertFalse(SkillExperienceOptimizer.looksTruncated("[{\"a\": 1}]"));
    }

    @Test
    void testUnbalancedIsTruncated() {
        assertTrue(SkillExperienceOptimizer.looksTruncated("[{\"a\": 1}, {\"b\":"));
    }

    @Test
    void testSlightImbalanceNotTruncated() {
        assertFalse(SkillExperienceOptimizer.looksTruncated("[{\"a\": 1}"));
    }

    @Test
    void testLongContentGetsTruncated() {
        String snippet = SkillExperienceOptimizer.buildConversationSnippet(
                List.of(message("user", "x".repeat(1000))), 30, 50, "en");

        assertTrue(snippet.contains("truncated"));
        assertTrue(snippet.length() < 1000);
    }

    @Test
    void testRecencyBiasLastMessagesGetMoreBudget() {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(message("user", "x".repeat(400)));
        }
        String snippet = SkillExperienceOptimizer.buildConversationSnippet(messages, 30, 200, "cn");
        String[] lines = snippet.strip().split("\n");

        assertTrue(lines[lines.length - 1].length() > lines[0].length());
    }

    @Test
    void testRetryOnMalformedJsonSendsFixPrompt() throws Exception {
        List<String> prompts = new ArrayList<>();
        List<Float> timeouts = new ArrayList<>();
        Model llm = llmWith(List.of(
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"fixed\"}]"
        ), prompts, timeouts);
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "cn");

        SkillExperienceOptimizer.RetryParseResult result =
                optimizer.retryParse("[{\"action\":\"append\" invalid json}]", "original prompt here");

        assertEquals(1, result.patches().size());
        assertEquals("fixed", result.patches().get(0).getContent());
        assertFalse(result.retryRaw().isEmpty());
        assertTrue(prompts.get(0).contains("修复") || prompts.get(0).contains("invalid json"));
        assertEquals(20f, timeouts.get(0));
    }

    @Test
    void testRetryOnTruncatedUsesOriginalPrompt() throws Exception {
        List<String> prompts = new ArrayList<>();
        Model llm = llmWith(List.of("[{\"action\":\"skip\",\"skip_reason\":\"irrelevant\"}]"),
                prompts, new ArrayList<>());
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "cn");
        String truncatedRaw = "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"partial";

        SkillExperienceOptimizer.RetryParseResult result =
                optimizer.retryParse(truncatedRaw, "THE ORIGINAL PROMPT");

        assertEquals(1, result.patches().size());
        assertEquals("THE ORIGINAL PROMPT", prompts.get(0));
    }

    @Test
    void testRetryReturnsEmptyOnDoubleFailure() throws Exception {
        Model llm = llmWith(List.of("still broken"), new ArrayList<>(), new ArrayList<>());
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "cn");

        SkillExperienceOptimizer.RetryParseResult result = optimizer.retryParse("bad", "p");

        assertNull(result.patches());
        assertEquals("still broken", result.retryRaw());
    }

    @Test
    void testRetryReturnsEmptyOnLlmException() throws Exception {
        Model llm = llmWith(List.of(new RuntimeException("network")), new ArrayList<>(), new ArrayList<>());
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "cn");

        SkillExperienceOptimizer.RetryParseResult result = optimizer.retryParse("bad", "p");

        assertNull(result.patches());
        assertEquals("", result.retryRaw());
    }

    @Test
    void testRetryPassesParseErrorToFixPrompt() throws Exception {
        List<String> prompts = new ArrayList<>();
        Model llm = llmWith(List.of("[{\"action\":\"skip\",\"skip_reason\":\"irrelevant\"}]"),
                prompts, new ArrayList<>());
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "cn");

        optimizer.retryParse("not json at all", "orig", 1, "Expecting value: line 1 column 1");

        assertTrue(prompts.get(0).contains("Expecting value: line 1 column 1"));
    }

    @Test
    void testRetryTruncatedAttempt3GivesUp() {
        Model llm = mock(Model.class);
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "cn");

        SkillExperienceOptimizer.RetryParseResult result =
                optimizer.retryParse("[{\"action\":\"append\",\"target\":\"body\"", "orig", 3);

        assertNull(result.patches());
        assertEquals("[{\"action\":\"append\",\"target\":\"body\"", result.retryRaw());
        verifyNoInteractions(llm);
    }

    @Test
    void testEmptyArrayDoesNotTriggerRetry() throws Exception {
        List<String> prompts = new ArrayList<>();
        Model llm = llmWith(List.of("[]"), prompts, new ArrayList<>());
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "cn");

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal())));

        assertEquals(List.of(), records);
        assertEquals(1, prompts.size());
    }

    @Test
    void testRetryPassesParseErrorInPrompt() throws Exception {
        List<String> prompts = new ArrayList<>();
        Model llm = llmWith(List.of(
                "not json at all",
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"recovered\"}]"
        ), prompts, new ArrayList<>());
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "cn");

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal())));

        assertEquals(2, prompts.size());
        assertEquals(1, records.size());
        assertTrue(prompts.get(1).contains("解析错误"));
    }

    @Test
    void testProgressiveRawUpdateOnDoubleFailure() throws Exception {
        List<String> prompts = new ArrayList<>();
        Model llm = llmWith(List.of(
                "broken1",
                "broken2",
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Examples\",\"content\":\"final\"}]"
        ), prompts, new ArrayList<>());
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "cn");

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal())));

        assertEquals(3, prompts.size());
        assertEquals(1, records.size());
        assertEquals("final", records.get(0).getChange().getContent());
        assertTrue(prompts.get(2).contains("严格要求"));
    }

    @Test
    void testTimeoutFallbackPromptIsPreservedForTruncatedRetryRegeneration() throws Exception {
        List<String> prompts = new ArrayList<>();
        Model llm = llmWith(List.of(
                new TimeoutException("request timed out"),
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"partial",
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"final\"}]"
        ), prompts, new ArrayList<>());
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "cn");

        List<EvolutionRecord> records = optimizer.generateRecords(largeContext());

        assertEquals(1, records.size());
        assertEquals(3, prompts.size());
        assertTrue(prompts.get(1).length() < prompts.get(0).length());
        assertEquals(prompts.get(1), prompts.get(2));
    }

    @Test
    void testTextAndScriptLimitsIndependent() throws Exception {
        Model llm = llmWith(List.of("""
                [
                  {"action":"append","target":"body","section":"Troubleshooting","content":"A"},
                  {"action":"append","target":"body","section":"Examples","content":"B"},
                  {"action":"append","target":"body","section":"Instructions","content":"C-overflow"},
                  {"action":"append","target":"script","section":"Scripts","content":"import os","script_filename":"s.py","script_language":"python","script_purpose":"test"},
                  {"action":"append","target":"script","section":"Scripts","content":"import sys","script_filename":"s2.py","script_language":"python","script_purpose":"test2"}
                ]
                """), new ArrayList<>(), new ArrayList<>());
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(llm, "dummy", "en");

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal())));

        List<EvolutionRecord> textRecords = records.stream()
                .filter(r -> !EvolutionTarget.SCRIPT.equals(r.getChange().getTarget()))
                .toList();
        List<EvolutionRecord> scriptRecords = records.stream()
                .filter(r -> EvolutionTarget.SCRIPT.equals(r.getChange().getTarget()))
                .toList();
        assertEquals(2, textRecords.size());
        assertEquals(1, scriptRecords.size());
        assertEquals("A", textRecords.get(0).getChange().getContent());
        assertEquals("B", textRecords.get(1).getChange().getContent());
    }

    @Test
    void testRoleConstraintInCnPrompt() {
        String prompt = SkillExperienceOptimizer.SKILL_EXPERIENCE_GENERATE_PROMPT.get("cn");
        assertTrue(prompt.contains("角色约束"));
        assertTrue(prompt.contains("演进经验必须遵从 Agent 的角色能力和主要任务目标"));
    }

    @Test
    void testRoleConstraintInEnPrompt() {
        String prompt = SkillExperienceOptimizer.SKILL_EXPERIENCE_GENERATE_PROMPT.get("en");
        assertTrue(prompt.contains("Role Constraints") || prompt.toLowerCase().contains("role constraints"));
    }

    @Test
    void testCollaborationSectionInCnPrompt() {
        assertTrue(SkillExperienceOptimizer.SKILL_EXPERIENCE_GENERATE_PROMPT.get("cn").contains("Collaboration"));
    }

    @Test
    void testCollaborationSectionInEnPrompt() {
        assertTrue(SkillExperienceOptimizer.SKILL_EXPERIENCE_GENERATE_PROMPT.get("en").contains("Collaboration"));
    }

    private static Map<String, Object> message(String role, Object content) {
        return Map.of("role", role, "content", content);
    }

    private static EvolutionSignal makeSignal() {
        return makeSignal("tool timeout");
    }

    private static EvolutionSignal makeSignal(String excerpt) {
        return makeSignal(excerpt, "execution_failure");
    }

    private static EvolutionSignal makeSignal(String excerpt, String signalType) {
        return EvolutionSignal.builder()
                .signalType(signalType)
                .evolutionType(EvolutionCategory.SKILL_EXPERIENCE)
                .section("Troubleshooting")
                .excerpt(excerpt)
                .toolName("bash")
                .skillName("skill-a")
                .build();
    }

    private static EvolutionRecord makeRecord(String recordId, String content) {
        return EvolutionRecord.builder()
                .id(recordId)
                .source("execution_failure")
                .timestamp("2026-01-01T00:00:00+00:00")
                .context("ctx")
                .change(EvolutionPatch.builder()
                        .section("Troubleshooting")
                        .action("append")
                        .content(content)
                        .target(EvolutionTarget.BODY)
                        .build())
                .applied(false)
                .build();
    }

    private static EvolutionContext defaultContext(List<EvolutionSignal> signals) {
        return new EvolutionContext(
                "skill-a",
                signals,
                "# skill",
                List.of(message("user", "hello")),
                List.of(),
                List.of()
        );
    }

    private static EvolutionContext largeContext() {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = message("user", "hello ".repeat(400));
        for (int i = 0; i < 12; i++) {
            messages.add(message);
        }
        return new EvolutionContext(
                "skill-a",
                List.of(makeSignal("s1"), makeSignal("s2"), makeSignal("s3")),
                "# Skill\n" + "content\n".repeat(3000),
                messages,
                List.of(makeRecord("ev_d1", "desc old"), makeRecord("ev_d2", "desc old 2")),
                List.of(makeRecord("ev_b1", "body old"), makeRecord("ev_b2", "body old 2")),
                "query ".repeat(200)
        );
    }

    private static Model llmWith(List<Object> results, List<String> prompts, List<Float> timeouts) throws Exception {
        Model llm = mock(Model.class);
        final int[] index = {0};
        Answer<AssistantMessage> answer = invocation -> {
            Object messages = invocation.getArgument(0);
            prompts.add(extractPrompt(messages));
            timeouts.add(invocation.getArgument(8));
            Object result = results.get(Math.min(index[0], results.size() - 1));
            index[0]++;
            if (result instanceof Exception exception) {
                throw exception;
            }
            return new AssistantMessage(String.valueOf(result));
        };
        doAnswer(answer).when(llm).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        return llm;
    }

    private static String extractPrompt(Object messages) {
        if (messages instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof UserMessage message) {
            Object content = message.getContent();
            return content != null ? String.valueOf(content) : "";
        }
        return String.valueOf(messages);
    }
}
