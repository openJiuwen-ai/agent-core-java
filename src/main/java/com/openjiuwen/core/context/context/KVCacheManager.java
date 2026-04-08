/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.foundation.llm.InferenceAffinityModel;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;

/**
 * Manages KV cache release for inference-affinity models.
 * <p>
 * Tracks the last context window and detects changes that require
 * releasing stale KV cache entries on the inference server.
 * <p>
 * Mirrors Python's {@code KVCacheManager} from {@code context_engine/context/kv_cache_manager.py}.
 * <p>
 * Note: The actual release call depends on the InferenceAffinityModel interface,
 * which may not yet be implemented in Java. The comparison logic is fully ported.
 */
public class KVCacheManager {

    private final String sessionId;
    private ContextWindow lastContextWindow;

    public KVCacheManager(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Check and release stale KV cache if the context window has changed.
     * <p>
     * In the Python version, this calls {@code model.release()} on an
     * InferenceAffinityModel. In Java, the actual release is a no-op
     * until InferenceAffinityModel is implemented.
     *
     * @param contextWindow the current context window
     */
    public void release(ContextWindow contextWindow) {
        release(contextWindow, null);
    }

    /**
     * Check and release stale KV cache if the context window has changed and a model
     * with release capability is provided.
     *
     * @param contextWindow the current context window
     * @param model         optional model instance
     */
    public void release(ContextWindow contextWindow, Object model) {
        if (lastContextWindow == null) {
            lastContextWindow = contextWindow;
            return;
        }

        ReleaseCheckResult result = checkReleaseNeeded(contextWindow);

        if (result.shouldRelease
                && (result.messagesReleasedIndex != null || result.toolsReleasedIndex != null)) {
            Loggers.CONTEXT_ENGINE.info(
                    "KV cache release triggered for session " + sessionId
                            + " (msg_idx=" + result.messagesReleasedIndex
                            + ", tool_idx=" + result.toolsReleasedIndex + ")");
            if (model instanceof InferenceAffinityModel inferenceAffinityModel) {
                try {
                    inferenceAffinityModel.release(
                            sessionId,
                            lastContextWindow.getMessages(),
                            result.messagesReleasedIndex != null ? result.messagesReleasedIndex : 0,
                            lastContextWindow.getToolList(),
                            result.toolsReleasedIndex,
                            null
                    );
                } catch (Exception e) {
                    Loggers.CONTEXT_ENGINE.warning("Failed to release inference-affinity KV cache: " + e.getMessage());
                }
            }
        }

        lastContextWindow = contextWindow;
    }

    private ReleaseCheckResult checkReleaseNeeded(ContextWindow contextWindow) {
        boolean shouldRelease = false;
        Integer msgIdx = null;
        Integer toolIdx = null;

        List<BaseMessage> prevMsgs = lastContextWindow.getMessages();
        List<BaseMessage> currMsgs = contextWindow.getMessages();

        if (prevMsgs != null && !prevMsgs.isEmpty()) {
            msgIdx = prevMsgs.size();
            int minLen = Math.min(prevMsgs.size(), currMsgs != null ? currMsgs.size() : 0);
            for (int idx = 0; idx < minLen; idx++) {
                if (!prevMsgs.get(idx).equals(currMsgs.get(idx))) {
                    shouldRelease = true;
                    msgIdx = idx;
                    Loggers.CONTEXT_ENGINE.info("  [RELEASE REASON] Message modified at index " + idx);
                    break;
                }
            }
        }

        List<ToolInfo> prevTools = lastContextWindow.getToolList();
        List<ToolInfo> currTools = contextWindow.getToolList();

        if (prevTools != null && !prevTools.isEmpty()) {
            toolIdx = prevTools.size();
            int minLen = Math.min(prevTools.size(), currTools != null ? currTools.size() : 0);
            for (int idx = 0; idx < minLen; idx++) {
                if (!prevTools.get(idx).equals(currTools.get(idx))) {
                    shouldRelease = true;
                    toolIdx = idx;
                    Loggers.CONTEXT_ENGINE.info("  [RELEASE REASON] Tool modified at index " + idx);
                    break;
                }
            }
        }

        return new ReleaseCheckResult(shouldRelease, msgIdx, toolIdx);
    }

    private record ReleaseCheckResult(boolean shouldRelease, Integer messagesReleasedIndex,
                                      Integer toolsReleasedIndex) {
    }
}
