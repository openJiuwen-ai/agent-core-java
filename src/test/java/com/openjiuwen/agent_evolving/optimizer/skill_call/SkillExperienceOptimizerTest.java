/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

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
        assertEquals(12.0f, invoker.options().getFirst().getTimeout());
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
        assertEquals("fixed", result.patches().getFirst().getContent());
        assertTrue(invoker.prompts().getFirst().contains("Expecting value"));
        assertEquals(20.0f, invoker.options().getFirst().getTimeout());
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
        assertNull(patches.getFirst().getMergeTarget());

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
                .context(Map.of("tool", "bash"))
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
            if (messages != null && !messages.isEmpty() && messages.getFirst() instanceof UserMessage message) {
                Object content = message.getContent();
                return content == null ? "" : String.valueOf(content);
            }
            return String.valueOf(messages);
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
