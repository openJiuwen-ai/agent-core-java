/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent;

import java.util.List;

/**
 * Focused smoke for {@link MultiAgentPackage}.
 */
public final class MultiAgentPackageTest {

    private MultiAgentPackageTest() {
    }

    public static void main(String[] args) {
        require(MultiAgentPackage.all().equals(List.of(
                "TeamCard",
                "EventDrivenTeamCard",
                "TeamConfig",
                "Session",
                "BaseTeam",
                "create_agent_team_session"
        )), "__all__ order should match Python");
        require(MultiAgentPackage.resolve("TeamConfig") == TeamConfig.class, "TeamConfig lazy export");

        try {
            MultiAgentPackage.resolve("missing");
            throw new AssertionError("missing export should fail");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("has no attribute 'missing'"), "missing export message");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
