/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package facade for agent-team schema exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.schema} in
 * {@code openjiuwen/agent_teams/schema/__init__.py}.</p>
 */
public final class AgentTeamsSchemaPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/schema/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "AudioModelSpec",
            "DeepAgentSpec",
            "LeaderSpec",
            "ProgressiveToolSpec",
            "RailSpec",
            "StorageSpec",
            "SubAgentSpec",
            "SysOperationSpec",
            "TeamAgentSpec",
            "TeamOutputSchema",
            "TransportSpec",
            "VisionModelSpec",
            "WorkspaceSpec",
            "register_rail_type",
            "register_storage",
            "register_transport",
            "TeamLifecycle",
            "TeamMemberSpec",
            "TeamRole",
            "TeamRuntimeContext",
            "TeamSpec"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private AgentTeamsSchemaPackage() {
    }

    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    public static Class<?> typeFor(String exportedName) {
        return EXPORTED_TYPES.get(exportedName);
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("AudioModelSpec", AudioModelSpec.class);
        exports.put("DeepAgentSpec", DeepAgentSpec.class);
        exports.put("LeaderSpec", LeaderSpec.class);
        exports.put("ProgressiveToolSpec", ProgressiveToolSpec.class);
        exports.put("RailSpec", RailSpec.class);
        exports.put("StorageSpec", StorageSpec.class);
        exports.put("SubAgentSpec", SubAgentSpec.class);
        exports.put("SysOperationSpec", SysOperationSpec.class);
        exports.put("TeamAgentSpec", TeamAgentSpec.class);
        exports.put("TeamOutputSchema", TeamOutputSchema.class);
        exports.put("TransportSpec", TransportSpec.class);
        exports.put("VisionModelSpec", VisionModelSpec.class);
        exports.put("WorkspaceSpec", WorkspaceSpec.class);
        exports.put("TeamLifecycle", TeamLifecycle.class);
        exports.put("TeamMemberSpec", TeamMemberSpec.class);
        exports.put("TeamRole", TeamRole.class);
        exports.put("TeamRuntimeContext", TeamRuntimeContext.class);
        exports.put("TeamSpec", TeamSpec.class);
        return Collections.unmodifiableMap(exports);
    }
}
