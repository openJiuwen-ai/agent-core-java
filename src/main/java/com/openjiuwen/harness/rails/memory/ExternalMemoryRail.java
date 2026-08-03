/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.core.memory.external.MemoryProvider;
import com.openjiuwen.core.singleagent.rail.RunKind;
import com.openjiuwen.harness.prompts.sections.ExternalMemorySection;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * External-memory provider adapter rail.
 *
 * <p>Mirrors Python's {@code ExternalMemoryRail} in
 * {@code openjiuwen/harness/rails/memory/external_memory_rail.py}.</p>
 *
 * <p>Mirrors Python's {@code tests.unit_tests.harness.rails.test_external_memory_rail} in
 * {@code tests/unit_tests/harness/rails/test_external_memory_rail.py}.</p>
 */
public class ExternalMemoryRail extends DeepAgentRail {

    public static final String EXTERNAL_MEMORY_PREFETCH_SECTION = "external_memory_prefetch";
    public static final double PREFETCH_TIMEOUT = 5.0;

    private final MemoryProvider provider;
    private final String userId;
    private final String scopeId;
    private final String sessionId;
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();
    private boolean initialized;
    private String prefetchCache;
    private int syncConsecutiveFailures;
    private CompletableFuture<Void> syncTask;

    public ExternalMemoryRail(MemoryProvider provider) {
        this(provider, "__default__", "__default__", "__default__");
    }

    public ExternalMemoryRail(MemoryProvider provider, String userId, String scopeId, String sessionId) {
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
        if (!initialized && provider != null) {
            provider.initialize(Map.of(
                    "user_id", userId,
                    "scope_id", scopeId,
                    "session_id", sessionId
            )).join();
            initialized = true;
        }
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
        if (!initialized || provider == null || isBackgroundRun(ctx)) {
            return;
        }

        String query = resolveUserTextForMemory(ctx);
        String output = extractAssistantOutput(ctx);
        if (query.isBlank() || output.isBlank()) {
            return;
        }

        if (syncTask != null && !syncTask.isDone()) {
            syncTask.join();
        }
        syncTask = provider.syncTurn(query, output, Map.of(
                "user_id", userId,
                "scope_id", scopeId,
                "session_id", sessionId
        ));
        syncTask.whenComplete((ignored, error) -> {
            if (error == null) {
                syncConsecutiveFailures = 0;
            } else {
                syncConsecutiveFailures += 1;
            }
        });
    }

    public MemoryProvider getProvider() {
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

    public CompletableFuture<Void> getSyncTask() {
        return syncTask;
    }

    public static boolean isBackgroundRun(CallbackContext ctx) {
        Object runKind = ctx.get("run_kind");
        if (runKind == RunKind.HEARTBEAT || runKind == RunKind.CRON) {
            return true;
        }
        String value = Objects.toString(runKind, "").toLowerCase(Locale.ROOT);
        return RunKind.HEARTBEAT.getValue().equals(value) || RunKind.CRON.getValue().equals(value);
    }

    public static String resolveUserTextForMemory(CallbackContext ctx) {
        Object query = ctx.get("query");
        if (query instanceof String text && !text.trim().isEmpty()) {
            return text.trim();
        }

        List<?> messages = toList(ctx.get("messages"));
        List<?> reversed = new ArrayList<>(messages);
        Collections.reverse(reversed);
        for (Object message : reversed) {
            if (!"user".equals(messageValue(message, "role"))) {
                continue;
            }
            Object content = rawMessageValue(message, "content");
            if (content instanceof String contentText && !contentText.trim().isEmpty()) {
                return contentText.trim();
            }
            List<String> textParts = new ArrayList<>();
            for (Object part : toList(content)) {
                if ("text".equals(messageValue(part, "type"))) {
                    Object text = rawMessageValue(part, "text");
                    if (text instanceof String partText && !partText.trim().isEmpty()) {
                        textParts.add(partText.trim());
                    }
                }
            }
            if (!textParts.isEmpty()) {
                return String.join(" ", textParts).trim();
            }
        }
        return "";
    }

    public static String extractAssistantOutput(CallbackContext ctx) {
        Object result = ctx.get("result");
        if (result == null) {
            return "";
        }
        if (!(result instanceof Map<?, ?> resultMap)) {
            String text = Objects.toString(result, "");
            return text.trim();
        }

        for (String key : List.of("output", "message", "content", "text", "response")) {
            if (!resultMap.containsKey(key)) {
                continue;
            }
            Object value = resultMap.get(key);
            if (value instanceof String text && !text.trim().isEmpty()) {
                return text.trim();
            }
            if (value instanceof Map<?, ?> nested && nested.get("content") instanceof String content
                    && !content.trim().isEmpty()) {
                return content.trim();
            }
        }
        return "";
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

    private static List<?> toList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String messageValue(Object message, String key) {
        Object value = rawMessageValue(message, key);
        return value == null ? null : String.valueOf(value);
    }

    private static Object rawMessageValue(Object message, String key) {
        if (message instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }
}
