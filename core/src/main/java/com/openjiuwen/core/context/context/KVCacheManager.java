/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.context.ContextWindow;
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

    /**
     * Compare the previous LLM-bound window with the next one without mutating
     * any tracked state.
     *
     * <p>Mirrors Python's {@code detect_context_window_change} plus
     * {@code first_changed_index}: append-only sequences are treated as
     * unchanged, and appending a tool invalidates the old message suffix that
     * follows the system prompt (tools serialize right after the system
     * prompt, so appended tools move every following conversation token).</p>
     *
     * @param previousWindow window previously sent to the LLM, may be {@code null}
     * @param nextWindow window about to be sent
     * @return the change decision; empty when there was no previous window
     * @since 0.1.16
     */
    public static java.util.Optional<ReleaseDecision> firstChangedDecision(
            ContextWindow previousWindow, ContextWindow nextWindow) {
        if (previousWindow == null) {
            return java.util.Optional.empty();
        }
        List<BaseMessage> oldMessages = previousWindow.getMessages();
        List<BaseMessage> newMessages = nextWindow == null ? List.of() : nextWindow.getMessages();
        List<ToolInfo> oldTools = previousWindow.getTools();
        List<ToolInfo> newTools = nextWindow == null ? List.of() : nextWindow.getTools();
        java.util.OptionalInt msgStart = firstChangedIndex(oldMessages.size(), index ->
                !oldMessages.get(index).equals(newMessages.get(index)));
        java.util.OptionalInt toolsStart = firstChangedIndex(oldTools.size(), index ->
                !oldTools.get(index).equals(newTools.get(index)));

        boolean isToolsAppended = newTools.size() > oldTools.size()
                && newTools.subList(0, oldTools.size()).equals(oldTools);
        if (isToolsAppended) {
            int firstContextMessage = previousWindow.getSystemMessages().size();
            if (firstContextMessage < oldMessages.size()) {
                if (msgStart.isEmpty() || msgStart.getAsInt() > firstContextMessage) {
                    msgStart = java.util.OptionalInt.of(firstContextMessage);
                }
            }
            toolsStart = java.util.OptionalInt.empty();
        }
        if (msgStart.isEmpty() && toolsStart.isEmpty()) {
            return java.util.Optional.of(new ReleaseDecision(false, null, null));
        }
        return java.util.Optional.of(new ReleaseDecision(true,
                msgStart.isEmpty() ? null : msgStart.getAsInt(),
                toolsStart.isEmpty() ? null : toolsStart.getAsInt()));
    }

    private static java.util.OptionalInt firstChangedIndex(int oldSize, java.util.function.IntPredicate changedAt) {
        for (int index = 0; index < oldSize; index++) {
            if (changedAt.test(index)) {
                return java.util.OptionalInt.of(index);
            }
        }
        return java.util.OptionalInt.empty();
    }
}
