/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import reactor.test.StepVerifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

/**
 * System tests for reactive Model APIs backed by a real remote model.
 */
@Tag("system-test")
class ModelReactiveSystemTest extends SystemTestSupport {
    @Test
    @DisplayName("Model.invokeAsync invokes remote model")
    void testModelInvokeAsyncWithRemoteModel() {
        assumeRemoteModelAvailable();

        Model model = newRemoteModel();

        StepVerifier
                .create(model.invokeAsync(List.of(new UserMessage("Reply with the exact token MODEL_INV_OK.")), null,
                        0.0f, 0.9f, null, 128, null, null, 120.0f, null))
                .assertNext(message -> assertTrue(containsIgnoreCase(message.getContentAsString(), "MODEL_INV_OK"),
                        () -> "Expected MODEL_INV_OK in output but got: " + message.getContentAsString()))
                .expectComplete().verify(Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("Model.streamAsync streams remote model output")
    void testModelStreamAsyncWithRemoteModel() {
        assumeRemoteModelAvailable();

        Model model = newRemoteModel();

        StepVerifier.create(model.streamAsync(List.of(new UserMessage("Reply with the exact token MODEL_STR_OK.")),
                null, 0.0f, 0.9f, null, 128, null, null, 120.0f, null).collectList()).assertNext(chunks -> {
                    String text = chunks.stream().map(AssistantMessageChunk::getContent)
                            .filter(content -> content != null).map(String::valueOf).reduce("", String::concat);
                    assertTrue(containsIgnoreCase(text, "MODEL_STR_OK"),
                            () -> "Expected MODEL_STR_OK in stream but got: " + text);
                }).expectComplete().verify(Duration.ofSeconds(120));
    }

    private Model newRemoteModel() {
        return new Model(remoteClientConfig(120.0), remoteRequestConfig(0.0, 128));
    }
}
