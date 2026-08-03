/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.context;

import com.openjiuwen.core.context_engine.ContextWindow;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Tracks the previous model context window and releases stale KV cache prefixes.
 *
 * <p>Mirrors Python's {@code KVCacheManager} in
 * {@code openjiuwen/core/context_engine/context/kv_cache_manager.py}.</p>
 */
public class KVCacheManager implements SessionModelContext.KvCacheManagerPort {
    private final String sessionId;
    private ContextWindow lastContextWindow;

    public KVCacheManager(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public void release(ContextWindow contextWindow, Object model) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("model", model);
        release(contextWindow, kwargs).toCompletableFuture().join();
    }

    public CompletionStage<Void> release(ContextWindow contextWindow, Map<String, Object> kwargs) {
        Object model = kwargs == null ? null : kwargs.get("model");
        if (model == null || !canRelease(model)) {
            return CompletableFuture.completedFuture(null);
        }
        if (lastContextWindow == null) {
            lastContextWindow = contextWindow;
            return CompletableFuture.completedFuture(null);
        }

        ReleaseDecision decision = checkReleaseNeeded(contextWindow);
        if (decision.shouldRelease()
                && (decision.messagesReleasedIndex() != null || decision.toolsReleasedIndex() != null)) {
            List<ToolInfo> tools = decision.toolsReleasedIndex() == null ? null : lastContextWindow.getTools();
            invokeRelease(model, sessionId, lastContextWindow.getMessages(), decision.messagesReleasedIndex(),
                    tools, decision.toolsReleasedIndex());
        }
        lastContextWindow = contextWindow;
        return CompletableFuture.completedFuture(null);
    }

    public ReleaseDecision checkReleaseNeeded(ContextWindow contextWindow) {
        boolean shouldRelease = false;
        Integer messageIndex = null;
        Integer toolIndex = null;

        List<BaseMessage> previousMessages = lastContextWindow == null ? List.of() : lastContextWindow.getMessages();
        List<BaseMessage> currentMessages = contextWindow == null ? List.of() : contextWindow.getMessages();
        if (!previousMessages.isEmpty()) {
            messageIndex = previousMessages.size();
            for (int index = 0; index < Math.min(previousMessages.size(), currentMessages.size()); index++) {
                if (!previousMessages.get(index).equals(currentMessages.get(index))) {
                    shouldRelease = true;
                    messageIndex = index;
                    break;
                }
            }
        }

        List<ToolInfo> previousTools = lastContextWindow == null ? List.of() : lastContextWindow.getTools();
        List<ToolInfo> currentTools = contextWindow == null ? List.of() : contextWindow.getTools();
        if (!previousTools.isEmpty()) {
            toolIndex = previousTools.size();
            for (int index = 0; index < Math.min(previousTools.size(), currentTools.size()); index++) {
                if (!previousTools.get(index).equals(currentTools.get(index))) {
                    shouldRelease = true;
                    toolIndex = index;
                    break;
                }
            }
        }

        return new ReleaseDecision(shouldRelease, messageIndex, toolIndex);
    }

    public ContextWindow lastContextWindow() {
        return lastContextWindow;
    }

    private static boolean canRelease(Object model) {
        if (model instanceof ReleaseCapableModel) {
            return true;
        }
        return findReleaseMethod(model) != null;
    }

    private static void invokeRelease(Object model, String sessionId, List<BaseMessage> messages,
                                      Integer messagesReleasedIndex, List<ToolInfo> tools,
                                      Integer toolsReleasedIndex) {
        if (model instanceof ReleaseCapableModel releaseCapableModel) {
            releaseCapableModel.release(sessionId, messages, messagesReleasedIndex, tools, toolsReleasedIndex)
                    .toCompletableFuture()
                    .join();
            return;
        }
        Method method = findReleaseMethod(model);
        if (method == null) {
            return;
        }
        try {
            method.invoke(model, sessionId, messages, messagesReleasedIndex, tools, toolsReleasedIndex);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            // Python awaits release and otherwise ignores its return value here.
        }
    }

    private static Method findReleaseMethod(Object model) {
        if (model == null) {
            return null;
        }
        try {
            return model.getClass().getMethod("release", String.class, List.class, Integer.class, List.class,
                    Integer.class);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /**
     * Narrow model release adapter.
     *
     * <p>Mirrors Python's {@code model.release(...)} callback in
     * {@code openjiuwen/core/context_engine/context/kv_cache_manager.py}.</p>
     */
    public interface ReleaseCapableModel {
        CompletionStage<Boolean> release(String sessionId, List<BaseMessage> messages, Integer messagesReleasedIndex,
                                         List<ToolInfo> tools, Integer toolsReleasedIndex);
    }

    /**
     * Release decision returned by {@link #checkReleaseNeeded(ContextWindow)}.
     *
     * <p>Mirrors Python's {@code _check_release_needed} tuple in
     * {@code openjiuwen/core/context_engine/context/kv_cache_manager.py}.</p>
     */
    public record ReleaseDecision(boolean shouldRelease, Integer messagesReleasedIndex,
                                  Integer toolsReleasedIndex) {
    }
}
