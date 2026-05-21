/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Rail that integrates memory tools and injects memory usage prompts.
 * <p>
 * Registers memory-related tools to the agent and injects a memory
 * usage prompt section into the system prompt.
 * <p>
 * Mirrors Python's {@code MemoryRail} in
 * {@code openjiuwen.harness.rails.memory.memory_rail}.
 */
public class MemoryRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryRail.class);

    public static final int PRIORITY = 80;

    private final boolean isProactive;
    private boolean initialized = false;
    private final Set<String> ownedToolNames = new HashSet<>();
    private Object systemPromptBuilder;

    public MemoryRail() {
        this(true);
    }

    public MemoryRail(boolean isProactive) {
        super();
        this.isProactive = isProactive;
    }

    @Override
    public void init(Object agent) {
        if (initialized) {
            return;
        }

        try {
            this.systemPromptBuilder = agent.getClass().getMethod("getSystemPromptBuilder").invoke(agent);
        } catch (Exception e) {
            LOG.debug("[MemoryRail] Could not get system_prompt_builder");
        }

        // Inject memory prompt section
        if (systemPromptBuilder != null) {
            try {
                com.openjiuwen.core.single_agent.prompts.PromptSection section =
                        com.openjiuwen.harness.prompts.sections.MemorySection.build("cn", false, isProactive);
                systemPromptBuilder.getClass().getMethod("addSection",
                        com.openjiuwen.core.single_agent.prompts.PromptSection.class)
                        .invoke(systemPromptBuilder, section);
                LOG.info("[MemoryRail] Injected memory prompt section (proactive={})", isProactive);
            } catch (Exception e) {
                LOG.warn("[MemoryRail] Failed to inject memory section: {}", e.getMessage());
            }
        }

        initialized = true;
        LOG.info("[MemoryRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        if (systemPromptBuilder != null) {
            try {
                systemPromptBuilder.getClass().getMethod("removeSection", String.class)
                        .invoke(systemPromptBuilder, "memory");
            } catch (Exception e) {
                LOG.debug("[MemoryRail] Could not remove memory section");
            }
        }
        ownedToolNames.clear();
        initialized = false;
        LOG.info("[MemoryRail] Uninitialized");
    }
}
