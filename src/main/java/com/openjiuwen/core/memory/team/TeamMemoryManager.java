/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Team-scoped orchestration for per-member memory tools and prompt injection.
 * <p>
 * Manages the lifecycle of team memory: initializes the member toolkit,
 * registers memory tools with the agent, injects context into system prompts,
 * and handles extraction after coordination rounds.
 * <p>
 * Resource contract:
 * <ul>
 *   <li>{@link #registerTools(Object)} registers tools with the agent.</li>
 *   <li>{@link #close()} removes tools and releases resources.</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code TeamMemoryManager} from
 * {@code memory/team/manager.py}.
 */
public class TeamMemoryManager {

    public static final String SECTION_NAME = "team_memory";
    private static final int MAX_PERSONAL_MEMORY_BYTES = 10 * 1024;

    private final String memberName;
    private final String teamName;
    private final String role;
    private final String lifecycle;
    private final String scenario;
    private final EmbeddingConfig embeddingConfig;
    private final String language;
    private final String promptMode;
    private final boolean enableAutoExtract;
    private final Object sysOperation;
    private final Object workspace;
    private final String teamMemoryDir;

    private MemberMemoryToolkit toolkit;
    private SharedMemoryManager sharedManager;
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();
    private Object deepAgentForCleanup;

    /**
     * Create a new TeamMemoryManager from parameters.
     */
    public TeamMemoryManager(
            String memberName, String teamName, String role,
            String lifecycle, String scenario,
            EmbeddingConfig embeddingConfig, String language, String promptMode,
            boolean enableAutoExtract, Object sysOperation,
            Object workspace, String teamMemoryDir) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.role = role;
        this.lifecycle = lifecycle;
        this.scenario = scenario;
        this.embeddingConfig = embeddingConfig;
        this.language = language;
        this.promptMode = promptMode;
        this.enableAutoExtract = enableAutoExtract;
        this.sysOperation = sysOperation;
        this.workspace = workspace;
        this.teamMemoryDir = teamMemoryDir;
    }

    /**
     * Initialize the member memory toolkit.
     */
    public CompletableFuture<Boolean> initToolkit() {
        if (toolkit != null) {
            return CompletableFuture.completedFuture(true);
        }

        if (workspace == null) {
            Loggers.MEMORY.warn("[TeamMemoryManager] No workspace available, skipping init");
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                toolkit = new MemberMemoryToolkit(
                        memberName, teamName, workspace,
                        scenario, embeddingConfig, sysOperation,
                        false);

                boolean success = toolkit.initialize().join();
                if (!success) {
                    Loggers.MEMORY.warn("[TeamMemoryManager] Toolkit init failed, memory tools unavailable");
                    return false;
                }

                if (teamMemoryDir != null && !teamMemoryDir.isEmpty()) {
                    sharedManager = new SharedMemoryManager(teamMemoryDir, sysOperation);
                    sharedManager.ensureDir().join();
                }

                Loggers.MEMORY.info("[TeamMemoryManager] Initialized for {}.{} lifecycle={} scenario={}",
                        teamName, memberName, lifecycle, scenario);
                return true;
            } catch (Exception e) {
                Loggers.MEMORY.error("[TeamMemoryManager] Init failed: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * Register memory tools with the agent.
     * <p>
     * Idempotent: skips if already registered.
     */
    public void registerTools(Object deepAgent) {
        if (!ownedToolNames.isEmpty()) {
            return;
        }
        if (toolkit == null) {
            return;
        }
        this.deepAgentForCleanup = deepAgent;
        // Full tool registration with Runner.resource_mgr and ability_manager
        // deferred until those subsystems are fully ported.
        Loggers.MEMORY.info("[TeamMemoryManager] Tool registration for {}.{} (pending full integration)",
                teamName, memberName);
    }

    /**
     * Load personal and team memory and inject into system prompt context.
     */
    public CompletableFuture<String> loadAndInject(String query) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sb = new StringBuilder();
            // Personal memory injection
            if (toolkit != null && toolkit.getCtx() != null) {
                sb.append("[Memory context loading pending full integration]");
            }
            // Shared team memory injection
            if (sharedManager != null) {
                try {
                    String teamSummary = sharedManager.readTeamSummary().join();
                    if (teamSummary != null && !teamSummary.isEmpty()) {
                        sb.append("\n## Team Memory\n").append(teamSummary);
                    }
                } catch (Exception e) {
                    Loggers.MEMORY.debug("[TeamMemoryManager] Failed to read team summary: {}", e.getMessage());
                }
            }
            return sb.toString().trim();
        });
    }

    /**
     * Extract memory after a coordination round.
     */
    public CompletableFuture<Void> extractAfterRound(String roundSummary) {
        if (!enableAutoExtract || toolkit == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            Loggers.MEMORY.debug("[TeamMemoryManager] Extract after round for {}.{}",
                    teamName, memberName);
            // Full extraction logic deferred
        });
    }

    /**
     * Close and release all resources.
     */
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            ownedToolNames.clear();
            ownedToolIds.clear();
            deepAgentForCleanup = null;

            if (toolkit != null) {
                toolkit.close().join();
            }
            Loggers.MEMORY.info("[TeamMemoryManager] Closed for {}.{}", teamName, memberName);
        });
    }

    // ==================== Accessors ====================

    public MemberMemoryToolkit getToolkit() {
        return toolkit;
    }

    public SharedMemoryManager getSharedManager() {
        return sharedManager;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getTeamName() {
        return teamName;
    }
}
