/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
/**
 * 结构化日志事件定义包
 * 
 * <p>本包定义了系统中所有类型的结构化日志事件，用于记录各种活动的详细信息。
 * 
 * <h2>事件类型</h2>
 * <ul>
 *   <li>{@link com.openjiuwen.core.common.logging.events.AgentEvent} - Agent 活动和交互事件</li>
 *   <li>{@link com.openjiuwen.core.common.logging.events.WorkflowEvent} - Workflow 执行和状态事件</li>
 *   <li>{@link com.openjiuwen.core.common.logging.events.LLMEvent} - LLM 调用和响应事件</li>
 *   <li>{@link com.openjiuwen.core.common.logging.events.ToolEvent} - Tool 调用和执行事件</li>
 *   <li>{@link com.openjiuwen.core.common.logging.events.MemoryEvent} - Memory 操作事件</li>
 *   <li>{@link com.openjiuwen.core.common.logging.events.SessionEvent} - Session 管理事件</li>
 *   <li>{@link com.openjiuwen.core.common.logging.events.ContextEvent} - Context 操作事件</li>
 *   <li>{@link com.openjiuwen.core.common.logging.events.RetrievalEvent} - Retrieval 检索事件</li>
 *   <li>{@link com.openjiuwen.core.common.logging.events.PerformanceEvent} - Performance 指标事件</li>
 *   <li>{@link com.openjiuwen.core.common.logging.events.UserInteractionEvent} - User 交互事件</li>
 *   <li>{@link com.openjiuwen.core.common.logging.events.SystemEvent} - System 级别事件</li>
 * </ul>
 * 
 * <h2>工厂类</h2>
 * <p>{@link com.openjiuwen.core.common.logging.events.LogEventFactory} 提供创建、验证和脱敏事件的工具方法。
 * 
 * <p>对应 Python: openjiuwen/core/common/logging/events.py
 */
package com.openjiuwen.core.common.logging.events;

