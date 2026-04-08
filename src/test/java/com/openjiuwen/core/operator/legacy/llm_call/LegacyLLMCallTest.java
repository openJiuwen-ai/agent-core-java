/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.operator.legacy.llm_call;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.operator.OperatorTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyLLMCallTest {

    @Test
    void invokeCallsOptimizerCallbackWithoutOperatorContext() throws Exception {
        Model llm = mock(Model.class);
        when(llm.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssistantMessage("legacy-response"));
        AtomicReference<Object> callbackResponse = new AtomicReference<>();
        LLMCall legacy = new LLMCall("gpt-4", llm, "sys", "{{query}}");
        legacy.setOptimizerCallback((llmCallId, inputs, response, session) -> callbackResponse.set(response));
        OperatorTestSupport.TrackingSession session = new OperatorTestSupport.TrackingSession();

        AssistantMessage result = legacy.invoke(
                Map.of("query", "hello"),
                session,
                List.of(new UserMessage("history")),
                null);

        assertEquals("legacy-response", result.getContent());
        assertEquals(result, callbackResponse.get());
        assertEquals(List.of(), session.getOperatorHistory());
        assertNull(session.getCurrentOperatorId());

        ArgumentCaptor<Object> messagesCaptor = ArgumentCaptor.forClass(Object.class);
        verify(llm).invoke(messagesCaptor.capture(), isNull(), isNull(), isNull(), eq("gpt-4"),
                isNull(), isNull(), isNull(), isNull(), anyMap());
        @SuppressWarnings("unchecked")
        List<BaseMessage> messages = (List<BaseMessage>) messagesCaptor.getValue();
        assertEquals(3, messages.size());
        assertEquals("sys", messages.get(0).getContent());
        assertEquals("history", messages.get(1).getContent());
        assertEquals("hello", messages.get(2).getContent());
    }

    @Test
    void streamAggregatesChunksForOptimizerCallback() throws Exception {
        Model llm = mock(Model.class);
        when(llm.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        AssistantMessageChunk.builder().content("Hel").build(),
                        AssistantMessageChunk.builder().content("lo").build()).iterator());
        AtomicReference<Object> callbackResponse = new AtomicReference<>();
        LLMCall legacy = new LLMCall("gpt-4", llm, "sys", "{{query}}");
        legacy.setOptimizerCallback((llmCallId, inputs, response, session) -> callbackResponse.set(response));

        List<String> chunks = new ArrayList<>();
        try (OperatorStream<AssistantMessageChunk> stream = legacy.stream(Map.of("query", "hello"), new OperatorTestSupport.TrackingSession(), null, null)) {
            while (stream.hasNext()) {
                chunks.add(String.valueOf(stream.next().getContent()));
            }
        }

        assertEquals(List.of("Hel", "lo"), chunks);
        assertEquals("Hello", callbackResponse.get());
    }
}
