/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared filesystem path helpers for agent teams.
 * <p>
 * Single source of truth for the on-disk layout used by team workspaces,
 * member workspaces, and the default sqlite db. Centralizing it here keeps
 * creation and cleanup in sync.
 * </p>
 * 
 * @since 0.1.7
 */
public final class TeamPaths {
    private static volatile Path configuredOpenjiuwenHome;

    /**
     * TeamPaths.
     * 
     * @since 0.1.7
     */
    private TeamPaths() {
    }

    /**
     * configureOpenjiuwenHome.
     * 
     * @param path path
     * @since 0.1.7
     */
    public static void configureOpenjiuwenHome(String path) {
        configuredOpenjiuwenHome = Paths.get(path);
    }

    /**
     * configureOpenjiuwenHome.
     * 
     * @param path path
     * @since 0.1.7
     */
    public static void configureOpenjiuwenHome(Path path) {
        configuredOpenjiuwenHome = path;
    }

    /**
     * resetOpenjiuwenHome.
     * 
     * @since 0.1.7
     */
    public static void resetOpenjiuwenHome() {
        configuredOpenjiuwenHome = null;
    }

    /**
     * getOpenjiuwenHome.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Path getOpenjiuwenHome() {
        if (configuredOpenjiuwenHome != null) {
            return configuredOpenjiuwenHome;
        }
        return Paths.get(System.getProperty("user.home"), ".openjiuwen");
    }

    /**
     * getAgentTeamsHome.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Path getAgentTeamsHome() {
        return getOpenjiuwenHome().resolve(".agent_teams");
    }

    /**
     * teamHome.
     * 
     * @param teamName teamName
     * @return the result
     * @since 0.1.7
     */
    public static Path teamHome(String teamName) {
        return getAgentTeamsHome().resolve(teamName);
    }

    /**
     * independentMemberWorkspace.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public static Path independentMemberWorkspace(String memberName) {
        return getOpenjiuwenHome().resolve(memberName + "_workspace");
    }

    /**
     * teamMemoryDir.
     * 
     * @param teamName teamName
     * @return the result
     * @since 0.1.7
     */
    public static Path teamMemoryDir(String teamName) {
        return teamHome(teamName).resolve("team-workspace").resolve("team-memory");
    }
}
