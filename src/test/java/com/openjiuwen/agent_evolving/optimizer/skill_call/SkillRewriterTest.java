/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionLog;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.OngoingStubbing;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for SkillRewriter.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.skill_call.test_skill_rewriter}.
 */
class SkillRewriterTest {

    @Test
    void testInitWithDefaults() throws Exception {
        Model llm = mock(Model.class);
        SkillRewriter rewriter = new SkillRewriter(llm, "gpt-4", null);
        assertSame(llm, readField(rewriter, "llm"));
        assertEquals("gpt-4", readField(rewriter, "model"));
        assertEquals("cn", readField(rewriter, "language"));
    }

    @Test
    void testInitWithLanguage() throws Exception {
        SkillRewriter rewriter = new SkillRewriter(mock(Model.class), "gpt-4", "en");
        assertEquals("en", readField(rewriter, "language"));
    }

    @Test
    void testUpdateLlm() throws Exception {
        SkillRewriter rewriter = new SkillRewriter(mock(Model.class), "m1", "cn");
        Model newLlm = mock(Model.class);
        rewriter.updateLlm(newLlm, "m2");
        assertSame(newLlm, readField(rewriter, "llm"));
        assertEquals("m2", readField(rewriter, "model"));
    }

    @Test
    void testRewriteReturnsNullWhenNoSkillContent() throws Exception {
        Model llm = mock(Model.class);
        SkillRewriter rewriter = new SkillRewriter(llm, "dummy", "cn");
        assertNull(rewriter.rewrite("test-skill", makeStore("", Collections.emptyList()), 0.0, false, ""));
        verifyNoInteractions(llm);
    }

    @Test
    void testRewriteReturnsNullWhenNoEvolutionRecords() throws Exception {
        Model llm = mock(Model.class);
        SkillRewriter rewriter = new SkillRewriter(llm, "dummy", "cn");
        assertNull(rewriter.rewrite("test-skill", makeStore("# Test Skill", Collections.emptyList()),
                0.0, false, ""));
        verifyNoInteractions(llm);
    }

    @Test
    void testRewriteReturnsNullWhenAllRecordsBelowMinScore() throws Exception {
        Model llm = mock(Model.class);
        SkillRewriter rewriter = new SkillRewriter(llm, "dummy", "cn");
        EvolutionStore store = makeStore("# Test Skill", List.of(makeRecord("ev_001", "test content", 0.3,
                EvolutionTarget.BODY, "Troubleshooting", null)));
        assertNull(rewriter.rewrite("test-skill", store, 0.5, false, ""));
        verifyNoInteractions(llm);
    }

    @Test
    void testRewriteReturnsNullWhenAllRecordsSkipped() throws Exception {
        Model llm = mock(Model.class);
        SkillRewriter rewriter = new SkillRewriter(llm, "dummy", "cn");
        EvolutionStore store = makeStore("# Test Skill", List.of(makeRecord("ev_001", "test content", 0.7,
                EvolutionTarget.BODY, "Troubleshooting", "irrelevant")));
        assertNull(rewriter.rewrite("test-skill", store, 0.0, false, ""));
        verifyNoInteractions(llm);
    }

    @Test
    void testRewriteSuccessReturnsResult() throws Exception {
        String original = frontMatterSkill("Some instructions here.");
        String rewritten = frontMatterSkill("Updated instructions with integrated experience.");
        EvolutionStore store = makeStore(original, List.of(makeRecord("ev_001", "New guidance")));
        when(store.deleteRecords("test-skill", List.of("ev_001"))).thenReturn(1);
        SkillRewriter rewriter = new SkillRewriter(mockModelResponses("```markdown\n" + rewritten + "\n```"),
                "dummy", "cn");

        SkillRewriter.SkillRewriteResult result = rewriter.rewrite("test-skill", store, 0.0, false, "");

        assertNotNull(result);
        assertEquals("test-skill", result.getSkillName());
        assertEquals(original, result.getOriginalContent());
        assertEquals(rewritten.strip(), result.getRewrittenContent());
        assertTrue(result.getConsumedRecordIds().contains("ev_001"));
        assertEquals(1, result.getRecordsCleaned());
        verify(store).writeSkillContent("test-skill", rewritten.strip());
        verify(store).deleteRecords("test-skill", List.of("ev_001"));
    }

    @Test
    void testRewriteDryRunNoSideEffects() throws Exception {
        EvolutionStore store = makeStore("# Test Skill\n\nOriginal content.", List.of(makeRecord("ev_001")));
        SkillRewriter rewriter = new SkillRewriter(mockModelResponses("```markdown\n# Test Skill\n\nUpdated content.\n```"),
                "dummy", "cn");

        SkillRewriter.SkillRewriteResult result = rewriter.rewrite("test-skill", store, 0.0, true, "");

        assertNotNull(result);
        assertEquals(0, result.getRecordsCleaned());
        verify(store, never()).writeSkillContent(any(), any());
        verify(store, never()).deleteRecords(any(), any());
    }

