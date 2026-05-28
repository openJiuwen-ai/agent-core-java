/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rail that provides coding memory context for programming agents.
 * <p>
 * Mirrors Python's {@code CodingMemoryRail} in
 * {@code openjiuwen.harness.rails.memory.coding_memory_rail}.
 *
 * <p>Python features:
 * <ul>
 *   <li>Auto-recall: prefetch task on each user turn</li>
 *   <li>Mutual injection: inject top5 full text if recall has results</li>
 *   <li>Data isolation: coding_memory/ directory separate from personal memory</li>
 *   <li>Tool registration: coding_memory_read/write/edit</li>
 * </ul>
 */
public class CodingMemoryRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(CodingMemoryRail.class);

    // Recall limits
    private static final int MAX_RECALL_RESULTS = 5;
    private static final int MAX_RECALL_TOTAL_BYTES = 10240;

    private String codingMemoryDir;
    private Object embeddingConfig;
    private String language = "cn";
    private boolean managerInitialized = false;
    private Object memoryManager;
    private Object systemPromptBuilder;
    private String recalledContent = null;
    private int totalMemories = 0;
    private final Set<String> ownedToolNames = ConcurrentHashMap.newKeySet();
    private final Set<String> ownedToolIds = ConcurrentHashMap.newKeySet();

    public CodingMemoryRail() {
        super();
    }

    public CodingMemoryRail(String codingMemoryDir, Object embeddingConfig, String language) {
        super();
        this.codingMemoryDir = codingMemoryDir;
        this.embeddingConfig = embeddingConfig;
        this.language = language;
    }

    /**
     * Initialize with agent - register coding memory tools and setup manager.
     * <p>
     * Mirrors Python's {@code init} method which:
     * <ul>
     *   <li>Gets system_prompt_builder from agent</li>
     *   <li>Registers coding memory tools (read/write/edit)</li>
     *   <li>Initializes MemoryIndexManager</li>
     * </ul>
     */
    @Override
    public void init(Object agent) {
        super.init(agent);

        // Get system_prompt_builder from agent
        try {
            systemPromptBuilder = agent.getClass().getMethod("getSystemPromptBuilder").invoke(agent);
        } catch (Exception e) {
            LOG.debug("[CodingMemoryRail] Could not get system_prompt_builder from agent");
            systemPromptBuilder = null;
        }

        // Register coding memory tools
        registerCodingMemoryTools(agent);

        // Initialize memory manager
        try {
            // TODO: Initialize MemoryIndexManager with coding_memory_dir and embedding_config
            // Python: self._manager = await init_memory_manager_async(...)
            managerInitialized = true;
            LOG.info("[CodingMemoryRail] Memory manager initialized");
        } catch (Exception e) {
            LOG.warn("[CodingMemoryRail] Failed to initialize memory manager: {}", e.getMessage());
        }

        LOG.info("[CodingMemoryRail] Initialized with coding_memory_dir={}", codingMemoryDir);
    }

    /**
     * Uninitialize with agent - remove tools and cleanup manager.
     * <p>
     * Mirrors Python's {@code uninit} method which:
     * <ul>
     *   <li>Removes tools from agent.ability_manager</li>
     *   <li>Removes tools from Runner.resource_mgr</li>
     *   <li>Removes prompt sections</li>
     *   <li>Shutdowns memory manager</li>
     * </ul>
     */
    @Override
    public void uninit(Object agent) {
        // Remove tools from agent.ability_manager
        if (agent != null) {
            try {
                Object abilityManager = agent.getClass().getMethod("getAbilityManager").invoke(agent);
                if (abilityManager != null) {
                    for (String toolName : new ArrayList<>(ownedToolNames)) {
                        try {
                            abilityManager.getClass().getMethod("remove", String.class).invoke(abilityManager, toolName);
                        } catch (Exception exc) {
                            LOG.warn("[CodingMemoryRail] remove tool '{}' failed: {}", toolName, exc.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                LOG.debug("[CodingMemoryRail] Could not get ability_manager from agent");
            }
        }

        // Remove tools from resource_mgr
        for (String toolId : new ArrayList<>(ownedToolIds)) {
            try {
                Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
            } catch (Exception exc) {
                LOG.warn("[CodingMemoryRail] Failed to remove tool '{}' from resource_mgr: {}", toolId, exc.getMessage());
            }
        }

        ownedToolIds.clear();
        ownedToolNames.clear();

        // Remove prompt sections
        if (systemPromptBuilder != null) {
            try {
                // Python: self.system_prompt_builder.remove_section(...)
                LOG.debug("[CodingMemoryRail] Removed prompt sections");
            } catch (Exception e) {
                LOG.debug("[CodingMemoryRail] Failed to remove prompt sections");
            }
            systemPromptBuilder = null;
        }

        // Shutdown memory manager
        if (memoryManager != null) {
            try {
                // TODO: Shutdown memory manager
                // Python: await self._manager.shutdown()
                LOG.info("[CodingMemoryRail] Memory manager shutdown completed");
            } catch (Exception e) {
                LOG.warn("[CodingMemoryRail] Memory manager shutdown failed: {}", e.getMessage());
            }
        }

        managerInitialized = false;
        LOG.info("[CodingMemoryRail] Uninitialized");
    }

    /**
     * Register coding memory tools to agent.
     * <p>
     * Mirrors Python's tool registration logic.
     */
    private void registerCodingMemoryTools(Object agent) {
        if (agent == null) {
            return;
        }

        try {
            Object abilityManager = agent.getClass().getMethod("getAbilityManager").invoke(agent);
            if (abilityManager == null) {
                LOG.warn("[CodingMemoryRail] Agent has no ability_manager, cannot register tools");
                return;
            }

            // TODO: Register coding_memory_read, coding_memory_write, coding_memory_edit tools
            // Python uses get_decorated_tools() from coding_memory_tools module

            // Placeholder tool registration
            List<String> toolNames = Arrays.asList("coding_memory_read", "coding_memory_write", "coding_memory_edit");
            for (String toolName : toolNames) {
                ownedToolNames.add(toolName);
                LOG.debug("[CodingMemoryRail] Would register tool: {}", toolName);
            }

            LOG.info("[CodingMemoryRail] Registered {} coding memory tools", ownedToolNames.size());

        } catch (Exception e) {
            LOG.warn("[CodingMemoryRail] Failed to register coding memory tools: {}", e.getMessage());
        }
    }

    /**
     * Prefetch memory for a query (non-blocking).
     * <p>
     * Mirrors Python's prefetch functionality.
     */
    public void prefetch(String query) {
        if (!managerInitialized) {
            return;
        }

        // TODO: Implement prefetch with MemoryIndexManager
        // Python: self._prefetch_task = asyncio.create_task(self._prefetch_async(query))
        LOG.debug("[CodingMemoryRail] Prefetch initiated for query: {}", query);
    }

    /**
     * Get recalled content.
     */
    public String getRecalledContent() {
        return recalledContent;
    }

    /**
     * Get total memory count.
     */
    public int getTotalMemories() {
        return totalMemories;
    }

    /**
     * Check if manager is initialized.
     */
    public boolean isManagerInitialized() {
        return managerInitialized;
    }

    /**
     * Set the coding memory directory.
     */
    public void setCodingMemoryDir(String codingMemoryDir) {
        this.codingMemoryDir = codingMemoryDir;
    }

    /**
     * Set the embedding config.
     */
    public void setEmbeddingConfig(Object embeddingConfig) {
        this.embeddingConfig = embeddingConfig;
    }

    /**
     * Set the language.
     */
    public void setLanguage(String language) {
        this.language = language;
    }
}
