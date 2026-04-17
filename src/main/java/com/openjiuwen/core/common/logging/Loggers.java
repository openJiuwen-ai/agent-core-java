/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

/**
 * Pre-defined loggers for each module — lazy-initialized singletons.
 * <p>
 * Java equivalent of the module-level logger instances in Python's {@code __init__.py}.
 */
public final class Loggers {

    private Loggers() {
    }

    // ========== General Loggers ==========
    public static final LoggerProtocol COMMON = new LazyLogger(() -> LogManager.getLogger("common"));
    public static final LoggerProtocol INTERFACE = new LazyLogger(() -> LogManager.getLogger("interface"));
    public static final LoggerProtocol PERFORMANCE = new LazyLogger(() -> LogManager.getLogger("performance"));
    public static final LoggerProtocol PROMPT_BUILDER = new LazyLogger(() -> LogManager.getLogger("prompt_builder"));

    // ========== Core Module Loggers ==========
    public static final LoggerProtocol AGENT = new LazyLogger(() -> LogManager.getLogger("agent"));
    public static final LoggerProtocol MULTI_AGENT = new LazyLogger(() -> LogManager.getLogger("multi_agent"));
    public static final LoggerProtocol WORKFLOW = new LazyLogger(() -> LogManager.getLogger("workflow"));
    public static final LoggerProtocol SESSION = new LazyLogger(() -> LogManager.getLogger("session"));
    public static final LoggerProtocol CONTROLLER = new LazyLogger(() -> LogManager.getLogger("controller"));
    public static final LoggerProtocol RUNNER = new LazyLogger(() -> LogManager.getLogger("runner"));
    public static final LoggerProtocol SYS_OPERATION = new LazyLogger(() -> LogManager.getLogger("sys_operation"));

    // ========== Foundation Module Loggers ==========
    public static final LoggerProtocol LLM = new LazyLogger(() -> LogManager.getLogger("llm"));
    public static final LoggerProtocol TOOL = new LazyLogger(() -> LogManager.getLogger("tool"));
    public static final LoggerProtocol PROMPT = new LazyLogger(() -> LogManager.getLogger("prompt"));
    public static final LoggerProtocol STORE = new LazyLogger(() -> LogManager.getLogger("store"));

    // ========== Data and Retrieval Module Loggers ==========
    public static final LoggerProtocol MEMORY = new LazyLogger(() -> LogManager.getLogger("memory"));
    public static final LoggerProtocol RETRIEVAL = new LazyLogger(() -> LogManager.getLogger("retrieval"));
    public static final LoggerProtocol CONTEXT_ENGINE = new LazyLogger(() -> LogManager.getLogger("context_engine"));

    // ========== Execution and Graph Module Loggers ==========
    public static final LoggerProtocol GRAPH = new LazyLogger(() -> LogManager.getLogger("graph"));
    public static final LoggerProtocol OPERATOR = new LazyLogger(() -> LogManager.getLogger("operator"));

    // ========== Protocol and Extension Module Loggers ==========
    public static final LoggerProtocol MCP = new LazyLogger(() -> LogManager.getLogger("mcp"));
}
