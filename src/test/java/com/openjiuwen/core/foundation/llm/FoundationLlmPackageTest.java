/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused parity tests for the LLM package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.llm} package facade in
 * {@code openjiuwen/core/foundation/llm/__init__.py}.</p>
 */
class FoundationLlmPackageTest {

    @Test
    void exposesPythonAllInOrder() {
        List<String> expected = List.of(
                "Model",
                "init_model",
                "BaseModelClient",
                "BaseOutputParser",
                "ModelRequestConfig",
                "ModelClientConfig",
                "ProviderType",
                "BaseModelInfo",
                "ModelConfig",
                "BaseMessage",
                "AssistantMessage",
                "UserMessage",
                "SystemMessage",
                "ToolMessage",
                "UsageMetadata",
                "AssistantMessageChunk",
                "ToolCall",
                "OpenAIModelClient",
                "JsonOutputParser",
                "MarkdownOutputParser"
        );

        assertEquals("openjiuwen/core/foundation/llm/__init__.py", FoundationLlmPackage.PYTHON_MODULE);
        assertIterableEquals(expected, FoundationLlmPackage.EXPORTED_SYMBOLS);
        assertSame(FoundationLlmPackage.EXPORTED_SYMBOLS, FoundationLlmPackage.all());
    }

    @Test
    void preservesPythonExportGroups() {
        assertIterableEquals(List.of("Model", "init_model", "BaseModelClient", "BaseOutputParser"),
                FoundationLlmPackage.group("core"));
        assertIterableEquals(List.of("ModelRequestConfig", "ModelClientConfig", "ProviderType", "BaseModelInfo",
                        "ModelConfig"),
                FoundationLlmPackage.group("configuration"));
        assertIterableEquals(List.of("BaseMessage", "AssistantMessage", "UserMessage", "SystemMessage",
                        "ToolMessage", "UsageMetadata"),
                FoundationLlmPackage.group("messages"));
        assertIterableEquals(List.of("AssistantMessageChunk"), FoundationLlmPackage.group("message_chunks"));
        assertIterableEquals(List.of("ToolCall"), FoundationLlmPackage.group("tools"));
        assertIterableEquals(List.of("OpenAIModelClient"), FoundationLlmPackage.group("prebuilt_model_clients"));
        assertIterableEquals(List.of("JsonOutputParser", "MarkdownOutputParser"),
                FoundationLlmPackage.group("prebuilt_output_parsers"));
        assertIterableEquals(List.of(), FoundationLlmPackage.group("missing"));
    }
}
