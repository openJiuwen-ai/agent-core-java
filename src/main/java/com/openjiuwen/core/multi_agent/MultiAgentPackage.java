/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package-level export metadata for multi-agent APIs.
 *
 * <p>Mirrors Python's module {@code openjiuwen.core.multi_agent} in
 * {@code openjiuwen/core/multi_agent/__init__.py}.</p>
 */
public final class MultiAgentPackage {

    private static final Map<String, String> LAZY_EXPORTS = new LinkedHashMap<>();
    private static final List<String> ALL = List.of(
            "TeamCard",
            "EventDrivenTeamCard",
            "TeamConfig",
            "Session",
            "BaseTeam",
            "create_agent_team_session"
    );

    static {
        LAZY_EXPORTS.put("BaseTeam", "com.openjiuwen.core.multi_agent.BaseTeam");
        LAZY_EXPORTS.put("TeamConfig", "com.openjiuwen.core.multi_agent.TeamConfig");
        LAZY_EXPORTS.put("TeamCard", "com.openjiuwen.core.multi_agent.schema.TeamCard");
        LAZY_EXPORTS.put("EventDrivenTeamCard", "com.openjiuwen.core.multi_agent.schema.EventDrivenTeamCard");
        LAZY_EXPORTS.put("Session", "com.openjiuwen.core.session.agent_team.Session");
    }

    private MultiAgentPackage() {
    }

    public static List<String> all() {
        return ALL;
    }

    public static Class<?> resolve(String name) {
        String className = LAZY_EXPORTS.get(name);
        if (className == null) {
            throw new IllegalArgumentException(
                    "module 'openjiuwen.core.multi_agent' has no attribute '" + name + "'"
            );
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to import multi-agent export: " + name, exception);
        }
    }
}
