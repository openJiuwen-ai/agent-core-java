/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System tests for reactive Model APIs backed by a real remote model.
 */
@Tag("system-test")
class ModelReactiveSystemTest extends SystemTestSupport {

    @Test
    @DisplayName("Model.invoke invokes remote model")
    void testModelInvokeWithRemoteModel() {
        assumeRemoteModelAvailable();

        Model model = newRemoteModel();

        AssistantMessage message = model.invoke(
                List.of(new UserMessage("Reply with the exact token MODEL_INV_OK.")),
                null, 0.0f, 0.9f, 128, null, null, null, null, null);

        assertTrue(
                containsIgnoreCase(message.getContentAsString(), "MODEL_INV_OK"),
                () -> "Expected MODEL_INV_OK in output but got: "
                        + message.getContentAsString());
    }

    @Test
    @DisplayName("Model.stream streams remote model output")
    void testModelStreamWithRemoteModel() {
        assumeRemoteModelAvailable();

        Model model = newRemoteModel();

        List<AssistantMessageChunk> chunks = new ArrayList<>();
        model.stream(List.of(new UserMessage("Reply with the exact token MODEL_STR_OK.")))
                .forEachRemaining(chunks::add);

        String text = chunks.stream()
                .map(AssistantMessageChunk::getContent)
                .filter(content -> content != null)
                .map(String::valueOf)
                .reduce("", String::concat);
        assertTrue(containsIgnoreCase(text, "MODEL_STR_OK"),
                () -> "Expected MODEL_STR_OK in stream but got: " + text);
    }

    private Model newRemoteModel() {
        return new Model(remoteClientConfig(120.0), remoteRequestConfig(0.0, 128));
    }
}
