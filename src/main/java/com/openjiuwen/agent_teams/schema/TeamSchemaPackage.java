/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import java.util.List;

/**
 * Module facade for team-level schema exports.
 *
 * <p>Mirrors Python's module exports in
 * {@code openjiuwen/agent_teams/schema/team.py}.</p>
 */
public final class TeamSchemaPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/schema/team.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "BridgeMailboxInjectMode",
            "BridgeMemberSpec",
            "ExternalCliAgentSpec",
            "MemberOpResult",
            "TeamCompletionSnapshot",
            "TeamLifecycle",
            "TeamMemberSpec",
            "TeamRole",
            "TeamRuntimeContext",
            "TeamSpec"
    );

    private TeamSchemaPackage() {
    }
}
