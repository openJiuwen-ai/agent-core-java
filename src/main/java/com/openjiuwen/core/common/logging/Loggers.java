/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

/**
 * Pre-defined loggers for each module — lazy-initialized singletons.
 * <p>
 * Java equivalent of the module-level logger instances in Python's {@code __init__.py}.
 * 
 * @since 0.1.7
 */
public final class Loggers {
    /**
     * Loggers.
     * 
     * @since 0.1.7
     */
    private Loggers() {
    }

    // ========== General Loggers ==========

    /**
     * COMMON.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol COMMON = new LazyLogger(() -> LogManager.getLogger("common"));

    /**
     * INTERFACE.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol INTERFACE = new LazyLogger(() -> LogManager.getLogger("interface"));

    /**
     * PERFORMANCE.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol PERFORMANCE = new LazyLogger(() -> LogManager.getLogger("performance"));

    /**
     * PROMPT_BUILDER.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol PROMPT_BUILDER = new LazyLogger(() -> LogManager.getLogger("prompt_builder"));

    // ========== Core Module Loggers ==========

    /**
     * AGENT.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol AGENT = new LazyLogger(() -> LogManager.getLogger("agent"));

    /**
     * MULTI_AGENT.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol MULTI_AGENT = new LazyLogger(() -> LogManager.getLogger("multi_agent"));

    /**
     * WORKFLOW.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol WORKFLOW = new LazyLogger(() -> LogManager.getLogger("workflow"));

    /**
     * SESSION.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol SESSION = new LazyLogger(() -> LogManager.getLogger("session"));

    /**
     * CONTROLLER.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol CONTROLLER = new LazyLogger(() -> LogManager.getLogger("controller"));

    /**
     * RUNNER.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol RUNNER = new LazyLogger(() -> LogManager.getLogger("runner"));

    /**
     * SYS_OPERATION.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol SYS_OPERATION = new LazyLogger(() -> LogManager.getLogger("sys_operation"));

    // ========== Foundation Module Loggers ==========

    /**
     * LLM.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol LLM = new LazyLogger(() -> LogManager.getLogger("llm"));

    /**
     * TOOL.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol TOOL = new LazyLogger(() -> LogManager.getLogger("tool"));

    /**
     * PROMPT.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol PROMPT = new LazyLogger(() -> LogManager.getLogger("prompt"));

    /**
     * STORE.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol STORE = new LazyLogger(() -> LogManager.getLogger("store"));

    // ========== Data and Retrieval Module Loggers ==========

    /**
     * MEMORY.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol MEMORY = new LazyLogger(() -> LogManager.getLogger("memory"));

    /**
     * RETRIEVAL.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol RETRIEVAL = new LazyLogger(() -> LogManager.getLogger("retrieval"));

    /**
     * CONTEXT_ENGINE.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol CONTEXT_ENGINE = new LazyLogger(() -> LogManager.getLogger("context_engine"));

    // ========== Execution and Graph Module Loggers ==========

    /**
     * GRAPH.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol GRAPH = new LazyLogger(() -> LogManager.getLogger("graph"));

    /**
     * OPERATOR.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol OPERATOR = new LazyLogger(() -> LogManager.getLogger("operator"));

    // ========== Protocol and Extension Module Loggers ==========

    /**
     * MCP.
     * 
     * @since 0.1.7
     */
    public static final LoggerProtocol MCP = new LazyLogger(() -> LogManager.getLogger("mcp"));
}
