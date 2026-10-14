/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;

/**
 * Tracks the previous model context window and releases stale KV cache prefixes.
 *
 * <p>Mirrors Python's {@code KVCacheManager} in
 * {@code openjiuwen/core/context_engine/context/kv_cache_manager.py}.</p>
 */
public class KVCacheManager {

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

    /**
     * Window change decision: whether cached prefixes were invalidated and the
     * half-open ranges (message/tool indexes) where the change starts.
     *
     * <p>Mirrors Python's {@code _check_release_needed} tuple in
     * {@code openjiuwen/core/context_engine/context/kv_cache_manager.py}.</p>
     */
    public record ReleaseDecision(boolean shouldRelease, Integer messagesReleasedIndex,
                                  Integer toolsReleasedIndex) {
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
