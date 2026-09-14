/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.legacy.llm_call;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for legacy LLMCall behavior.
 *
 * <p>Mirrors Python's {@code LLMCall} in
 * {@code openjiuwen/core/operator/legacy/llm_call/base.py}.</p>
 */
class LLMCallTest {

    @Test
    void constructorUsesPythonDefaultsAndFreezeRules() {
        LLMCall call = new LLMCall("model-a", new Model(new RecordingClient()), "sys", "");

        assertEquals("sys", call.getSystemPrompt().getContent());
        assertEquals(LLMCall.DEFAULT_USER_PROMPT, call.getUserPrompt().getContent());
        assertFalse(call.getFreezeSystemPrompt());
        assertTrue(call.getFreezeUserPrompt());

        call.updateUserPrompt("blocked");
        assertEquals(LLMCall.DEFAULT_USER_PROMPT, call.getUserPrompt().getContent());

        call.setFreezeUserPrompt(false);
        call.updateUserPrompt("{{query}}!");
        assertEquals("{{query}}!", call.getUserPrompt().getContent());
    }

    @Test
    void invokeFormatsMessagesAndRunsOptimizerCallback() {
        RecordingClient client = new RecordingClient();
        Model model = new Model(client);
        LLMCall call = new LLMCall("model-a", model, "system {{query}}", "user {{query}}",
                false, true, "legacy");
        List<Object> callbackPayload = new ArrayList<>();
        call.setOptimizerCallback((id, inputs, response, session) -> {
            callbackPayload.add(id);
            callbackPayload.add(inputs.get("query"));
            callbackPayload.add(response);
            callbackPayload.add(session);
        });
        Map<String, Object> inputs = Map.of("query", "hello");
        ToolInfo toolInfo = ToolInfo.builder().name("search").build();

        AssistantMessage response = call.invoke(inputs, null, List.of(new BaseMessage("assistant", "history")),
                List.of(toolInfo)).toCompletableFuture().join();

        assertEquals("ok", response.getContentAsString());
        assertEquals("model-a", client.invokeOptions.get(0).getModel());
        assertEquals(List.of(toolInfo), client.invokeOptions.get(0).getTools());
        assertEquals(List.of("system", "assistant", "user"), client.invokeMessages.get(0)
                .stream()
                .map(BaseMessage::getRole)
                .toList());
        assertEquals("system hello", client.invokeMessages.get(0).get(0).getContentAsString());
        assertEquals("user hello", client.invokeMessages.get(0).get(2).getContentAsString());
        assertEquals("legacy", callbackPayload.get(0));
        assertEquals("hello", callbackPayload.get(1));
        assertSame(response, callbackPayload.get(2));
    }

    @Test
    void streamYieldsChunksAndCallbacksWithJoinedContent() {
        RecordingClient client = new RecordingClient();
        client.streamChunks = List.of(
                AssistantMessageChunk.builder().content("a").build(),
                AssistantMessageChunk.builder().content("b").build()
        );
        LLMCall call = new LLMCall("model-b", new Model(client), "sys", "{{query}}",
                false, true, "stream-id");
        List<Object> callbackPayload = new ArrayList<>();
        call.setOptimizerCallback((id, inputs, response, session) -> {
            callbackPayload.add(id);
            callbackPayload.add(response);
        });

        Iterator<AssistantMessageChunk> iterator = call.stream(Map.of("query", "go"), null);
        List<String> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next().getContentAsString());
        }

        assertEquals(List.of("a", "b"), chunks);
        assertEquals("model-b", client.streamOptions.get(0).getModel());
        assertEquals(List.of("stream-id", "ab"), callbackPayload);
    }

    @Test
    void listOfDictPromptsMirrorPydanticMessageCoercion() {
        RecordingClient client = new RecordingClient();
        LLMCall call = new LLMCall(
                "model-c",
                new Model(client),
                List.of(Map.of("role", "system", "content", "policy")),
                List.of(Map.of("role", "assistant", "content", "prefill")),
                false,
                true,
                "llm_call"
        );

        call.invoke(Map.of(), null).toCompletableFuture().join();

        List<BaseMessage> messages = client.invokeMessages.get(0);
        assertEquals("system", messages.get(0).getRole());
        assertEquals("policy", messages.get(0).getContentAsString());
        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("prefill", messages.get(1).getContentAsString());
        assertInstanceOf(AssistantMessage.class, client.invokeResponse);
    }

    private static final class RecordingClient implements Model.ModelClient {
        private final List<List<BaseMessage>> invokeMessages = new ArrayList<>();
        private final List<ModelInvokeOptions> invokeOptions = new ArrayList<>();
        private final List<List<BaseMessage>> streamMessages = new ArrayList<>();
        private final List<ModelInvokeOptions> streamOptions = new ArrayList<>();
        private List<AssistantMessageChunk> streamChunks = List.of();
        private AssistantMessage invokeResponse = new AssistantMessage("ok");

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeMessages.add(messages);
            invokeOptions.add(options);
            return CompletableFuture.completedFuture(invokeResponse);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            streamMessages.add(messages);
            streamOptions.add(options);
            return streamChunks.iterator();
        }
    }
}
