/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.tool_discovery;

import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.List;
import java.util.Map;

/**
 * Loads selected real tools into the session-visible tool set.
 *
 * <p>Mirrors Python's {@code LoadToolsTool} in
 * {@code openjiuwen/harness/tools/tool_discovery/load_tools.py}.</p>
 */
public class LoadToolsTool extends AbstractHarnessTool {

    private final ToolLoader toolLoader;

    public LoadToolsTool(ToolLoader toolLoader) {
        super(toolCard("load_tools", "LoadToolsTool", "Resolve and load selected tools for the current session."));
        this.toolLoader = toolLoader;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        try {
            LoadToolsInput parsed = parse(inputs);
            Object session = kwargs == null ? null : kwargs.get("session");
            Map<String, Object> result = toolLoader == null
                    ? Map.of("tool_names", parsed.toolNames(), "replace", parsed.replace())
                    : toolLoader.load(session, parsed.toolNames(), parsed.replace());
            return ToolOutput.success(result);
        } catch (Exception exception) {
            return ToolOutput.failure(exception.getMessage());
        }
    }

    private static LoadToolsInput parse(Map<String, Object> inputs) {
        Object rawNames = inputs == null ? null : inputs.get("tool_names");
        List<String> names = rawNames instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        boolean replace = boolValue(inputs == null ? null : inputs.get("replace"), false);
        return new LoadToolsInput(names, replace);
    }

    @FunctionalInterface
    public interface ToolLoader {
        Map<String, Object> load(Object session, List<String> toolNames, boolean replace) throws Exception;
    }
}