    @Test
    void testRewriteFiltersByMinScore() throws Exception {
        EvolutionStore store = makeStore("# Test Skill", List.of(
                makeRecord("ev_high", "high", 0.8, EvolutionTarget.BODY, "Troubleshooting", null),
                makeRecord("ev_low", "low", 0.3, EvolutionTarget.BODY, "Troubleshooting", null)));
        when(store.deleteRecords(eq("test-skill"), anyList())).thenReturn(1);
        SkillRewriter rewriter = new SkillRewriter(mockModelResponses("```markdown\n# Test Skill\n\nUpdated.\n```"),
                "dummy", "cn");

        SkillRewriter.SkillRewriteResult result = rewriter.rewrite("test-skill", store, 0.5, false, "");

        assertNotNull(result);
        assertEquals(List.of("ev_high"), result.getConsumedRecordIds());
    }

    @Test
    void testRewritePreservesFrontMatter() throws Exception {
        String original = frontMatterSkill("Content here.");
        String rewritten = frontMatterSkill("Updated content.");
        SkillRewriter rewriter = new SkillRewriter(mockModelResponses("```markdown\n" + rewritten + "\n```"),
                "dummy", "cn");

        SkillRewriter.SkillRewriteResult result = rewriter.rewrite("test-skill",
                makeStore(original, List.of(makeRecord("ev_001"))), 0.0, false, "");

        assertNotNull(result);
        assertTrue(result.getRewrittenContent().startsWith("---"));
    }

    @Test
    void testRewriteFailsValidationWhenFrontMatterMissing() throws Exception {
        String original = "---\nname: my-skill\n---\n\n# My Skill";
        String rewritten = "# My Skill\n\nNo front matter here.";
        SkillRewriter rewriter = new SkillRewriter(mockModelResponses("```markdown\n" + rewritten + "\n```"),
                "dummy", "cn");

        assertNull(rewriter.rewrite("test-skill",
                makeStore(original, List.of(makeRecord("ev_001"))), 0.0, false, ""));
    }

    @Test
    void testRewriteReturnsNullOnLlmException() throws Exception {
        Model llm = mock(Model.class);
        whenInvoke(llm).thenThrow(new RuntimeException("network error"));
        SkillRewriter rewriter = new SkillRewriter(llm, "dummy", "cn");

        assertNull(rewriter.rewrite("test-skill",
                makeStore("# Test Skill", List.of(makeRecord("ev_001"))), 0.0, false, ""));
    }

    @Test
    void testRewriteReturnsNullOnUnparseableOutput() throws Exception {
        Model llm = mockModelResponses("not valid markdown output");
        SkillRewriter rewriter = new SkillRewriter(llm, "dummy", "cn");

        assertNull(rewriter.rewrite("test-skill",
                makeStore("# Test Skill", List.of(makeRecord("ev_001"))), 0.0, false, ""));
        verifyInvokeCount(llm, 2);
    }

    @Test
    void testRewriteRetryOnMalformedOutput() throws Exception {
        Model llm = mockModelResponses("not markdown", "```markdown\n# Test Skill\n\nFixed.\n```");
        SkillRewriter rewriter = new SkillRewriter(llm, "dummy", "cn");

        SkillRewriter.SkillRewriteResult result = rewriter.rewrite("test-skill",
                makeStore("# Test Skill", List.of(makeRecord("ev_001"))), 0.0, false, "");

        assertNotNull(result);
        verifyInvokeCount(llm, 2);
    }

    @Test
    void testRewriteReturnsNullWhenRetryAlsoFails() throws Exception {
        Model llm = mockModelResponses("still not valid");
        SkillRewriter rewriter = new SkillRewriter(llm, "dummy", "cn");

        assertNull(rewriter.rewrite("test-skill",
                makeStore("# Test Skill", List.of(makeRecord("ev_001"))), 0.0, false, ""));
        verifyInvokeCount(llm, 2);
    }

    @Test
    void testRewriteFailsWhenContentTooShort() throws Exception {
        String original = "# Test Skill\n\n" + "x".repeat(1000);
        String rewritten = "# Test";
        SkillRewriter rewriter = new SkillRewriter(mockModelResponses("```markdown\n" + rewritten + "\n```"),
                "dummy", "cn");

        assertNull(rewriter.rewrite("test-skill",
                makeStore(original, List.of(makeRecord("ev_001"))), 0.0, false, ""));
    }

