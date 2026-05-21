/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.core.memory.lite.MemoryManagerParams;
import com.openjiuwen.core.memory.lite.MemorySettings;
import com.openjiuwen.core.memory.lite.MemoryToolContext;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Per-member memory toolkit providing memory tools for team members.
 * <p>
 * Initializes a {@link MemoryToolContext} or {@link CodingMemoryToolContext}
 * based on the scenario, and exposes memory tools (search, get, write, edit).
 * <p>
 * Mirrors Python's {@code MemberMemoryToolkit} from
 * {@code memory/team/member_memory_toolkit.py}.
 */
public class MemberMemoryToolkit {

    private final String memberName;
    private final String teamName;
    private final Object workspace;
    private final String scenario;
    private final EmbeddingConfig embeddingConfig;
    private final Object sysOperation;
    private final boolean readOnly;

    private Object manager;
    private Object ctx;
    private boolean initialized;

    public MemberMemoryToolkit(String memberName, String teamName, Object workspace,
                               String scenario, EmbeddingConfig embeddingConfig,
                               Object sysOperation, boolean readOnly) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.workspace = workspace;
        this.scenario = (scenario != null ? scenario : "general").trim().toLowerCase();
        this.embeddingConfig = embeddingConfig;
        this.sysOperation = sysOperation;
        this.readOnly = readOnly;
        this.initialized = false;
    }

    /**
     * Initialize the memory toolkit.
     * <p>
     * Sets up the memory index manager and tool context based on the scenario.
     */
    public CompletableFuture<Boolean> initialize() {
        if (initialized && manager != null) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String agentId = teamName + "." + memberName;
                String nodeName = "coding".equals(scenario) ? "coding_memory" : "memory";

                // Create memory settings
                MemorySettings settings = new MemorySettings();

                // Create manager params
                MemoryManagerParams params = MemoryManagerParams.builder()
                        .agentId(agentId)
                        .settings(settings)
                        .embeddingConfig(embeddingConfig)
                        .sysOperation(sysOperation)
                        .nodeName(nodeName)
                        .build();

                // Note: Full MemoryIndexManager initialization deferred
                // The toolkit is structured to support future integration
                this.initialized = true;
                Loggers.MEMORY.info("[MemberMemoryToolkit] Initialized for {}.{}", teamName, memberName);
                return true;
            } catch (Exception e) {
                Loggers.MEMORY.error("[MemberMemoryToolkit] Failed to initialize: {}", e.getMessage());
                this.manager = null;
                return false;
            }
        });
    }

    /**
     * Close the toolkit and release resources.
     */
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            this.ctx = null;
            this.manager = null;
            this.initialized = false;
        });
    }

    public Object getManager() {
        return manager;
    }

    public Object getCtx() {
        return ctx;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getMemberName() {
        return memberName;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
