/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.Map;

/**
 * Standard interface for tool metadata providers.
 * <p>
 * All DeepAgent built-in tools must implement this interface,
 * ensuring complete bilingual descriptions and parameter schemas.
 * <p>
 * Mirrors Python's {@code ToolMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.base}.
 */
public interface ToolMetadataProvider {

    /** Unique name of the tool in the registry. */
    String getName();

    /** Return the tool description in the specified language. */
    String getDescription(String language);

    /** Return the tool description in the default language. */
    default String getDescription() {
        return getDescription("cn");
    }

    /** Return JSON Schema parameter definitions for the specified language. */
    Map<String, Object> getInputParams(String language);

    /** Return JSON Schema parameter definitions in the default language. */
    default Map<String, Object> getInputParams() {
        return getInputParams("cn");
    }
}
