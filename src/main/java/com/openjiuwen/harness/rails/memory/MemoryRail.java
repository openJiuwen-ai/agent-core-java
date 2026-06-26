/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.MemorySection;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Personal memory rail state holder and prompt injector.
 *
 * <p>Mirrors Python's {@code MemoryRail} in
 * {@code openjiuwen/harness/rails/memory/memory_rail.py}.</p>
 */
public class MemoryRail extends DeepAgentRail {

    private final Object embeddingConfig;
    private final boolean proactive;
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();
    private boolean initialized;
    private boolean managerInitialized;
    private boolean readOnly;
    private Object toolContext;
    private String language = "cn";

    public MemoryRail(Object embeddingConfig) {
        this(embeddingConfig, true);
    }

    public MemoryRail(Object embeddingConfig, boolean proactive) {
        setPriority(80);
        this.embeddingConfig = embeddingConfig;
        this.proactive = proactive;
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        if (agent != null && agent.deepConfig() != null) {
            language = agent.deepConfig().getLanguage();
        }
        registerMemoryTools(agent);
    }

    @Override
    public void uninit(DeepAgent agent) {
        ownedToolNames.clear();
        ownedToolIds.clear();
        initialized = false;
        managerInitialized = false;
        toolContext = null;
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        if (!initialized) {
            managerInitialized = embeddingConfig != null;
            toolContext = ctx.getValues().getOrDefault("memory_tool_context", toolContext);
            initialized = true;
        }
        Object runKind = ctx.getValues().getOrDefault("run_kind", "");
        readOnly = "cron".equals(String.valueOf(runKind)) || "heartbeat".equals(String.valueOf(runKind));
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        String resolvedLanguage = String.valueOf(ctx.getValues().getOrDefault("language", language));
        ctx.put("memory_section", MemorySection.buildMemorySection(resolvedLanguage, readOnly, proactive));
        ctx.put("memory_manager_initialized", managerInitialized);
    }

    public Object getEmbeddingConfig() {
        return embeddingConfig;
    }

    public boolean isProactive() {
        return proactive;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isManagerInitialized() {
        return managerInitialized;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public Set<String> getOwnedToolNames() {
        return new LinkedHashSet<>(ownedToolNames);
    }

    public Set<String> getOwnedToolIds() {
        return new LinkedHashSet<>(ownedToolIds);
    }

    protected void registerMemoryTools(DeepAgent agent) {
        if (agent == null) {
            return;
        }
        Object names = agent.deepConfig() == null ? null : agent.deepConfig().getModelSelection().get("memory_tools");
        if (names instanceof Iterable<?> iterable) {
            for (Object name : iterable) {
                String toolName = String.valueOf(name).trim();
                if (!toolName.isEmpty()) {
                    ownedToolNames.add(toolName);
                }
            }
        }
    }
}
