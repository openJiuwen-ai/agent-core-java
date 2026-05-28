/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.multi_agent;

/**
 * TeamRuntime Hybrid Communication Example - Documentation placeholder.
 * <p>
 * Combines P2P and Pub-Sub patterns for flexible multi-agent coordination.
 * <p>
 * Mirrors Python's {@code runtime_hybrid} in
 * {@code examples.multi_agent.runtime_hybrid}.
 * <p>
 * Pattern:
 * <pre>
 * Main → Orchestrator (P2P)
 * Orchestrator → Executors (Pub-Sub)
 * Executors → Aggregator (Pub-Sub)
 * Main → Reporter (P2P)
 * </pre>
 */
public final class RuntimeHybridExample {

    private RuntimeHybridExample() {}

    public static final String PATTERN = "HYBRID";
}