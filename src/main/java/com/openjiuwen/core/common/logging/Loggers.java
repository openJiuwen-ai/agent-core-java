// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.logging;

/**
 * 预定义的Logger实例集合
 * 
 * <p>提供各模块专用的日志器，便于日志管理和调试。
 * 所有logger都使用延迟初始化，仅在首次使用时创建。
 * 
 * <p>对应 Python: __init__.py 中的模块级logger变量
 * 
 * @since 0.1.4
 */
public final class Loggers {
    
    private Loggers() {
        // 工具类，禁止实例化
    }
    
    // ========== 通用Logger ==========
    
    /**
     * 通用logger - 用于通用日志记录
     */
    public static final LoggerProtocol COMMON = 
        new LazyLogger(() -> LogManager.getLogger("common"));
    
    /**
     * 接口logger - 用于接口调用日志
     */
    public static final LoggerProtocol INTERFACE = 
        new LazyLogger(() -> LogManager.getLogger("interface"));
    
    /**
     * 性能logger - 用于性能指标日志
     */
    public static final LoggerProtocol PERFORMANCE = 
        new LazyLogger(() -> LogManager.getLogger("performance"));
    
    /**
     * Prompt构建器logger - 用于prompt构建相关日志
     */
    public static final LoggerProtocol PROMPT_BUILDER = 
        new LazyLogger(() -> LogManager.getLogger("prompt_builder"));
    
    // ========== 核心模块Logger ==========
    
    /**
     * Agent模块logger - 用于单Agent相关日志
     */
    public static final LoggerProtocol AGENT = 
        new LazyLogger(() -> LogManager.getLogger("agent"));
    
    /**
     * Multi-Agent模块logger - 用于多Agent协作相关日志
     */
    public static final LoggerProtocol MULTI_AGENT = 
        new LazyLogger(() -> LogManager.getLogger("multi_agent"));
    
    /**
     * Workflow模块logger - 用于工作流执行相关日志
     */
    public static final LoggerProtocol WORKFLOW = 
        new LazyLogger(() -> LogManager.getLogger("workflow"));
    
    /**
     * Session模块logger - 用于会话管理相关日志
     */
    public static final LoggerProtocol SESSION = 
        new LazyLogger(() -> LogManager.getLogger("session"));
    
    /**
     * Controller模块logger - 用于控制器相关日志
     */
    public static final LoggerProtocol CONTROLLER = 
        new LazyLogger(() -> LogManager.getLogger("controller"));
    
    /**
     * Runner模块logger - 用于执行器相关日志
     */
    public static final LoggerProtocol RUNNER = 
        new LazyLogger(() -> LogManager.getLogger("runner"));
    
    // ========== Foundation模块Logger ==========
    
    /**
     * LLM模块logger - 用于LLM调用相关日志
     */
    public static final LoggerProtocol LLM = 
        new LazyLogger(() -> LogManager.getLogger("llm"));
    
    /**
     * Tool模块logger - 用于工具调用相关日志
     */
    public static final LoggerProtocol TOOL = 
        new LazyLogger(() -> LogManager.getLogger("tool"));
    
    /**
     * Prompt模块logger - 用于prompt处理相关日志
     */
    public static final LoggerProtocol PROMPT = 
        new LazyLogger(() -> LogManager.getLogger("prompt"));
    
    // ========== 数据和检索模块Logger ==========
    
    /**
     * Memory模块logger - 用于记忆管理相关日志
     */
    public static final LoggerProtocol MEMORY = 
        new LazyLogger(() -> LogManager.getLogger("memory"));
    
    /**
     * Retrieval模块logger - 用于检索相关日志
     */
    public static final LoggerProtocol RETRIEVAL = 
        new LazyLogger(() -> LogManager.getLogger("retrieval"));
    
    /**
     * Context Engine模块logger - 用于上下文引擎相关日志
     */
    public static final LoggerProtocol CONTEXT_ENGINE = 
        new LazyLogger(() -> LogManager.getLogger("context_engine"));
    
    // ========== 执行和图模块Logger ==========
    
    /**
     * Graph模块logger - 用于图执行相关日志
     */
    public static final LoggerProtocol GRAPH = 
        new LazyLogger(() -> LogManager.getLogger("graph"));
    
    /**
     * Operator模块logger - 用于操作符执行相关日志
     */
    public static final LoggerProtocol OPERATOR = 
        new LazyLogger(() -> LogManager.getLogger("operator"));
    
    // ========== 协议和扩展模块Logger ==========
    
    /**
     * MCP协议logger - 用于MCP协议相关日志
     */
    public static final LoggerProtocol MCP = 
        new LazyLogger(() -> LogManager.getLogger("mcp"));
    
    // ========== 便捷方法 ==========
    
    /**
     * 获取指定名称的logger
     * 
     * <p>用于获取未预定义的logger。
     * 
     * @param name logger名称
     * @return logger实例
     */
    public static LoggerProtocol get(String name) {
        return LogManager.getLogger(name);
    }
}