    @Test
    void testRewriteFailsWhenNoHeadings() throws Exception {
        String original = "# Test Skill\n\nContent.";
        String rewritten = "Just plain text without any headings.";
        SkillRewriter rewriter = new SkillRewriter(mockModelResponses("```markdown\n" + rewritten + "\n```"),
                "dummy", "cn");

        assertNull(rewriter.rewrite("test-skill",
                makeStore(original, List.of(makeRecord("ev_001"))), 0.0, false, ""));
    }

    @Test
    void testRewritePassesUserQueryIntoPrompt() throws Exception {
        Model llm = mockModelResponses("```markdown\n# Test Skill\n\nUpdated.\n```");
        SkillRewriter rewriter = new SkillRewriter(llm, "dummy", "en");

        assertNotNull(rewriter.rewrite("test-skill",
                makeStore("# Test Skill", List.of(makeRecord("ev_001"))), 0.0, true, "prefer concise guidance"));

        ArgumentCaptor<Object> messages = ArgumentCaptor.forClass(Object.class);
        verify(llm).invoke(messages.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        UserMessage message = (UserMessage) ((List<?>) messages.getValue()).get(0);
        assertTrue(String.valueOf(message.getContent()).contains("prefer concise guidance"));
    }

    @Test
    void testRewriteReturnsNullWhenWriteFails() throws Exception {
        EvolutionStore store = makeStore("# Test Skill", List.of(makeRecord("ev_001")));
        when(store.writeSkillContent(any(), any())).thenReturn(false);
        SkillRewriter rewriter = new SkillRewriter(mockModelResponses("```markdown\n# Test Skill\n\nUpdated.\n```"),
                "dummy", "cn");

        assertNull(rewriter.rewrite("test-skill", store, 0.0, false, ""));
        verify(store, never()).deleteRecords(any(), any());
    }

    @Test
    void testFormatExperiencesBySection() {
        List<EvolutionRecord> records = List.of(
                makeRecord("ev_001", "Body content A", 0.8, EvolutionTarget.BODY, "Troubleshooting", null),
                makeRecord("ev_002", "Body content B", 0.6, EvolutionTarget.BODY, "Troubleshooting", null),
                makeRecord("ev_003", "Desc content", 0.9, EvolutionTarget.DESCRIPTION, "Instructions", null));
        SkillRewriter rewriter = new SkillRewriter(mock(Model.class), "dummy", "cn");

        String result = rewriter.formatExperiencesBySection(records);

        assertTrue(result.contains("body / Troubleshooting"));
        assertTrue(result.contains("description / Instructions"));
        assertTrue(result.contains("ev_001"));
        assertTrue(result.contains("ev_002"));
        assertTrue(result.contains("ev_003"));
        assertTrue(result.indexOf("ev_001") < result.indexOf("ev_002"));
    }

    @Test
    void testFormatExperiencesEmpty() {
        SkillRewriter rewriter = new SkillRewriter(mock(Model.class), "dummy", "cn");
        assertTrue(rewriter.formatExperiencesBySection(Collections.emptyList()).contains("无有效经验记录"));
    }

    @Test
    void testExtractFromMarkdownCodeBlock() {
        assertEquals("# Content\n\nText.",
                SkillRewriter.extractMarkdown("```markdown\n# Content\n\nText.\n```"));
    }

    @Test
    void testExtractFromGenericCodeBlock() {
        assertEquals("# Content\n\nText.",
                SkillRewriter.extractMarkdown("```\n# Content\n\nText.\n```"));
    }

    @Test
    void testExtractRawWhenStartsWithFrontMatter() {
        String raw = "---\nname: test\n---\n\n# Content";
        assertEquals(raw, SkillRewriter.extractMarkdown(raw));
    }

    @Test
    void testExtractReturnsNullForInvalid() {
        assertNull(SkillRewriter.extractMarkdown("just plain text without markdown"));
    }

    @Test
    void testValidateOutputAcceptsValidMarkdown() {
        assertTrue(SkillRewriter.validateOutput("# Test Skill\n\nContent.",
                "# Test Skill\n\nUpdated content."));
    }

    @Test
    void testValidateOutputRejectsMissingFrontMatter() {
        assertFalse(SkillRewriter.validateOutput("---\nname: test\n---\n\n# Test",
                "# Test\n\nNo front matter"));
    }

    @Test
    void testGenerateSummaryCn() {
        List<EvolutionRecord> records = List.of(
                makeRecord("ev_001", "content", 0.7, EvolutionTarget.BODY, "Troubleshooting", null),
                makeRecord("ev_002", "content", 0.7, EvolutionTarget.DESCRIPTION, "Instructions", null));
        SkillRewriter rewriter = new SkillRewriter(mock(Model.class), "dummy", "cn");

        String summary = rewriter.generateSummary(records, "Line 1\nLine 2", "Line 1\nLine 2\nLine 3");

        assertTrue(summary.contains("2 条"));
        assertTrue(summary.contains("body"));
        assertTrue(summary.contains("description"));
        assertTrue(summary.contains("2 -> 3"));
    }

    @Test
    void testGenerateSummaryEn() {
        SkillRewriter rewriter = new SkillRewriter(mock(Model.class), "dummy", "en");

        String summary = rewriter.generateSummary(List.of(makeRecord("ev_001")),
                "Line 1", "Line 1\nLine 2");

        assertTrue(summary.contains("1 experience"));
        assertTrue(summary.contains("body"));
        assertTrue(summary.contains("1 -> 2"));
    }

    @Test
    void testPromptsDefinedForCnAndEn() {
        assertTrue(SkillRewriterPrompts.SKILL_REWRITE_PROMPT_CN.contains("{skill_content}"));
        assertTrue(SkillRewriterPrompts.SKILL_REWRITE_PROMPT_CN.contains("{experiences_by_section}"));
        assertTrue(SkillRewriterPrompts.SKILL_REWRITE_PROMPT_CN.contains("{user_query}"));
        assertTrue(SkillRewriterPrompts.SKILL_REWRITE_PROMPT_EN.contains("{user_query}"));
        assertSame(SkillRewriterPrompts.SKILL_REWRITE_PROMPT_CN, SkillRewriterPrompts.getPrompt("cn"));
        assertSame(SkillRewriterPrompts.SKILL_REWRITE_PROMPT_EN, SkillRewriterPrompts.getPrompt("en"));
    }

    @Test
    void testRetryPromptsDefined() {
        assertTrue(SkillRewriterPrompts.RETRY_PROMPT_CN.contains("{broken_preview}"));
        assertTrue(SkillRewriterPrompts.RETRY_PROMPT_EN.contains("{broken_preview}"));
        assertSame(SkillRewriterPrompts.RETRY_PROMPT_CN, SkillRewriterPrompts.getRetryPrompt("cn"));
        assertSame(SkillRewriterPrompts.RETRY_PROMPT_EN, SkillRewriterPrompts.getRetryPrompt("en"));
    }

    private static EvolutionStore makeStore(String skillContent, List<EvolutionRecord> entries) {
        EvolutionStore store = mock(EvolutionStore.class);
        when(store.readSkillContent("test-skill")).thenReturn(skillContent);
        when(store.loadEvolutionLog("test-skill")).thenReturn(EvolutionLog.builder()
                .skillId("test-skill")
                .entries(entries)
                .build());
        when(store.writeSkillContent(any(), any())).thenReturn(true);
        when(store.deleteRecords(any(), any())).thenReturn(0);
        return store;
    }

    private static Model mockModelResponses(String... responses) throws Exception {
        Model llm = mock(Model.class);
        OngoingStubbing<AssistantMessage> stubbing = whenInvoke(llm);
        for (String response : responses) {
            stubbing = stubbing.thenReturn(new AssistantMessage(response));
        }
        return llm;
    }

    private static OngoingStubbing<AssistantMessage> whenInvoke(Model llm) throws Exception {
        return when(llm.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()));
    }

