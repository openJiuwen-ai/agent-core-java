// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared filesystem path helpers for agent teams.
 * 
 * Single source of truth for the on-disk layout used by team workspaces,
 * member workspaces, and the default sqlite db. Centralizing it here
 * keeps creation (TeamAgent, Blueprint) and cleanup (TeamBackend.clean_team)
 * in sync: a future move of the root only needs to update this module.
 * 
 * Mirrors Python's agent_teams.paths
 * 
 * @since 0.1.12
 */
public final class Paths {
    
    /** 
     * Override the runtime home directory used by agent teams.
     * Uses AtomicReference for thread-safe configuration.
     */
    private static final AtomicReference<Path> configuredOpenjiuwenHome = new AtomicReference<>(null);
    
    /**
     * Override the runtime home directory used by agent teams.
     * 
     * @param path The path to use as the runtime home directory
     */
    public static void configureOpenjiuwenHome(String path) {
        configuredOpenjiuwenHome.set(java.nio.file.Paths.get(path));
    }
    
    /**
     * Override the runtime home directory used by agent teams.
     * 
     * @param path The path to use as the runtime home directory
     */
    public static void configureOpenjiuwenHome(Path path) {
        configuredOpenjiuwenHome.set(path);
    }
    
    /**
     * Clear the runtime home override and restore the default layout.
     */
    public static void resetOpenjiuwenHome() {
        configuredOpenjiuwenHome.set(null);
    }
    
    /**
     * Return the root directory for openJiuWen local state.
     * 
     * @return The path to the openjiuwen home directory
     */
    public static Path getOpenjiuwenHome() {
        Path configured = configuredOpenjiuwenHome.get();
        if (configured != null) {
            return configured;
        }
        return java.nio.file.Paths.get(System.getProperty("user.home"), ".openjiuwen");
    }
    
    /**
     * Return the root directory for agent-team-owned state.
     * 
     * @return The path to the agent teams home directory
     */
    public static Path getAgentTeamsHome() {
        return getOpenjiuwenHome().resolve(".agent_teams");
    }
    
    /**
     * Return the per-team root directory.
     * 
     * Layout:
     *   {getAgentTeamsHome()}/{team_name}/
     *     team-workspace/         # default team shared workspace
     *     workspaces/             # stable_base member workspaces
     *       {member}_workspace/
     *     team.db                 # default sqlite db
     * 
     * @param teamName Team identifier
     * @return Absolute path to the team-named parent directory
     */
    public static Path teamHome(String teamName) {
        return getAgentTeamsHome().resolve(teamName);
    }
    
    /**
     * Return the path of a standalone DeepAgent workspace.
     * 
     * Predefined independent DeepAgents keep their workspace at
     * {getOpenjiuwenHome()}/{member_name}_workspace/ so it survives
     * joining and leaving teams.
     * 
     * @param memberName Member identifier
     * @return Absolute path to the independent workspace directory
     */
    public static Path independentMemberWorkspace(String memberName) {
        return getOpenjiuwenHome().resolve(memberName + "_workspace");
    }
    
    /**
     * Return the per-team shared memory directory.
     * 
     * Layout: {AGENT_TEAMS_HOME}/{team_name}/team-workspace/team-memory/
     * 
     * @param teamName Team identifier
     * @return Path to the team memory directory
     */
    public static Path teamMemoryDir(String teamName) {
        return teamHome(teamName).resolve("team-workspace").resolve("team-memory");
    }
    
    // Backward-compatible constants (equivalent to Python's __getattr__)
    /**
     * Backward-compatible constant for OPENJIUWEN_HOME.
     * Equivalent to calling getOpenjiuwenHome().
     */
    public static final Path OPENJIUWEN_HOME = getOpenjiuwenHome();
    
    /**
     * Backward-compatible constant for AGENT_TEAMS_HOME.
     * Equivalent to calling getAgentTeamsHome().
     */
    public static final Path AGENT_TEAMS_HOME = getAgentTeamsHome();
    
    // Private constructor to prevent instantiation
    private Paths() {
        throw new AssertionError("Paths class should not be instantiated");
    }
}