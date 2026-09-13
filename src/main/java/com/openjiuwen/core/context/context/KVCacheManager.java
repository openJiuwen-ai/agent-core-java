/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.foundation.llm.InferenceAffinityModel;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.KvCacheReleaseRequest;
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
 * Supports two release paths matching Python's duck-typing:
 * (1) standalone {@link InferenceAffinityModel} (direct caller);
 * (2) {@link Model} wrapping an {@code InferenceAffinityModelClient} via factory
 * (the common path from {@code ReActAgent}).
 * 
 * @since 0.1.7
 */
public class KVCacheManager {
    private final String sessionId;
    private ContextWindow lastContextWindow;

    /**
     * KVCacheManager.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public KVCacheManager(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Check and release stale KV cache if the context window has changed.
     * <p>
     * Convenience overload without a model — comparison runs but no HTTP
     * release is issued. Use {@link #release(ContextWindow, Object)} to
     * trigger the actual release.
     *
     * @param contextWindow the current context window
     * @since 0.1.7
     */
    public void release(ContextWindow contextWindow) {
        release(contextWindow, null);
    }

    /**
     * Check and release stale KV cache if the context window has changed and a model
     * with release capability is provided.
     * 
     * @param contextWindow the current context window
     * @param model optional model instance
     * @since 0.1.7
     */
    public void release(ContextWindow contextWindow, Object model) {
        if (lastContextWindow == null) {
            lastContextWindow = contextWindow;
            return;
        }

        ReleaseCheckResult result = checkReleaseNeeded(contextWindow);

        if (result.shouldRelease && (result.messagesReleasedIndex != null || result.toolsReleasedIndex != null)) {
            if (model instanceof InferenceAffinityModel inferenceAffinityModel) {
                Loggers.CONTEXT_ENGINE.info("KV cache release triggered for session " + sessionId + " (msg_idx="
                        + result.messagesReleasedIndex + ", tool_idx=" + result.toolsReleasedIndex + ")");
                KvCacheReleaseRequest request = buildReleaseRequest(lastContextWindow, result);
                try {
                    inferenceAffinityModel.release(request);
                } catch (Exception e) {
                    Loggers.CONTEXT_ENGINE.warning("Failed to release inference-affinity KV cache: " + e.getMessage());
                }
            } else if (model instanceof Model llmModel && llmModel.supportsKvCacheRelease()) {
                Loggers.CONTEXT_ENGINE.info("KV cache release triggered for session " + sessionId + " (msg_idx="
                        + result.messagesReleasedIndex + ", tool_idx=" + result.toolsReleasedIndex + ")");
                KvCacheReleaseRequest request = buildReleaseRequest(lastContextWindow, result);
                try {
                    llmModel.release(request);
                } catch (Exception e) {
                    Loggers.CONTEXT_ENGINE.warning("Failed to release KV cache via Model: " + e.getMessage());
                }
            } else {
                Loggers.CONTEXT_ENGINE.info("Context diff detected for session " + sessionId
                        + " but model does not support KV cache release; skipped.");
            }
        }

        lastContextWindow = contextWindow;
    }

    /**
     * Build the release request from the last context window and check result.
     *
     * @param lastWindow the previous context window
     * @param result the release check result carrying the modified indices
     * @return the assembled release request
     * @since 0.1.7
     */
    private KvCacheReleaseRequest buildReleaseRequest(ContextWindow lastWindow, ReleaseCheckResult result) {
        int messagesIndex = result.messagesReleasedIndex != null ? result.messagesReleasedIndex : 0;
        return new KvCacheReleaseRequest(sessionId, lastWindow.getMessages(), messagesIndex,
                lastWindow.getToolList(), result.toolsReleasedIndex, null);
    }

    /**
     * checkReleaseNeeded.
     * 
     * @param contextWindow contextWindow
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * ReleaseCheckResult.
     * 
     * @param shouldRelease shouldRelease
     * @param messagesReleasedIndex messagesReleasedIndex
     * @param toolsReleasedIndex toolsReleasedIndex
     * @since 0.1.7
     */
    private record ReleaseCheckResult(boolean shouldRelease, Integer messagesReleasedIndex,
            Integer toolsReleasedIndex) {
    }
}