    private static void verifyInvokeCount(Model llm, int count) throws Exception {
        verify(llm, times(count)).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static Object readField(SkillRewriter rewriter, String name) throws Exception {
        Field field = SkillRewriter.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(rewriter);
    }

    private static EvolutionRecord makeRecord(String recordId) {
        return makeRecord(recordId, "test content", 0.7, EvolutionTarget.BODY, "Troubleshooting", null);
    }

    private static EvolutionRecord makeRecord(String recordId, String content) {
        return makeRecord(recordId, content, 0.7, EvolutionTarget.BODY, "Troubleshooting", null);
    }

    private static EvolutionRecord makeRecord(String recordId, String content, double score,
                                              EvolutionTarget target, String section, String skipReason) {
        return EvolutionRecord.builder()
                .id(recordId)
                .source("execution_failure")
                .timestamp("2026-01-01T00:00:00+00:00")
                .context("test context")
                .change(EvolutionPatch.builder()
                        .section(section)
                        .action("append")
                        .content(content)
                        .target(target)
                        .skipReason(skipReason)
                        .build())
                .applied(false)
                .score(score)
                .build();
    }

    private static String frontMatterSkill(String body) {
        return """
                ---
                name: test-skill
                description: A test skill
                ---

                # Test Skill

                %s
                """.formatted(body);
    }
}
