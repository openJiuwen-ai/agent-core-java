/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for evolution LLM resilience helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.llm_resilience} in
 * {@code openjiuwen/agent_evolving/optimizer/llm_resilience.py}.</p>
 *
 * <p>Focused coverage follows Python's {@code tests.unit_tests.agent_evolving.optimizer.test_llm_resilience}
 * in {@code tests/unit_tests/agent_evolving/optimizer/test_llm_resilience.py}.</p>
 */
class LLMResilienceTest {

    private static final Object NEVER_COMPLETE = new Object();

    @Test
    void retriesWithRetryPromptAfterTimeoutThenSucceeds() throws Exception {
        RecordingInvoker invoker = new RecordingInvoker(
                new TimeoutException("request timed out"),
                new AssistantMessage("{\"ok\": true}")
        );
        Model model = new Model(invoker);

        String result = LlmResilience.invokeTextWithRetry(
                model,
                "test-model",
                "full prompt",
                new LlmResilience.LLMInvokePolicy(5, 10, 2, 0, true),
                "short prompt",
                null,
                null
        );

        assertEquals("{\"ok\": true}", result);
        assertPrompt(invoker.messages().get(0), "full prompt");
        assertPrompt(invoker.messages().get(1), "short prompt");
        assertEquals("test-model", invoker.options().get(0).getModel());
    }

    @Test
    void doesNotUseRetryPromptForNonTimeoutError() {
        RecordingInvoker invoker = new RecordingInvoker(new RuntimeException("boom"));
        Model model = new Model(invoker);

        BaseError error = assertThrows(BaseError.class, () -> LlmResilience.invokeTextWithRetry(
                model,
                "test-model",
                "full prompt",
                new LlmResilience.LLMInvokePolicy(5, 10, 2, 0, true),
                "short prompt",
                null,
                null
        ));

        assertEquals(StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_LLM_CALL_EXECUTION_ERROR, error.getStatus());
        assertEquals("invoke_failed", error.getParams().get("reason"));
        assertEquals(1, error.getParams().get("attempts"));
        assertPrompt(invoker.messages().get(0), "full prompt");
    }

    @Test
    void retriesOnEmptyResponseThenSucceeds() throws Exception {
        RecordingInvoker invoker = new RecordingInvoker(
                new AssistantMessage("   "),
                new AssistantMessage("{\"ok\": true}")
        );
        Model model = new Model(invoker);

        String result = LlmResilience.invokeTextWithRetry(
                model,
                "test-model",
                "hello",
                new LlmResilience.LLMInvokePolicy(5, 10, 2, 0, true),
                null,
                null,
                null
        );

        assertEquals("{\"ok\": true}", result);
        assertEquals(2, invoker.messages().size());
    }

    @Test
    void retriesOnUnusableResponseThenSucceeds() throws Exception {
        RecordingInvoker invoker = new RecordingInvoker(
                new AssistantMessage("not json"),
                new AssistantMessage("{\"ok\": true}")
        );
        Model model = new Model(invoker);

        String result = LlmResilience.invokeTextWithRetry(
                model,
                "test-model",
                "hello",
                new LlmResilience.LLMInvokePolicy(5, 10, 2, 0, true),
                null,
                null,
                raw -> raw.startsWith("{")
        );

        assertEquals("{\"ok\": true}", result);
        assertEquals(2, invoker.messages().size());
    }

    @Test
    void raisesWhenTotalBudgetExceeded() {
        RecordingInvoker invoker = new RecordingInvoker(NEVER_COMPLETE);
        Model model = new Model(invoker);

        long started = System.nanoTime();
        BaseError error = assertThrows(BaseError.class, () -> LlmResilience.invokeTextWithRetry(
                model,
                "test-model",
                "hello",
                new LlmResilience.LLMInvokePolicy(1, 0.01, 2, 0, true),
                null,
                null,
                null
        ));
        double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;

        assertEquals(StatusCode.TOOLCHAIN_EVOLVING_TOOL_CALL_LLM_CALL_EXECUTION_ERROR, error.getStatus());
        assertEquals("total_budget_exceeded", error.getParams().get("reason"));
        assertEquals(1, error.getParams().get("attempts"));
        assertTrue(elapsed < 0.5);
        assertEquals(1, invoker.messages().size());
    }

    @Test
    void invokeTextWithRetryAndPromptReturnsPromptUsed() throws Exception {
        RecordingInvoker invoker = new RecordingInvoker(new AssistantMessage("ok"));
        Model model = new Model(invoker);

        LlmResilience.InvokeResult result = LlmResilience.invokeTextWithRetryAndPrompt(
                model,
                "test-model",
                "full prompt",
                new LlmResilience.LLMInvokePolicy(5, 10, 1, 0, true),
                "short prompt",
                null,
                null
        );

        assertEquals("ok", result.getRaw());
        assertEquals("full prompt", result.getPromptUsed());
    }

    private static void assertPrompt(List<BaseMessage> messages, String expectedPrompt) {
        UserMessage message = assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals(expectedPrompt, message.getContentAsString());
    }

    private static final class RecordingInvoker implements Model.ModelInvoker {

        private final Queue<Object> outcomes = new ArrayDeque<>();
        private final List<List<BaseMessage>> messages = new ArrayList<>();
        private final List<ModelInvokeOptions> options = new ArrayList<>();

        private RecordingInvoker(Object... outcomes) {
            this.outcomes.addAll(List.of(outcomes));
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(
                List<BaseMessage> messages,
                com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig modelConfig,
                com.openjiuwen.core.foundation.llm.schema.ModelClientConfig modelClientConfig,
                ModelInvokeOptions options) {
            this.messages.add(List.copyOf(messages));
            this.options.add(options);

            Object outcome = this.outcomes.isEmpty() ? new AssistantMessage("") : this.outcomes.remove();
            if (outcome == NEVER_COMPLETE) {
                return new CompletableFuture<>();
            }
            if (outcome instanceof AssistantMessage message) {
                return CompletableFuture.completedFuture(message);
            }
            CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
            failed.completeExceptionally((Throwable) outcome);
            return failed;
        }

        private List<List<BaseMessage>> messages() {
            return messages;
        }

        private List<ModelInvokeOptions> options() {
            return options;
        }
    }
}
