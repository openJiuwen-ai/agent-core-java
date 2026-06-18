/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import java.util.List;
import java.util.Map;

/**
 * Package bridge for the public LLM facade exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.llm} package facade in
 * {@code openjiuwen/core/foundation/llm/__init__.py}.</p>
 */
public final class FoundationLlmPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/foundation/llm/__init__.py";

    public static final List<String> CORE_CLASSES = List.of(
            "Model",
            "init_model",
            "BaseModelClient",
            "BaseOutputParser"
    );

    public static final List<String> CONFIG_CLASSES = List.of(
            "ModelRequestConfig",
            "ModelClientConfig",
            "ProviderType",
            "BaseModelInfo",
            "ModelConfig"
    );

    public static final List<String> MESSAGE_CLASSES = List.of(
            "BaseMessage",
            "AssistantMessage",
            "UserMessage",
            "SystemMessage",
            "ToolMessage",
            "UsageMetadata"
    );

    public static final List<String> MESSAGE_CHUNK_CLASSES = List.of(
            "AssistantMessageChunk"
    );

    public static final List<String> TOOL_CLASSES = List.of(
            "ToolCall"
    );

    public static final List<String> PREBUILT_MODEL_CLIENTS = List.of(
            "OpenAIModelClient"
    );

    public static final List<String> PREBUILT_OUTPUT_PARSERS = List.of(
            "JsonOutputParser",
            "MarkdownOutputParser"
    );

    public static final List<String> EXPORTED_SYMBOLS = List.of(
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

    public static final Map<String, List<String>> EXPORT_GROUPS = Map.of(
            "core", CORE_CLASSES,
            "configuration", CONFIG_CLASSES,
            "messages", MESSAGE_CLASSES,
            "message_chunks", MESSAGE_CHUNK_CLASSES,
            "tools", TOOL_CLASSES,
            "prebuilt_model_clients", PREBUILT_MODEL_CLIENTS,
            "prebuilt_output_parsers", PREBUILT_OUTPUT_PARSERS
    );

    private FoundationLlmPackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Return a Python export group by its Java ledger name.
     *
     * @param groupName group name
     * @return group symbols, or an empty list when absent
     */
    public static List<String> group(String groupName) {
        return EXPORT_GROUPS.getOrDefault(groupName, List.of());
    }
}
