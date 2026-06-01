package com.openjiuwen.agent_evolving.optimizer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.OngoingStubbing;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for evolution LLM resilience helpers.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.test_llm_resilience}.</p>
 */
class LLMResilienceTest {

    @Test
    void retriesWithRetryPromptAfterTimeoutThenSucceeds() throws Exception {
        Model model = mock(Model.class);
        whenInvoke(model)
                .thenThrow(new TimeoutException("request timed out"))
                .thenReturn(new AssistantMessage("{\"ok\": true}"));

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
        List<Object> prompts = capturedMessages(model, 2);
        assertPrompt(prompts.get(0), "full prompt");
        assertPrompt(prompts.get(1), "short prompt");
    }

    @Test
    void doesNotUseRetryPromptForNonTimeoutError() throws Exception {
        Model model = mock(Model.class);
        whenInvoke(model).thenThrow(new RuntimeException("boom"));

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
        List<Object> prompts = capturedMessages(model, 1);
        assertPrompt(prompts.getFirst(), "full prompt");
    }

    @Test
    void retriesOnEmptyResponseThenSucceeds() throws Exception {
        Model model = mock(Model.class);
        whenInvoke(model)
                .thenReturn(new AssistantMessage("   "))
                .thenReturn(new AssistantMessage("{\"ok\": true}"));

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
        capturedMessages(model, 2);
    }

    @Test
    void retriesOnUnusableResponseThenSucceeds() throws Exception {
        Model model = mock(Model.class);
        whenInvoke(model)
                .thenReturn(new AssistantMessage("not json"))
                .thenReturn(new AssistantMessage("{\"ok\": true}"));

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
        capturedMessages(model, 2);
    }

    @Test
    void raisesWhenTotalBudgetExceeded() throws Exception {
        Model model = mock(Model.class);
        whenInvoke(model).thenAnswer(invocation -> {
            Thread.sleep(100);
            return new AssistantMessage("");
        });

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
        assertEquals("1", error.getParams().get("attempts"));
        assertTrue(elapsed < 0.5);
        capturedMessages(model, 1);
    }

    @Test
    void invokeTextWithRetryAndPromptReturnsPromptUsed() throws Exception {
        Model model = mock(Model.class);
        whenInvoke(model).thenReturn(new AssistantMessage("ok"));

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

    private static OngoingStubbing<AssistantMessage> whenInvoke(Model model) throws Exception {
        return when(model.invoke(
                any(),
                isNull(),
                nullable(Float.class),
                isNull(),
                any(),
                isNull(),
                isNull(),
                isNull(),
                nullable(Float.class),
                isNull()
        ));
    }

    private static List<Object> capturedMessages(Model model, int invocations) throws Exception {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(model, times(invocations)).invoke(
                captor.capture(),
                isNull(),
                nullable(Float.class),
                isNull(),
                eq("test-model"),
                isNull(),
                isNull(),
                isNull(),
                nullable(Float.class),
                isNull()
        );
        return captor.getAllValues();
    }

    private static void assertPrompt(Object messages, String expectedPrompt) {
        List<?> messageList = assertInstanceOf(List.class, messages);
        UserMessage message = assertInstanceOf(UserMessage.class, messageList.getFirst());
        assertEquals(expectedPrompt, message.getContentAsString());
    }
}
