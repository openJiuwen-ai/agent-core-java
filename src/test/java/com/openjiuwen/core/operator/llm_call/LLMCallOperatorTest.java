/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.operator.llm_call;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.operator.OperatorTestSupport;
import com.openjiuwen.core.operator.TunableSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Port of Python LLMCallOperator tests.
 */
class LLMCallOperatorTest {

    @Test
    @DisplayName("operator id and tunables")
    void testOperatorIdAndTunables() {
        Model llm = mock(Model.class);
        LLMCallOperator operator = new LLMCallOperator(
                "gpt-4", llm, "sys", "{{query}}", false, false, "llm_call", null);
        LLMCallOperator custom = new LLMCallOperator(
                "gpt-4", llm, "sys", "{{query}}", false, true, "custom_id", null);
        LLMCallOperator frozenSystem = new LLMCallOperator(
                "gpt-4", llm, "sys", "{{query}}", true, false, "llm_call", null);
        LLMCallOperator frozenUser = new LLMCallOperator(
                "gpt-4", llm, "sys", "{{query}}", false, true, "llm_call", null);
        LLMCallOperator bothFrozen = new LLMCallOperator(
                "gpt-4", llm, "sys", "{{query}}", true, true, "llm_call", null);

        assertEquals("llm_call", operator.getOperatorId());
        assertEquals("custom_id", custom.getOperatorId());

        Map<String, TunableSpec> tunables = operator.getTunables();
        assertTrue(tunables.containsKey("system_prompt"));
        assertTrue(tunables.containsKey("user_prompt"));
        assertEquals("prompt", tunables.get("system_prompt").kind());
        assertEquals("prompt", tunables.get("user_prompt").kind());

        assertFalse(frozenSystem.getTunables().containsKey("system_prompt"));
        assertTrue(frozenSystem.getTunables().containsKey("user_prompt"));
        assertTrue(frozenUser.getTunables().containsKey("system_prompt"));
        assertFalse(frozenUser.getTunables().containsKey("user_prompt"));
        assertTrue(bothFrozen.getTunables().isEmpty());
    }

    @Test
    @DisplayName("set parameter, state, freeze and callback")
    void testPromptsStateFreezeAndCallback() {
        Model llm = mock(Model.class);
        @SuppressWarnings("unchecked")
        BiConsumer<String, Object> callback = mock(BiConsumer.class);
        LLMCallOperator operator = new LLMCallOperator(
                "gpt-4",
                llm,
                "You are a helpful assistant.",
                "Answer: {{query}}",
                false,
                false,
                "llm_call",
                callback);

        operator.setParameter("system_prompt", "New system prompt");
        operator.setParameter("user_prompt", "New: {{query}}");
        assertEquals("New system prompt", operator.getSystemPrompt().getContent());
        assertEquals("New: {{query}}", operator.getUserPrompt().getContent());
        verify(callback).accept("system_prompt", "New system prompt");
        verify(callback).accept("user_prompt", "New: {{query}}");

        Map<String, Object> state = operator.getState();
        assertEquals("New system prompt", state.get("system_prompt"));
        assertEquals("New: {{query}}", state.get("user_prompt"));

        operator.loadState(Map.of("system_prompt", "Loaded system", "user_prompt", "Loaded: {{query}}"));
        assertEquals("Loaded system", operator.getSystemPrompt().getContent());
        assertEquals("Loaded: {{query}}", operator.getUserPrompt().getContent());

        operator.updateUserPrompt("");
        assertEquals("", operator.getUserPrompt().getContent());

        operator.loadState(Map.of("system_prompt", "Partial load"));
        assertEquals("Partial load", operator.getSystemPrompt().getContent());
        assertEquals("", operator.getUserPrompt().getContent());

        operator.setFreezeSystemPrompt(true);
        operator.setFreezeUserPrompt(true);
        assertTrue(operator.getFreezeSystemPrompt());
        assertTrue(operator.getFreezeUserPrompt());

        operator.setParameter("system_prompt", "ignored");
        operator.setParameter("user_prompt", "ignored");
        assertEquals("Partial load", operator.getSystemPrompt().getContent());
        assertEquals("", operator.getUserPrompt().getContent());
    }

