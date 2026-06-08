/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import java.nio.file.Path;

/**
 * Shared filesystem path helpers for agent teams.
 *
 * <p>Mirrors Python's path helpers in
 * {@code openjiuwen/agent_teams/paths.py}.</p>
 */
public final class AgentTeamPaths {

    private static Path configuredOpenjiuwenHome;

    private AgentTeamPaths() {
    }

    public static void configureOpenjiuwenHome(Path path) {
        configuredOpenjiuwenHome = path;
    }

    public static void configureOpenjiuwenHome(String path) {
        configuredOpenjiuwenHome = Path.of(path);
    }

    public static void resetOpenjiuwenHome() {
        configuredOpenjiuwenHome = null;
    }

    public static Path getOpenjiuwenHome() {
        if (configuredOpenjiuwenHome != null) {
            return configuredOpenjiuwenHome;
        }
        return Path.of(System.getProperty("user.home")).resolve(".openjiuwen");
    }

    public static Path getAgentTeamsHome() {
        return getOpenjiuwenHome().resolve(".agent_teams");
    }

    public static Path teamHome(String teamName) {
        return getAgentTeamsHome().resolve(teamName);
    }

    public static Path independentMemberWorkspace(String memberName) {
        return getOpenjiuwenHome().resolve(memberName + "_workspace");
    }

    public static Path teamMemoryDir(String teamName) {
        return teamHome(teamName).resolve("team-workspace").resolve("team-memory");
    }
}
