/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

/**
 * Parameters for releasing stale KV cache on the inference server.
 * <p>
 * Bundles the previous context window state that an inference-affinity
 * client needs to issue a {@code /release_kv_cache} request. Replaces the
 * former six-argument {@code release(...)} signature so call sites stay
 * readable when the release contract grows.
 * <p>
 * Mirrors the Python {@code KVCacheManager.release()} arguments passed down
 * to {@code Model.release()} / {@code InferenceAffinityModel.release()}.
 *
 * @param sessionId sessionId used as cache_salt
 * @param messages messages from the previous context window
 * @param messagesReleasedIndex first modified message index
 * @param tools tools from the previous context window (may be {@code null})
 * @param toolsReleasedIndex first modified tool index (may be {@code null})
 * @param model model name override (falls back to configured model when {@code null})
 * @since 0.1.7
 */
public record KvCacheReleaseRequest(String sessionId, Object messages, int messagesReleasedIndex, Object tools,
        Integer toolsReleasedIndex, String model) {
}
