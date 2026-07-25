/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.ApplyResult;
import com.openjiuwen.agent_evolving.UpdateValue;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.experience.EvolutionContext;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Tests for Skill experience optimizer.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py}
 * and {@code openjiuwen/agent_evolving/optimizer/skill_call/experience_optimizer.py}.</p>
 */
class SkillExperienceOptimizerTest {

    @Test
    void buildConversationSnippetHandlesMixedContentAndToolCalls() {
        List<Map<String, Object>> messages = List.of(
                message("user", List.of("line1", Map.of("text", "line2"))),
                Map.of(
                        "role", "assistant",
                        "content", "",
                        "tool_calls", List.of(Map.of("name", "read_file"), Map.of("function", Map.of("name", "bash")))
                )
        );

        String snippet = SkillExperienceOptimizer.buildConversationSnippet(messages, 30, 300, "cn");

        assertTrue(snippet.contains("[user] line1\nline2"));
        assertTrue(snippet.contains("(tool_calls: read_file, bash)"));
        assertTrue(snippet.contains("无文本"));
    }

    @Test
    void generateRecordsFiltersSkipEmptyAndKeepsTextAndScriptLimits() {
        RecordingInvoker invoker = new RecordingInvoker("""
                [
                  {"action":"skip","skip_reason":"duplicate"},
                  {"action":"append","target":"body","section":"Troubleshooting","content":"A","summary":"sum A","keywords":["a"]},
                  {"action":"append","target":"description","section":"Instructions","content":"B","summary":"sum B"},
                  {"action":"append","target":"body","section":"Examples","content":"C-overflow"},
                  {"action":"append","target":"script","section":"Scripts","content":"print(1)","script_filename":"s.py","script_language":"python","script_purpose":"demo"},
                  {"action":"append","target":"script","section":"Scripts","content":"print(2)"},
                  {"action":"append","target":"body","section":"Examples","content":"   "}
                ]
                """);
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                new Model(invoker),
                "dummy",
                "en",
                new LlmResilience.LLMInvokePolicy(12, 36, 2, 0, true)
        );

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal())));

        assertEquals(3, records.size());
        assertEquals("A", records.get(0).getChange().getContent());
        assertEquals("sum A", records.get(0).getSummary());
        assertEquals("B", records.get(1).getChange().getContent());
        assertEquals(EvolutionTarget.SCRIPT, records.get(2).getChange().getTarget());
        assertEquals(12.0f, invoker.options().get(0).getTimeout());
    }

    @Test
    void generateRetriesWithShorterPromptAfterTimeout() {
        RecordingInvoker invoker = new RecordingInvoker(
                new TimeoutException("request timed out"),
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"A\"}]"
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "cn");

        List<EvolutionRecord> records = optimizer.generateRecords(largeContext());

        assertEquals(1, records.size());
        assertEquals(2, invoker.prompts().size());
        assertTrue(invoker.prompts().get(1).length() < invoker.prompts().get(0).length());
    }

    @Test
    void retryParseUsesFixPromptAndParseError() {
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"fixed\"}]"
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "cn");

        SkillExperienceOptimizer.RetryParseResult result =
                optimizer.retryParse("not json", "original prompt", 1, "Expecting value");

        assertNotNull(result.patches());
        assertEquals("fixed", result.patches().get(0).getContent());
        assertTrue(invoker.prompts().get(0).contains("Expecting value"));
        assertEquals(20.0f, invoker.options().get(0).getTimeout());
    }

    @Test
    void retryParseTruncatedAttemptThreeGivesUpWithoutInvokingLlm() {
        RecordingInvoker invoker = new RecordingInvoker("unused");
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "cn");

        SkillExperienceOptimizer.RetryParseResult result =
                optimizer.retryParse("[{\"action\":\"append\",\"target\":\"body\"", "original", 3, "");

        assertNull(result.patches());
        assertEquals("[{\"action\":\"append\",\"target\":\"body\"", result.retryRaw());
        assertEquals(0, invoker.prompts().size());
    }

    @Test
    void parseHelpersAcceptCodeFenceEmbeddedJsonAndBrokenText() {
        List<EvolutionPatch> patches = SkillExperienceOptimizer.parseLlmResponse("""
                ```json
                [{"action":"append","target":"body","section":"Troubleshooting","content":"A","merge_target":"null",}]
                ```
                """);
        assertNotNull(patches);
        assertEquals(1, patches.size());
        assertNull(patches.get(0).getMergeTarget());

        assertEquals(List.of(1, 2), SkillExperienceOptimizer.extractJson("prefix [1, 2] suffix"));
        assertNull(SkillExperienceOptimizer.extractJson("no json"));
    }

    @Test
    void backwardUsesOnlineContextAndStepReturnsExperienceUpdates() {
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"A\"}]"
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "en");
        TestOperator operator = new TestOperator("skill_experience_skill-a");

        int count = optimizer.bind(
                Map.of(operator.getOperatorId(), operator),
                null,
                Map.of("online_contexts", Map.of("skill-a", defaultContext(List.of(makeSignal()))))
        );
        optimizer.backward(List.of(makeSignal())).toCompletableFuture().join();

        Updates updates = optimizer.step();

        assertEquals(1, count);
        Object value = updates.get("skill_experience_skill-a", Protocols.EXPERIENCES_TARGET);
        assertTrue(value instanceof List<?> list && list.size() == 1);
    }

    @Test
    void updateLlmUpdatesRuntimeReferences() {
        Model oldModel = new Model(new RecordingInvoker("old"));
        Model newModel = new Model(new RecordingInvoker("new"));
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(oldModel, "m1", "cn");

        optimizer.updateLlm(newModel, "m2");

        assertSame(newModel, optimizer.getLlm());
        assertEquals("m2", optimizer.getModelName());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("manifestParityCases")
    void manifestParityCase(String nodeId, Executable executable) throws Throwable {
        executable.execute();
    }

    private static Stream<Arguments> manifestParityCases() {
        return Stream.of(
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestConversationSnippet::test_build_conversation_snippet_limits_messages",
                        SkillExperienceOptimizerTest::assertConversationSnippetLimitsMessages),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestSkillExperienceOptimizerGenerate::"
                        + "test_generate_records_llm_policy_property_returns_configured_policy",
                        SkillExperienceOptimizerTest::assertGenerateRecordsPolicyProperty),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestSkillExperienceOptimizerGenerate::test_generate_returns_empty_when_no_signals",
                        SkillExperienceOptimizerTest::assertGenerateReturnsEmptyWhenNoSignals),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestSkillExperienceOptimizerGenerate::"
                        + "test_generate_reraises_llm_invoke_exception_as_base_error",
                        SkillExperienceOptimizerTest::assertGenerateReraisesLlmInvokeExceptionAsBaseError),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestSkillExperienceOptimizerGenerate::test_generate_reraises_llm_base_error",
                        SkillExperienceOptimizerTest::assertGenerateReraisesLlmBaseError),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestSkillExperienceOptimizerGenerate::test_generate_filters_skip_empty_and_truncates_to_two",
                        SkillExperienceOptimizerTest::assertGenerateFiltersSkipEmptyAndTruncatesToTwo),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestSkillExperienceOptimizerGenerate::test_generate_uses_custom_llm_policy",
                        SkillExperienceOptimizerTest::assertGenerateUsesCustomLlmPolicy),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestSkillExperienceOptimizerGenerate::"
                        + "test_generate_accepts_standard_user_intent_signal_with_explicit_request_context",
                        SkillExperienceOptimizerTest::assertGenerateAcceptsUserIntentSignal),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestParsing::test_normalize_summary_accepts_only_meaningful_strings",
                        SkillExperienceOptimizerTest::assertNormalizeSummary),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestParsing::test_parse_experience_draft_carries_patch_and_summary",
                        SkillExperienceOptimizerTest::assertParseExperienceDraftCarriesPatchAndSummary),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestParsing::test_parse_experience_draft_ignores_summary_for_skip",
                        SkillExperienceOptimizerTest::assertParseExperienceDraftIgnoresSummaryForSkip),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestParsing::test_parse_experience_drafts_supports_json_codeblock_and_fallback",
                        SkillExperienceOptimizerTest::assertParseExperienceDraftsSupportsCodeblockAndFallback),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestParsing::test_parse_experience_drafts_invalid_returns_none",
                        SkillExperienceOptimizerTest::assertParseExperienceDraftsInvalidReturnsNone),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestParsing::test_parse_experience_draft_skip",
                        SkillExperienceOptimizerTest::assertParseExperienceDraftSkip),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestParsing::test_parse_experience_draft_with_script_fields",
                        SkillExperienceOptimizerTest::assertParseExperienceDraftWithScriptFields),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestParsing::test_parse_experience_drafts_with_trailing_comma",
                        SkillExperienceOptimizerTest::assertParseExperienceDraftsWithTrailingComma),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestParsing::test_parse_experience_drafts_with_comments",
                        SkillExperienceOptimizerTest::assertParseExperienceDraftsWithComments),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestSummarizeSkillContent::test_short_content_unchanged",
                        SkillExperienceOptimizerTest::assertShortSkillContentUnchanged),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestSummarizeSkillContent::test_long_content_summarized",
                        SkillExperienceOptimizerTest::assertLongSkillContentSummarized),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestSplitIntoSections::test_splits_on_headings",
                        SkillExperienceOptimizerTest::assertSplitIntoSectionsOnHeadings),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestSplitIntoSections::test_no_headings",
                        SkillExperienceOptimizerTest::assertSplitIntoSectionsWithoutHeadings),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestPreviewSection::test_short_body_unchanged",
                        SkillExperienceOptimizerTest::assertPreviewShortBodyUnchanged),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestPreviewSection::test_long_body_truncated",
                        SkillExperienceOptimizerTest::assertPreviewLongBodyTruncated),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestPreviewSection::test_heading_only",
                        SkillExperienceOptimizerTest::assertPreviewHeadingOnly),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestFixJsonText::test_removes_markdown_fences",
                        SkillExperienceOptimizerTest::assertFixJsonTextRemovesMarkdownFences),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestFixJsonText::test_removes_comments_and_trailing_commas",
                        SkillExperienceOptimizerTest::assertFixJsonTextRemovesCommentsAndTrailingCommas),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestExtractJson::test_direct_parse",
                        SkillExperienceOptimizerTest::assertExtractJsonDirectParse),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestExtractJson::test_with_markdown_fence",
                        SkillExperienceOptimizerTest::assertExtractJsonWithMarkdownFence),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestExtractJson::test_embedded_json_extraction",
                        SkillExperienceOptimizerTest::assertExtractJsonEmbedded),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestExtractJson::test_empty_string",
                        SkillExperienceOptimizerTest::assertExtractJsonEmptyString),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestExtractJson::test_completely_broken",
                        SkillExperienceOptimizerTest::assertExtractJsonCompletelyBroken),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestBuildContext::test_empty_signals",
                        SkillExperienceOptimizerTest::assertBuildContextEmptySignals),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestBuildContext::test_budget_splitting",
                        SkillExperienceOptimizerTest::assertBuildContextBudgetSplitting),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestBuildContext::test_short_signals_no_truncation",
                        SkillExperienceOptimizerTest::assertBuildContextShortSignalsNoTruncation),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestLooksTruncated::test_balanced_not_truncated",
                        SkillExperienceOptimizerTest::assertBalancedJsonNotTruncated),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestLooksTruncated::test_unbalanced_is_truncated",
                        SkillExperienceOptimizerTest::assertUnbalancedJsonIsTruncated),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestLooksTruncated::test_slight_imbalance_not_truncated",
                        SkillExperienceOptimizerTest::assertSlightImbalanceNotTruncated),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestConversationSnippetTruncation::test_long_content_gets_truncated",
                        SkillExperienceOptimizerTest::assertConversationSnippetLongContentGetsTruncated),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestConversationSnippetTruncation::test_recency_bias_last_messages_get_more_budget",
                        SkillExperienceOptimizerTest::assertConversationSnippetRecencyBias),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestRetryParse::test_retry_on_malformed_json_sends_fix_prompt",
                        SkillExperienceOptimizerTest::assertRetryOnMalformedJsonSendsFixPrompt),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestRetryParse::test_retry_on_truncated_uses_original_prompt",
                        SkillExperienceOptimizerTest::assertRetryOnTruncatedUsesOriginalPrompt),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestRetryParse::test_retry_returns_empty_on_double_failure",
                        SkillExperienceOptimizerTest::assertRetryReturnsEmptyOnDoubleFailure),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestRetryParse::test_retry_returns_empty_on_llm_exception",
                        SkillExperienceOptimizerTest::assertRetryReturnsEmptyOnLlmException),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestRetryParse::test_retry_passes_parse_error_to_fix_prompt",
                        SkillExperienceOptimizerTest::assertRetryPassesParseErrorToFixPrompt),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestRetryParse::test_retry_truncated_attempt_3_gives_up",
                        SkillExperienceOptimizerTest::assertRetryTruncatedAttemptThreeGivesUp),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestGenerateRecordsRetry::test_empty_array_does_not_trigger_retry",
                        SkillExperienceOptimizerTest::assertEmptyArrayDoesNotTriggerRetry),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestGenerateRecordsRetry::test_retry_passes_parse_error_in_prompt",
                        SkillExperienceOptimizerTest::assertGenerateRetryPassesParseErrorInPrompt),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestGenerateRecordsRetry::test_progressive_raw_update_on_double_failure",
                        SkillExperienceOptimizerTest::assertProgressiveRawUpdateOnDoubleFailure),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestGenerateRecordsRetry::"
                        + "test_timeout_fallback_prompt_is_preserved_for_truncated_retry_regeneration",
                        SkillExperienceOptimizerTest::assertTimeoutFallbackPromptIsPreserved),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestGenerateRecordsRetry::"
                        + "test_generate_drafts_with_retries_returns_parsed_result_without_repair",
                        SkillExperienceOptimizerTest::assertGenerateDraftsWithRetriesReturnsParsedWithoutRepair),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestGenerateRecordsRetry::"
                        + "test_generate_drafts_with_retries_uses_repair_flow_when_initial_parse_fails",
                        SkillExperienceOptimizerTest::assertGenerateDraftsWithRetriesUsesRepairFlow),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestScriptLimit::test_text_and_script_limits_independent",
                        SkillExperienceOptimizerTest::assertTextAndScriptLimitsIndependent),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestPromptRoleConstraints::test_role_constraint_in_cn_prompt",
                        SkillExperienceOptimizerTest::assertRoleConstraintInCnPrompt),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestPromptRoleConstraints::test_role_constraint_in_en_prompt",
                        SkillExperienceOptimizerTest::assertRoleConstraintInEnPrompt),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestPromptRoleConstraints::test_collaboration_section_in_cn_prompt",
                        SkillExperienceOptimizerTest::assertCollaborationSectionInCnPrompt),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestPromptRoleConstraints::test_collaboration_section_in_en_prompt",
                        SkillExperienceOptimizerTest::assertCollaborationSectionInEnPrompt),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestPromptRoleConstraints::test_prompts_request_summary_field",
                        SkillExperienceOptimizerTest::assertPromptsRequestSummaryField),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestBackwardContextBinding::"
                        + "test_backward_prefers_explicit_online_context_over_operator_state",
                        SkillExperienceOptimizerTest::assertBackwardPrefersExplicitOnlineContext),
                node("tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py::"
                        + "TestBackwardContextBinding::test_backward_raises_clear_error_without_online_context",
                        SkillExperienceOptimizerTest::assertBackwardRaisesClearErrorWithoutOnlineContext)
        );
    }

    private static Arguments node(String nodeId, Executable executable) {
        return Arguments.of(nodeId, executable);
    }

    private static void assertConversationSnippetLimitsMessages() {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(message("user", "m" + i));
        }

        String snippet = SkillExperienceOptimizer.buildConversationSnippet(messages, 2, 300, "en");

        assertFalse(snippet.contains("[user] m0"));
        assertTrue(snippet.contains("[user] m3"));
        assertTrue(snippet.contains("[user] m4"));
    }

    private static void assertGenerateRecordsPolicyProperty() {
        LlmResilience.LLMInvokePolicy policy = noBackoffPolicy(12, 2);
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                new Model(new RecordingInvoker("[]")),
                "dummy",
                "en",
                policy
        );

        assertSame(policy, optimizer.getGenerateRecordsLlmPolicy());
    }

    private static void assertGenerateReturnsEmptyWhenNoSignals() {
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                new Model(new RecordingInvoker("unused")),
                "dummy",
                "cn"
        );

        assertEquals(List.of(), optimizer.generateRecords(defaultContext(List.of())));
    }

    private static void assertGenerateReraisesLlmInvokeExceptionAsBaseError() {
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                new Model(new RecordingInvoker(new RuntimeException("network failed"))),
                "dummy",
                "cn",
                noBackoffPolicy(12, 1)
        );

        BaseError error = assertThrows(BaseError.class,
                () -> optimizer.generateRecords(defaultContext(List.of(makeSignal()))));
        assertEquals(StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_LLM_CALL_EXECUTION_ERROR, error.getStatus());
    }

    private static void assertGenerateReraisesLlmBaseError() {
        BaseError baseError = new BaseError(
                StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED,
                Map.of("error_msg", "network failed")
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                new Model(new RecordingInvoker(baseError)),
                "dummy",
                "cn",
                noBackoffPolicy(12, 1)
        );

        BaseError error = assertThrows(BaseError.class,
                () -> optimizer.generateRecords(defaultContext(List.of(makeSignal()))));
        assertEquals(StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_LLM_CALL_EXECUTION_ERROR, error.getStatus());
    }

    private static void assertGenerateFiltersSkipEmptyAndTruncatesToTwo() {
        RecordingInvoker invoker = new RecordingInvoker("""
                [
                  {"action":"skip","skip_reason":"duplicate"},
                  {"action":"append","target":"body","section":"Troubleshooting",
                   "summary":"When tool calls time out, retry with a shorter prompt.","content":"A","merge_target":null},
                  {"action":"append","target":"description","section":"Instructions",
                   "summary":"Clarify selection wording when users ask for audits.","content":"B","merge_target":null},
                  {"action":"append","target":"body","section":"Examples","content":"C","merge_target":null},
                  {"action":"append","target":"body","section":"Examples","content":"   ","merge_target":null}
                ]
                """);
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "en");

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(
                List.of(makeSignal("s1", "execution_failure"), makeSignal("s2", "execution_failure"))));

        assertEquals(2, records.size());
        assertEquals("A", records.get(0).getChange().getContent());
        assertEquals("When tool calls time out, retry with a shorter prompt.", records.get(0).getSummary());
        assertEquals("B", records.get(1).getChange().getContent());
        assertEquals("Clarify selection wording when users ask for audits.", records.get(1).getSummary());
        assertEquals(150.0f, invoker.options().get(0).getTimeout());
    }

    private static void assertGenerateUsesCustomLlmPolicy() {
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"A\"}]"
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                new Model(invoker),
                "dummy",
                "en",
                noBackoffPolicy(12, 2)
        );

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal("s1", "execution_failure"))));

        assertEquals(1, records.size());
        assertEquals(12.0f, invoker.options().get(0).getTimeout());
    }

    private static void assertGenerateAcceptsUserIntentSignal() {
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Instructions\","
                        + "\"content\":\"Add a clearer intent-handling note.\"}]"
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "en");
        EvolutionSignal signal = EvolutionSignal.builder()
                .signalType("user_intent")
                .section("Instructions")
                .excerpt("Please improve explicit intent handling.")
                .skillName("skill-a")
                .context(Map.of("source", "explicit_request"))
                .build();

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(signal)));

        assertEquals(1, records.size());
        assertEquals("user_intent", records.get(0).getSource());
        assertEquals("Instructions", records.get(0).getChange().getSection());
    }

    private static void assertNormalizeSummary() {
        assertEquals("Use CSV sniffing before parsing.",
                ExperienceDraftParser.normalizeSummary("  Use CSV sniffing before parsing.  "));
        assertNull(ExperienceDraftParser.normalizeSummary(""));
        assertNull(ExperienceDraftParser.normalizeSummary("null"));
        assertNull(ExperienceDraftParser.normalizeSummary(null));
        assertNull(ExperienceDraftParser.normalizeSummary(List.of("not", "a", "summary")));
    }

    private static void assertParseExperienceDraftCarriesPatchAndSummary() {
        ParsedExperienceDraft draft = ExperienceDraftParser.parseExperienceDraft(Map.of(
                "action", "append",
                "target", "body",
                "section", "Troubleshooting",
                "summary", "Check encoding before reading CSV files.",
                "content", "### CSV input checks\n- Validate encoding first."
        ));

        assertNotNull(draft);
        assertEquals("Troubleshooting", draft.getPatch().getSection());
        assertTrue(draft.getPatch().getContent().startsWith("### CSV input checks"));
        assertEquals("Check encoding before reading CSV files.", draft.getSummary());
    }

    private static void assertParseExperienceDraftIgnoresSummaryForSkip() {
        ParsedExperienceDraft draft = ExperienceDraftParser.parseExperienceDraft(Map.of(
                "action", "skip",
                "skip_reason", "duplicate",
                "summary", "unused"
        ));

        assertNotNull(draft);
        assertEquals("skip", draft.getPatch().getAction());
        assertNull(draft.getSummary());
    }

    private static void assertParseExperienceDraftsSupportsCodeblockAndFallback() {
        ExperienceDraftParser.DraftsWithError codeblock = ExperienceDraftParser.parseExperienceDraftsWithError("""
                ```json
                [
                  {"action":"append","target":"body","section":"Troubleshooting","content":"A","merge_target":"null"}
                ]
                ```
                """, SkillExperienceOptimizer::extractJsonWithError);
        assertEquals("", codeblock.lastError());
        assertNotNull(codeblock.drafts());
        assertEquals(1, codeblock.drafts().size());
        assertNull(codeblock.drafts().get(0).getPatch().getMergeTarget());

        ExperienceDraftParser.DraftsWithError mixed = ExperienceDraftParser.parseExperienceDraftsWithError(
                "prefix text {\"action\":\"append\",\"target\":\"invalid\",\"section\":\"NotExist\",\"content\":\"X\"} suffix",
                SkillExperienceOptimizer::extractJsonWithError
        );
        assertEquals("", mixed.lastError());
        assertNotNull(mixed.drafts());
        assertEquals("Troubleshooting", mixed.drafts().get(0).getPatch().getSection());
        assertEquals(EvolutionTarget.BODY, mixed.drafts().get(0).getPatch().getTarget());
    }

    private static void assertParseExperienceDraftsInvalidReturnsNone() {
        ExperienceDraftParser.DraftsWithError drafts = ExperienceDraftParser.parseExperienceDraftsWithError(
                "not json at all",
                SkillExperienceOptimizer::extractJsonWithError
        );

        assertNull(drafts.drafts());
    }

    private static void assertParseExperienceDraftSkip() {
        ParsedExperienceDraft draft = ExperienceDraftParser.parseExperienceDraft(Map.of(
                "action", "skip",
                "skip_reason", "irrelevant"
        ));

        assertNotNull(draft);
        assertEquals("skip", draft.getPatch().getAction());
        assertEquals("irrelevant", draft.getPatch().getSkipReason());
    }

    private static void assertParseExperienceDraftWithScriptFields() {
        ParsedExperienceDraft draft = ExperienceDraftParser.parseExperienceDraft(Map.of(
                "action", "append",
                "target", "script",
                "section", "Scripts",
                "content", "import os",
                "script_filename", "setup.py",
                "script_language", "python",
                "script_purpose", "environment setup"
        ));

        assertNotNull(draft);
        assertEquals(EvolutionTarget.SCRIPT, draft.getPatch().getTarget());
        assertEquals("setup.py", draft.getPatch().getScriptFilename());
        assertEquals("python", draft.getPatch().getScriptLanguage());
        assertEquals("environment setup", draft.getPatch().getScriptPurpose());
    }

    private static void assertParseExperienceDraftsWithTrailingComma() {
        ExperienceDraftParser.DraftsWithError drafts = ExperienceDraftParser.parseExperienceDraftsWithError(
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"fix\",},]",
                SkillExperienceOptimizer::extractJsonWithError
        );

        assertNotNull(drafts.drafts());
        assertEquals(1, drafts.drafts().size());
    }

    private static void assertParseExperienceDraftsWithComments() {
        ExperienceDraftParser.DraftsWithError drafts = ExperienceDraftParser.parseExperienceDraftsWithError("""
                [
                  // this is a comment
                  {"action":"append","target":"body","section":"Troubleshooting","content":"fix"}
                ]
                """, SkillExperienceOptimizer::extractJsonWithError);

        assertNotNull(drafts.drafts());
        assertEquals(1, drafts.drafts().size());
    }

    private static void assertShortSkillContentUnchanged() {
        String raw = "# Skill\nshort content";
        assertEquals(raw, SkillExperienceOptimizer.summarizeSkillContent(raw));
    }

    private static void assertLongSkillContentSummarized() {
        List<String> sections = new ArrayList<>();
        sections.add("# Intro\n" + "a".repeat(500));
        for (int i = 0; i < 10; i++) {
            sections.add("## Section " + i + "\n" + "b".repeat(1000));
        }

        String result = SkillExperienceOptimizer.summarizeSkillContent(String.join("\n", sections), 2000);

        assertTrue(result.length() <= 2100);
        assertTrue(result.contains("# Intro"));
        assertTrue(result.contains("## Section 0"));
        assertTrue(result.contains("以下章节仅保留标题与开头摘要"));
    }

    private static void assertSplitIntoSectionsOnHeadings() {
        List<String> sections = SkillExperienceOptimizer.splitIntoSections("# A\ncontent a\n## B\ncontent b\n### C\ncontent c");

        assertEquals(3, sections.size());
        assertTrue(sections.get(0).startsWith("# A"));
        assertTrue(sections.get(1).startsWith("## B"));
    }

    private static void assertSplitIntoSectionsWithoutHeadings() {
        List<String> sections = SkillExperienceOptimizer.splitIntoSections("just plain text\nno headings");

        assertEquals(1, sections.size());
    }

    private static void assertPreviewShortBodyUnchanged() {
        String section = "## Title\nShort body";
        assertEquals(section, SkillExperienceOptimizer.previewSection(section));
    }

    private static void assertPreviewLongBodyTruncated() {
        String section = "## Title\n" + "x".repeat(500);
        String result = SkillExperienceOptimizer.previewSection(section, 100);

        assertTrue(result.startsWith("## Title"));
        assertTrue(result.endsWith("..."));
        assertTrue(result.length() < section.length());
    }

    private static void assertPreviewHeadingOnly() {
        assertEquals("## Empty", SkillExperienceOptimizer.previewSection("## Empty"));
    }

    private static void assertFixJsonTextRemovesMarkdownFences() {
        assertEquals("[{\"a\": 1}]", SkillExperienceOptimizer.fixJsonText("```json\n[{\"a\": 1}]\n```"));
    }

    private static void assertFixJsonTextRemovesCommentsAndTrailingCommas() {
        String fixed = SkillExperienceOptimizer.fixJsonText("[{\"a\": 1}, // comment\n]");

        assertFalse(fixed.contains("//"));
        assertEquals(List.of(Map.of("a", 1)), SkillExperienceOptimizer.extractJson(fixed));
    }

    private static void assertExtractJsonDirectParse() {
        assertEquals(List.of(1, 2), SkillExperienceOptimizer.extractJson("[1, 2]"));
    }

    private static void assertExtractJsonWithMarkdownFence() {
        assertEquals(Map.of("a", 1), SkillExperienceOptimizer.extractJson("```json\n{\"a\": 1}\n```"));
    }

    private static void assertExtractJsonEmbedded() {
        assertEquals(List.of(Map.of("action", "append")),
                SkillExperienceOptimizer.extractJson("Some text before [{\"action\":\"append\"}] some text after"));
    }

    private static void assertExtractJsonEmptyString() {
        assertNull(SkillExperienceOptimizer.extractJson(""));
        assertNull(SkillExperienceOptimizer.extractJson("   "));
    }

    private static void assertExtractJsonCompletelyBroken() {
        assertNull(SkillExperienceOptimizer.extractJson("no json here at all!!!"));
    }

    private static void assertBuildContextEmptySignals() {
        assertEquals("", SkillExperienceOptimizer.buildContext(List.of()));
    }

    private static void assertBuildContextBudgetSplitting() {
        List<EvolutionSignal> signals = List.of(makeSignal("x".repeat(1000), "a"), makeSignal("y".repeat(1000), "b"));

        String result = SkillExperienceOptimizer.buildContext(signals, 500);

        assertTrue(result.contains("[a]"));
        assertTrue(result.contains("[b]"));
        assertTrue(result.contains("..."));
    }

    private static void assertBuildContextShortSignalsNoTruncation() {
        assertEquals("[err] short", SkillExperienceOptimizer.buildContext(List.of(makeSignal("short", "err"))));
    }

    private static void assertBalancedJsonNotTruncated() {
        assertFalse(SkillExperienceOptimizer.looksTruncated("[{\"a\": 1}]"));
    }

    private static void assertUnbalancedJsonIsTruncated() {
        assertTrue(SkillExperienceOptimizer.looksTruncated("[{\"a\": 1}, {\"b\":"));
    }

    private static void assertSlightImbalanceNotTruncated() {
        assertFalse(SkillExperienceOptimizer.looksTruncated("[{\"a\": 1}"));
    }

    private static void assertConversationSnippetLongContentGetsTruncated() {
        String snippet = SkillExperienceOptimizer.buildConversationSnippet(
                List.of(message("user", "x".repeat(1000))),
                30,
                50,
                "en"
        );

        assertTrue(snippet.contains("truncated"));
        assertTrue(snippet.length() < 1000);
    }

    private static void assertConversationSnippetRecencyBias() {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(message("user", "x".repeat(400)));
        }

        String[] lines = SkillExperienceOptimizer.buildConversationSnippet(messages, 30, 200, "cn").strip().split("\n");

        assertTrue(lines[lines.length - 1].length() > lines[0].length());
    }

    private static void assertRetryOnMalformedJsonSendsFixPrompt() {
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"fixed\"}]"
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "cn");

        SkillExperienceOptimizer.RetryParseResult result =
                optimizer.retryParse("[{\"action\":\"append\" invalid json}]", "original prompt here");

        assertNotNull(result.patches());
        assertEquals(1, result.patches().size());
        assertEquals("fixed", result.patches().get(0).getContent());
        assertTrue(invoker.prompts().get(0).contains("修复")
                || invoker.prompts().get(0).contains("invalid json"));
        assertEquals(20.0f, invoker.options().get(0).getTimeout());
    }

    private static void assertRetryOnTruncatedUsesOriginalPrompt() {
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"action\":\"skip\",\"skip_reason\":\"irrelevant\"}]"
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "cn");

        SkillExperienceOptimizer.RetryParseResult result = optimizer.retryParse(
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"partial",
                "THE ORIGINAL PROMPT"
        );

        assertNotNull(result.patches());
        assertEquals(1, result.patches().size());
        assertEquals("THE ORIGINAL PROMPT", invoker.prompts().get(0));
    }

    private static void assertRetryReturnsEmptyOnDoubleFailure() {
        RecordingInvoker invoker = new RecordingInvoker("still broken");
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "cn");

        SkillExperienceOptimizer.RetryParseResult result = optimizer.retryParse("bad", "p");

        assertNull(result.patches());
        assertEquals("still broken", result.retryRaw());
    }

    private static void assertRetryReturnsEmptyOnLlmException() {
        RecordingInvoker invoker = new RecordingInvoker(new RuntimeException("network"));
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "cn");

        SkillExperienceOptimizer.RetryParseResult result = optimizer.retryParse("bad", "p");

        assertNull(result.patches());
        assertEquals("", result.retryRaw());
    }

    private static void assertRetryPassesParseErrorToFixPrompt() {
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"action\":\"skip\",\"skip_reason\":\"irrelevant\"}]"
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "cn");

        optimizer.retryParse("not json at all", "orig", 1, "Expecting value: line 1 column 1");

        assertTrue(invoker.prompts().get(0).contains("Expecting value: line 1 column 1"));
    }

    private static void assertRetryTruncatedAttemptThreeGivesUp() {
        RecordingInvoker invoker = new RecordingInvoker("unused");
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "cn");
        String truncatedRaw = "[{\"action\":\"append\",\"target\":\"body\"";

        SkillExperienceOptimizer.RetryParseResult result = optimizer.retryParse(truncatedRaw, "orig", 3);

        assertNull(result.patches());
        assertEquals(truncatedRaw, result.retryRaw());
        assertEquals(0, invoker.prompts().size());
    }

    private static void assertEmptyArrayDoesNotTriggerRetry() {
        RecordingInvoker invoker = new RecordingInvoker("[]");
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "cn");

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal())));

        assertEquals(List.of(), records);
        assertEquals(1, invoker.prompts().size());
    }

    private static void assertGenerateRetryPassesParseErrorInPrompt() {
        RecordingInvoker invoker = new RecordingInvoker(
                "not json at all",
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"recovered\"}]"
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                new Model(invoker),
                "dummy",
                "cn",
                noBackoffPolicy(12, 2)
        );

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal())));

        assertEquals(2, invoker.prompts().size());
        assertEquals(1, records.size());
        assertTrue(invoker.prompts().get(1).contains("not json at all"));
    }

    private static void assertProgressiveRawUpdateOnDoubleFailure() {
        RecordingInvoker invoker = new RecordingInvoker(
                "broken1",
                "broken2",
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Examples\",\"content\":\"final\"}]"
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                new Model(invoker),
                "dummy",
                "cn",
                noBackoffPolicy(12, 2)
        );

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal())));

        assertEquals(3, invoker.prompts().size());
        assertEquals(1, records.size());
        assertEquals("final", records.get(0).getChange().getContent());
        assertTrue(invoker.prompts().get(2).contains("严格要求"));
        assertTrue(invoker.prompts().get(2).contains("broken2"));
    }

    private static void assertTimeoutFallbackPromptIsPreserved() {
        RecordingInvoker invoker = new RecordingInvoker(
                new TimeoutException("request timed out"),
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"partial",
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"final\"}]"
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                new Model(invoker),
                "dummy",
                "cn",
                noBackoffPolicy(12, 2)
        );

        List<EvolutionRecord> records = optimizer.generateRecords(largeContext());

        assertEquals(1, records.size());
        assertEquals(3, invoker.prompts().size());
        assertTrue(invoker.prompts().get(1).length() < invoker.prompts().get(0).length());
        assertEquals(invoker.prompts().get(1), invoker.prompts().get(2));
    }

    private static void assertGenerateDraftsWithRetriesReturnsParsedWithoutRepair() {
        RecordingInvoker invoker = new RecordingInvoker("{\"action\":\"skip\",\"skip_reason\":\"duplicate\"}");
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "en");

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal())));

        assertEquals(List.of(), records);
        assertEquals(1, invoker.prompts().size());
    }

    private static void assertGenerateDraftsWithRetriesUsesRepairFlow() {
        RecordingInvoker invoker = new RecordingInvoker(
                "broken-json",
                "[{\"action\":\"skip\",\"skip_reason\":\"duplicate\"}]"
        );
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                new Model(invoker),
                "dummy",
                "en",
                noBackoffPolicy(12, 2)
        );

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal())));

        assertEquals(List.of(), records);
        assertEquals(2, invoker.prompts().size());
        assertTrue(invoker.prompts().get(1).contains("broken-json"));
    }

    private static void assertTextAndScriptLimitsIndependent() {
        RecordingInvoker invoker = new RecordingInvoker("""
                [
                  {"action":"append","target":"body","section":"Troubleshooting","content":"A"},
                  {"action":"append","target":"body","section":"Examples","content":"B"},
                  {"action":"append","target":"body","section":"Instructions","content":"C-overflow"},
                  {"action":"append","target":"script","section":"Scripts","content":"import os",
                   "script_filename":"s.py","script_language":"python","script_purpose":"test"},
                  {"action":"append","target":"script","section":"Scripts","content":"import sys",
                   "script_filename":"s2.py","script_language":"python","script_purpose":"test2"}
                ]
                """);
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(new Model(invoker), "dummy", "en");

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal())));
        List<EvolutionRecord> textRecords = records.stream()
                .filter(record -> !EvolutionTarget.SCRIPT.equals(record.getChange().getTarget()))
                .toList();
        List<EvolutionRecord> scriptRecords = records.stream()
                .filter(record -> EvolutionTarget.SCRIPT.equals(record.getChange().getTarget()))
                .toList();

        assertEquals(2, textRecords.size());
        assertEquals(1, scriptRecords.size());
        assertEquals("A", textRecords.get(0).getChange().getContent());
        assertEquals("B", textRecords.get(1).getChange().getContent());
    }

    private static void assertRoleConstraintInCnPrompt() {
        String prompt = SkillCallTemplates.SKILL_EXPERIENCE_GENERATE_PROMPT.get("cn");

        assertTrue(prompt.contains("角色约束"));
        assertTrue(prompt.contains("演进经验必须遵从 Agent 的角色能力和主要任务目标"));
    }

    private static void assertRoleConstraintInEnPrompt() {
        String prompt = SkillCallTemplates.SKILL_EXPERIENCE_GENERATE_PROMPT.get("en");

        assertTrue(prompt.contains("Role Constraints") || prompt.toLowerCase().contains("role constraints"));
    }

    private static void assertCollaborationSectionInCnPrompt() {
        assertTrue(SkillCallTemplates.SKILL_EXPERIENCE_GENERATE_PROMPT.get("cn").contains("Collaboration"));
    }

    private static void assertCollaborationSectionInEnPrompt() {
        assertTrue(SkillCallTemplates.SKILL_EXPERIENCE_GENERATE_PROMPT.get("en").contains("Collaboration"));
    }

    private static void assertPromptsRequestSummaryField() {
        for (String prompt : SkillCallTemplates.SKILL_EXPERIENCE_GENERATE_PROMPT.values()) {
            assertTrue(prompt.contains("\"summary\""));
            assertTrue(prompt.toLowerCase().contains("summary"));
        }
    }

    private static void assertBackwardPrefersExplicitOnlineContext() {
        CapturingOptimizer optimizer = new CapturingOptimizer();
        TestOperator operator = new TestOperator("skill_experience_skill-a");
        EvolutionSignal signal = EvolutionSignal.builder()
                .signalType("user_intent")
                .section("Instructions")
                .excerpt("please improve")
                .skillName("skill-a")
                .context(Map.of("source", "explicit_request"))
                .build();
        EvolutionContext onlineContext = new EvolutionContext(
                "skill-a",
                List.of(signal),
                "# from context",
                List.of(message("user", "context")),
                List.of(makeRecord("ctx-desc", "desc")),
                List.of(makeRecord("ctx-body", "body")),
                "context query",
                null,
                List.of(makeRecord("ctx-script", "script")),
                Map.of()
        );

        optimizer.bind(
                Map.of(operator.getOperatorId(), operator),
                List.of(Protocols.EXPERIENCES_TARGET),
                Map.of("online_contexts", Map.of("skill-a", onlineContext))
        );
        optimizer.backward(List.of(signal)).toCompletableFuture().join();

        assertSame(onlineContext, optimizer.capturedContext());
        assertEquals("# from context", optimizer.capturedContext().getSkillContent());
        assertEquals(List.of(message("user", "context")), optimizer.capturedContext().getMessages());
        assertEquals("context query", optimizer.capturedContext().getUserQuery());
    }

    private static void assertBackwardRaisesClearErrorWithoutOnlineContext() {
        SkillExperienceOptimizer optimizer = new SkillExperienceOptimizer(
                new Model(new RecordingInvoker("[]")),
                "dummy",
                "en"
        );
        TestOperator operator = new TestOperator("skill_experience_skill-a");
        EvolutionSignal signal = makeSignal("tool timeout", "execution_failure");

        optimizer.bind(
                Map.of(operator.getOperatorId(), operator),
                List.of(Protocols.EXPERIENCES_TARGET),
                Map.of()
        );
        CompletionException exception = assertThrows(CompletionException.class,
                () -> optimizer.backward(List.of(signal)).toCompletableFuture().join());

        assertTrue(exception.getCause() instanceof BaseError);
        BaseError error = (BaseError) exception.getCause();
        assertEquals(StatusCode.TOOLCHAIN_OPTIMIZER_BACKWARD_EXECUTION_ERROR, error.getStatus());
        assertTrue(error.getMessage().contains("online_contexts missing entry for skill skill-a"));
    }

    private static LlmResilience.LLMInvokePolicy noBackoffPolicy(double timeoutSecs, int maxAttempts) {
        return new LlmResilience.LLMInvokePolicy(timeoutSecs, timeoutSecs * Math.max(maxAttempts, 1), maxAttempts, 0, true);
    }

    private static Map<String, Object> message(String role, Object content) {
        return Map.of("role", role, "content", content);
    }

    private static EvolutionSignal makeSignal() {
        return makeSignal("tool timeout", "execution_failure");
    }

    private static EvolutionSignal makeSignal(String excerpt, String signalType) {
        return EvolutionSignal.builder()
                .signalType(signalType)
                .section("Troubleshooting")
                .excerpt(excerpt)
                .skillName("skill-a")
                .context(Map.of("tool_name", "bash"))
                .build();
    }

    private static EvolutionContext defaultContext(List<EvolutionSignal> signals) {
        return new EvolutionContext(
                "skill-a",
                signals,
                "# skill",
                List.of(message("user", "hello")),
                List.of(makeRecord("ev_d1", "desc old")),
                List.of(makeRecord("ev_b1", "body old")),
                "",
                null,
                List.of(),
                Map.of()
        );
    }

    private static EvolutionContext largeContext() {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> longMessage = message("user", "hello ".repeat(400));
        for (int i = 0; i < 12; i++) {
            messages.add(longMessage);
        }
        return new EvolutionContext(
                "skill-a",
                List.of(makeSignal("s1", "execution_failure"), makeSignal("s2", "execution_failure")),
                "# Skill\n" + "content\n".repeat(3000),
                messages,
                List.of(makeRecord("ev_d1", "desc old"), makeRecord("ev_d2", "desc old 2")),
                List.of(makeRecord("ev_b1", "body old"), makeRecord("ev_b2", "body old 2")),
                "query ".repeat(200),
                null,
                List.of(),
                Map.of()
        );
    }

    private static EvolutionRecord makeRecord(String recordId, String content) {
        return EvolutionRecord.builder()
                .id(recordId)
                .source("execution_failure")
                .timestamp("2026-01-01T00:00:00Z")
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

    /**
     * Recording model invoker.
     *
     * <p>Mirrors Python's fake LLM fixtures for
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/experience_optimizer.py} tests.</p>
     */
    private static final class RecordingInvoker implements Model.ModelInvoker {
        private final Queue<Object> outcomes = new ArrayDeque<>();
        private final List<String> prompts = new ArrayList<>();
        private final List<ModelInvokeOptions> options = new ArrayList<>();

        private RecordingInvoker(Object... outcomes) {
            this.outcomes.addAll(List.of(outcomes));
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(
                List<BaseMessage> messages,
                ModelRequestConfig modelConfig,
                ModelClientConfig modelClientConfig,
                ModelInvokeOptions options
        ) {
            this.prompts.add(extractPrompt(messages));
            this.options.add(options);
            Object outcome = this.outcomes.isEmpty() ? new AssistantMessage("") : this.outcomes.remove();
            if (outcome instanceof AssistantMessage message) {
                return CompletableFuture.completedFuture(message);
            }
            if (outcome instanceof Throwable throwable) {
                CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
                failed.completeExceptionally(throwable);
                return failed;
            }
            return CompletableFuture.completedFuture(new AssistantMessage(String.valueOf(outcome)));
        }

        private List<String> prompts() {
            return prompts;
        }

        private List<ModelInvokeOptions> options() {
            return options;
        }

        private static String extractPrompt(List<BaseMessage> messages) {
            if (messages != null && !messages.isEmpty() && messages.get(0) instanceof UserMessage message) {
                Object content = message.getContent();
                return content == null ? "" : String.valueOf(content);
            }
            return String.valueOf(messages);
        }
    }

    /**
     * Optimizer fixture that captures the generated online context.
     *
     * <p>Mirrors Python's monkeypatched {@code generate_records} in
     * {@code tests/unit_tests/agent_evolving/optimizer/skill_call/test_experience_optimizer.py}.</p>
     */
    private static final class CapturingOptimizer extends SkillExperienceOptimizer {
        private EvolutionContext capturedContext;

        private CapturingOptimizer() {
            super(new Model(new RecordingInvoker("[]")), "dummy", "en");
        }

        @Override
        public List<EvolutionRecord> generateRecords(EvolutionContext ctx) {
            this.capturedContext = ctx;
            return List.of(makeRecord("ev_capture", "captured"));
        }

        private EvolutionContext capturedContext() {
            return capturedContext;
        }
    }

    /**
     * Minimal skill operator.
     *
     * <p>Mirrors the operator contract used by Python's
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/experience_optimizer.py}.</p>
     */
    private static final class TestOperator extends Operator {
        private final String operatorId;
        private Object experiences = new ArrayList<>();

        private TestOperator(String operatorId) {
            this.operatorId = operatorId;
        }

        @Override
        public String getOperatorId() {
            return operatorId;
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return Map.of(Protocols.EXPERIENCES_TARGET, new TunableSpec(Protocols.EXPERIENCES_TARGET, "list", "experiences"));
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of(Protocols.EXPERIENCES_TARGET, experiences);
        }

        @Override
        public void setParameter(String target, Object value) {
            if (Protocols.EXPERIENCES_TARGET.equals(target)) {
                this.experiences = value;
            }
        }

        @Override
        public ApplyResult applyUpdate(String target, UpdateValue update) {
            return super.applyUpdate(target, update);
        }

        @Override
        public void loadState(Map<String, Object> state) {
            this.experiences = new LinkedHashMap<>(state);
        }
    }
}
