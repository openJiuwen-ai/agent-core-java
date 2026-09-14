/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.common.clients.ClientRegistry;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for {@link DeepSeekModelClient}.
 *
 * <p>Mirrors Python's {@code DeepSeekModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/deepseek_model_client.py}.</p>
 */
class DeepSeekModelClientTest {

    @Test
    void exposesDeepSeekClientNameAndProviderName() {
        DeepSeekModelClient client = new DeepSeekModelClient(requestConfig(), clientConfig());

        assertThat(DeepSeekModelClient.__client_name__).isEqualTo("DeepSeek");
        assertThat(client.getClientName()).isEqualTo("DeepSeek client");
        assertThat(ClientRegistry.getClientRegistry().listClients()).contains("llm_DeepSeek");
    }

    @Test
    void convertMessagesAddsReasoningContentToAssistantMapsOnly() {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", "answer");
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", "question");

        List<Map<String, Object>> converted = DeepSeekModelClient.convertMessagesToDict(List.of(assistant, user));

        assertThat(converted.get(0)).containsEntry("reasoning_content", "");
        assertThat(converted.get(1)).doesNotContainKey("reasoning_content");
    }

    @Test
    void convertMessagesPreservesExistingReasoningContent() {
        AssistantMessage assistant = AssistantMessage.builder()
                .content("answer")
                .reasoningContent("because")
                .toolCalls(List.of(ToolCall.builder()
                        .id("call-1")
                        .type("function")
                        .name("search")
                        .arguments("{\"query\":\"deepseek\"}")
                        .build()))
                .build();

        List<Map<String, Object>> converted = DeepSeekModelClient.convertMessagesToDict(List.of(assistant));

        assertThat(converted.get(0)).containsEntry("reasoning_content", "because");
    }

    @Test
    void buildRequestParamsUsesDeepSeekMessageConversion() {
        DeepSeekModelClient client = new DeepSeekModelClient(requestConfig(), clientConfig());

        Map<String, Object> params = client.buildRequestParams(
                List.of(new AssistantMessage("answer"), new UserMessage("question")),
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                Map.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) params.get("messages");
        assertThat(messages.get(0)).containsEntry("role", "assistant")
                .containsEntry("reasoning_content", "");
        assertThat(messages.get(1)).containsEntry("role", "user")
                .doesNotContainKey("reasoning_content");
    }

    @Test
    void generationMethodsMirrorPythonPassByReturningNull() {
        DeepSeekModelClient client = new DeepSeekModelClient(requestConfig(), clientConfig());

        assertThat(client.generateImage(List.of(new UserMessage("image")), null, "1664*928",
                null, 1, true, false, 0, Map.of())).isNull();
        assertThat(client.generateSpeech(List.of(new UserMessage("speech")), null, "Cherry",
                "Auto", Map.of())).isNull();
        assertThat(client.generateVideo(List.of(new UserMessage("video")), null, null, null,
                null, null, 5, true, false, null, null, Map.of())).isNull();
    }

    private static ModelRequestConfig requestConfig() {
        return ModelRequestConfig.builder().modelName("deepseek-chat").build();
    }

    private static ModelClientConfig clientConfig() {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.DEEP_SEEK)
                .apiKey("sk-test")
                .apiBase("http://localhost")
                .verifySsl(false)
                .build();
    }
}
