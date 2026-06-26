/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package facade for agent-builder resource exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.resource} in
 * {@code openjiuwen/dev_tools/agent_builder/resource/__init__.py}.</p>
 */
public final class AgentBuilderResourcePackage {

    public static final String PYTHON_MODULE = "openjiuwen/dev_tools/agent_builder/resource/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of("ResourceRetriever", "PluginProcessor");
    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private AgentBuilderResourcePackage() {
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("ResourceRetriever", ResourceRetriever.class);
        exports.put("PluginProcessor", PluginProcessor.class);
        return Collections.unmodifiableMap(exports);
    }
}