    @Test
    @DisplayName("invoke formats prompt and clears operator context")
    void testInvokeBasicAndHistory() throws Exception {
        Model llm = mock(Model.class);
        AssistantMessage response = new AssistantMessage("Hello!");
        when(llm.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(response);
        LLMCallOperator operator = new LLMCallOperator(
                "gpt-4",
                llm,
                "You are a helpful assistant.",
                "Answer: {{query}}",
                false,
                false,
                "llm_call",
                null);
        OperatorTestSupport.TrackingSession session = new OperatorTestSupport.TrackingSession();

        AssistantMessage result = operator.invoke(
                Map.of("query", "new question"),
                session,
                Map.of("history", List.of(new UserMessage("past"))));

        assertEquals("Hello!", result.getContent());
        assertEquals(Arrays.asList("llm_call", null), session.getOperatorHistory());
        assertNull(session.getCurrentOperatorId());

        ArgumentCaptor<Object> messagesCaptor = ArgumentCaptor.forClass(Object.class);
        verify(llm).invoke(messagesCaptor.capture(), isNull(), isNull(), isNull(), eq("gpt-4"),
                isNull(), isNull(), isNull(), isNull(), anyMap());

        @SuppressWarnings("unchecked")
        List<BaseMessage> messages = (List<BaseMessage>) messagesCaptor.getValue();
        assertEquals(3, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertEquals("You are a helpful assistant.", messages.get(0).getContent());
        assertEquals("past", ((BaseMessage) messages.get(1)).getContent());
        assertEquals("Answer: new question", messages.get(2).getContent());
    }

    @Test
    @DisplayName("invoke forwards tool definitions and passthrough messages")
    void testInvokeWithToolsAndPassthroughMessages() throws Exception {
        Model llm = mock(Model.class);
        when(llm.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssistantMessage("response"));
        LLMCallOperator operator = new LLMCallOperator(
                "gpt-4", llm, "sys", "{{query}}", false, false, "llm_call", null);
        List<Map<String, Object>> tools = List.of(Map.of("name", "get_weather", "description", "Get weather"));
        List<BaseMessage> passthrough = List.of(new UserMessage("context"), new AssistantMessage("answer"));

        operator.invoke(Map.of("messages", passthrough), new OperatorTestSupport.TrackingSession(), Map.of("tools", tools));

        ArgumentCaptor<Object> messagesCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> toolsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(llm).invoke(messagesCaptor.capture(), toolsCaptor.capture(), isNull(), isNull(), eq("gpt-4"),
                isNull(), isNull(), isNull(), isNull(), anyMap());

        @SuppressWarnings("unchecked")
        List<BaseMessage> messages = (List<BaseMessage>) messagesCaptor.getValue();
        assertEquals(3, messages.size());
        assertEquals("sys", messages.get(0).getContent());
        assertEquals("context", messages.get(1).getContent());
        assertEquals("answer", messages.get(2).getContent());
        assertEquals(tools, toolsCaptor.getValue());
    }

    @Test
    @DisplayName("stream yields chunks and clears context")
    void testStreamBasicAndCleanup() throws Exception {
        Model llm = mock(Model.class);
        List<AssistantMessageChunk> stream = List.of(
                AssistantMessageChunk.builder().content("Hel").build(),
                AssistantMessageChunk.builder().content("lo!").build());
        when(llm.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(stream.iterator());
        LLMCallOperator operator = new LLMCallOperator(
                "test", llm, "sys", "{{query}}", false, false, "llm_call", null);
        OperatorTestSupport.TrackingSession session = new OperatorTestSupport.TrackingSession();

        List<AssistantMessageChunk> chunks = new ArrayList<>();
        Iterator<AssistantMessageChunk> iterator = operator.stream(Map.of("query", "hi"), session, Map.of());
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }

        assertEquals(2, chunks.size());
        assertEquals(Arrays.asList("llm_call", null), session.getOperatorHistory());
        assertNull(session.getCurrentOperatorId());
    }

    @Test
    @DisplayName("updateSystemPrompt directly updates system prompt")
    void testUpdateSystemPrompt() {
        Model llm = mock(Model.class);
        LLMCallOperator operator = new LLMCallOperator(
                "gpt-4", llm, "original", "{{query}}", false, false, "llm_call", null);

        operator.updateSystemPrompt("Updated system prompt");
        assertEquals("Updated system prompt", operator.getSystemPrompt().getContent());
    }

    @Test
    @DisplayName("stream close clears context on early termination")
    void testStreamEarlyCloseClearsContext() throws Exception {
        Model llm = mock(Model.class);
        List<AssistantMessageChunk> stream = List.of(
                AssistantMessageChunk.builder().content("Hel").build(),
                AssistantMessageChunk.builder().content("lo!").build());
        when(llm.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(stream.iterator());
        LLMCallOperator operator = new LLMCallOperator("test", llm, "sys", "{{query}}", false, false, "llm_call", null);
        OperatorTestSupport.TrackingSession session = new OperatorTestSupport.TrackingSession();

        OperatorStream<AssistantMessageChunk> iterator = operator.stream(Map.of("query", "hi"), session, Map.of());
        assertTrue(iterator.hasNext());
        iterator.next();
        iterator.close();

        assertEquals(Arrays.asList("llm_call", null), session.getOperatorHistory());
        assertNull(session.getCurrentOperatorId());
    }
}
