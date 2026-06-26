/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Package facade for single-agent rail exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.single_agent.rail} module in
 * {@code openjiuwen/core/single_agent/rail/__init__.py}.</p>
 */
public final class SingleAgentRailPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/single_agent/rail/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "AgentCallbackEvent",
            "AgentCallbackContext",
            "AgentRail",
            "AgentCallback",
            "SyncAgentCallback",
            "AnyAgentCallback",
            "EVENT_METHOD_MAP",
            "InvokeInputs",
            "ModelCallInputs",
            "ToolCallInputs",
            "TaskIterationInputs",
            "EventInputs",
            "ForceFinishRequest",
            "rail"
    );

    private static final Map<String, String> JAVA_REFERENCES = Map.ofEntries(
            Map.entry("AgentCallbackEvent", AgentCallbackEvent.class.getName()),
            Map.entry("AgentCallbackContext", AgentCallbackContext.class.getName()),
            Map.entry("AgentRail", AgentRail.class.getName()),
            Map.entry("AgentCallback", AgentCallback.class.getName()),
            Map.entry("SyncAgentCallback", AgentCallback.class.getName()),
            Map.entry("AnyAgentCallback", AgentCallback.class.getName()),
            Map.entry("EVENT_METHOD_MAP", Rails.class.getName() + "#eventMethodMap()"),
            Map.entry("InvokeInputs", InvokeInputs.class.getName()),
            Map.entry("ModelCallInputs", ModelCallInputs.class.getName()),
            Map.entry("ToolCallInputs", ToolCallInputs.class.getName()),
            Map.entry("TaskIterationInputs", TaskIterationInputs.class.getName()),
            Map.entry("EventInputs", EventInputs.class.getName()),
            Map.entry("ForceFinishRequest", ForceFinishRequest.class.getName()),
            Map.entry("rail", Rails.class.getName())
    );

    private SingleAgentRailPackage() {
    }

    public static boolean exports(String symbol) {
        return EXPORTED_SYMBOLS.contains(symbol);
    }

    public static Optional<String> javaReference(String symbol) {
        return Optional.ofNullable(JAVA_REFERENCES.get(symbol));
    }
}
