/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.harness.prompts.sections.ExternalMemorySection;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * External-memory provider adapter rail.
 *
 * <p>Mirrors Python's {@code ExternalMemoryRail} in
 * {@code openjiuwen/harness/rails/memory/external_memory_rail.py}.</p>
 */
public class ExternalMemoryRail extends DeepAgentRail {

    public static final String EXTERNAL_MEMORY_PREFETCH_SECTION = "external_memory_prefetch";
    public static final double PREFETCH_TIMEOUT = 5.0;

    private final Object provider;
    private final String userId;
    private final String scopeId;
    private final String sessionId;
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();
    private boolean initialized;
    private String prefetchCache;
    private int syncConsecutiveFailures;

    public ExternalMemoryRail(Object provider) {
        this(provider, "__default__", "__default__", "__default__");
    }

    public ExternalMemoryRail(Object provider, String userId, String scopeId, String sessionId) {
        setPriority(75);
        this.provider = provider;
        this.userId = defaultId(userId);
        this.scopeId = defaultId(scopeId);
        this.sessionId = defaultId(sessionId);
    }

    @Override
    public void init(com.openjiuwen.harness.DeepAgent agent) {
        super.init(agent);
        if (provider != null) {
            initialized = true;
        }
    }

    @Override
    public void uninit(com.openjiuwen.harness.DeepAgent agent) {
        ownedToolNames.clear();
        ownedToolIds.clear();
        initialized = false;
        prefetchCache = null;
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        prefetchCache = null;
        if (ctx.get("external_memory_prefetch") != null) {
            prefetchCache = String.valueOf(ctx.get("external_memory_prefetch"));
        }
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (!initialized || prefetchCache == null || prefetchCache.isBlank()) {
            return;
        }
        String language = String.valueOf(ctx.getValues().getOrDefault("language", "cn"));
        ctx.put(EXTERNAL_MEMORY_PREFETCH_SECTION, ExternalMemorySection.buildExternalMemorySection(
                buildMemoryContextBlock(prefetchCache),
                language
        ));
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        Object failure = ctx.get("external_memory_sync_failed");
        if (Boolean.TRUE.equals(failure)) {
            syncConsecutiveFailures += 1;
        } else if (ctx.get("external_memory_sync_result") != null) {
            syncConsecutiveFailures = 0;
        }
    }

    public Object getProvider() {
        return provider;
    }

    public String getUserId() {
        return userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getSyncConsecutiveFailures() {
        return syncConsecutiveFailures;
    }

    public static String buildMemoryContextBlock(String rawContext) {
        return "<memory-context>\n"
                + "[System note: recalled memory context from long-term memory, NOT new user input.]\n\n"
                + (rawContext == null ? "" : rawContext)
                + "\n</memory-context>";
    }

    private static String defaultId(String value) {
        return value == null || value.isBlank() ? "__default__" : value;
    }
}
