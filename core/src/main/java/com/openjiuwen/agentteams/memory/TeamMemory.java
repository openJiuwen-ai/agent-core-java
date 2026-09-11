/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.memory;

import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.io.IOException;

/**
 * Optional team memory integration used by the Agent Teams runtime.
 *
 * @since 0.1.7
 */
public interface TeamMemory {
    /**
     * Initializes memory tools and injects relevant memory for a coordination round.
     *
     * @param deepAgent configured team agent
     * @param query current user query
     * @throws IOException when memory data cannot be initialized or read
     * @since 0.1.7
     */
    void startRound(DeepAgent deepAgent, String query) throws IOException;

    /**
     * Persists memory produced during the completed coordination round.
     *
     * @throws IOException when memory data cannot be persisted
     * @since 0.1.7
     */
    void finishRound() throws IOException;

    /**
     * Releases memory resources and removes registered tools.
     *
     * @since 0.1.7
     */
    void close();
}
