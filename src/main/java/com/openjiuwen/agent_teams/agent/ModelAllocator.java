/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.List;
import java.util.Map;

/**
 * Allocates one pool entry per call.
 * <p>
 * Implementations encapsulate the policy for picking the next entry
 * (round-robin, weighted, least-recently-used, ...). Returning
 * null signals "no entry available" — callers fall back to the
 * member's per-agent model config.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_teams.agent.model_allocator.ModelAllocator}.
 */
public interface ModelAllocator {

    /**
     * Return the next allocation, or null when unavailable.
     *
     * @param modelName Optional model-name hint. Allocators that
     *                  select by name require it and return null when missing.
     *                  Allocators that ignore name accept it for compatibility.
     * @return Allocation or null
     */
    Allocation allocate(String modelName);

    /**
     * Return a JSON-friendly snapshot of allocator counters.
     *
     * @return State dictionary
     */
    Map<String, Object> stateDict();

    /**
     * Restore counters previously produced by stateDict.
     *
     * @param state State dictionary
     */
    void loadStateDict(Map<String, Object> state);
}