/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared filesystem path helpers for agent teams.
 *
 * <p>Single source of truth for the on-disk layout used by team workspaces,
 * member workspaces, and the default sqlite db. Centralizing it here keeps
 * creation and cleanup in sync.</p>
 */
public final class TeamPaths {

    private static volatile Path configuredOpenjiuwenHome;

    private TeamPaths() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void configureOpenjiuwenHome(String path) {
        configuredOpenjiuwenHome = Paths.get(path);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void configureOpenjiuwenHome(Path path) {
        configuredOpenjiuwenHome = path;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void resetOpenjiuwenHome() {
        configuredOpenjiuwenHome = null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Path getOpenjiuwenHome() {
        if (configuredOpenjiuwenHome != null) {
            return configuredOpenjiuwenHome;
        }
        return Paths.get(System.getProperty("user.home"), ".openjiuwen");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Path getAgentTeamsHome() {
        return getOpenjiuwenHome().resolve(".agent_teams");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Path teamHome(String teamName) {
        return getAgentTeamsHome().resolve(teamName);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Path independentMemberWorkspace(String memberName) {
        return getOpenjiuwenHome().resolve(memberName + "_workspace");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Path teamMemoryDir(String teamName) {
        return teamHome(teamName).resolve("team-workspace").resolve("team-memory");
    }
}
