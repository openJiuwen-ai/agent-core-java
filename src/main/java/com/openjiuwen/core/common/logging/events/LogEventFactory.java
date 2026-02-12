/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.events;

import com.openjiuwen.core.common.logging.BaseLogEvent;
import com.openjiuwen.core.common.logging.EventStatus;
import com.openjiuwen.core.common.logging.LogEventType;
import com.openjiuwen.core.common.logging.LogLevel;
import com.openjiuwen.core.common.logging.ModuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 日志事件工厂类
 * 
 * <p>提供创建、验证和处理日志事件的工具方法。
 * 
 * <p>对应 Python: events.py 中的 create_log_event, validate_event, sanitize_event_for_logging 函数
 */
public final class LogEventFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(LogEventFactory.class);
    
    /**
     * 事件类型到事件类的映射
     */
    private static final Map<LogEventType, Class<? extends BaseLogEvent>> EVENT_CLASS_MAP;
    
    /**
     * 默认敏感字段列表（需要脱敏）
     */
    private static final List<String> DEFAULT_SENSITIVE_FIELDS = List.of(
        "messages",
        "response_content",
        "input_content",
        "query",
        "arguments",
        "result",
        "message_content",
        "tool_calls",
        "input_data",
        "output_data",
        "retrieved_memories"
    );
    
    static {
        Map<LogEventType, Class<? extends BaseLogEvent>> map = new HashMap<>();
        
        // Agent 事件
        map.put(LogEventType.AGENT_START, AgentEvent.class);
        map.put(LogEventType.AGENT_END, AgentEvent.class);
        map.put(LogEventType.AGENT_INVOKE, AgentEvent.class);
        map.put(LogEventType.AGENT_RESPONSE, AgentEvent.class);
        map.put(LogEventType.AGENT_ERROR, AgentEvent.class);
        
        // Workflow 事件
        map.put(LogEventType.WORKFLOW_START, WorkflowEvent.class);
        map.put(LogEventType.WORKFLOW_END, WorkflowEvent.class);
        map.put(LogEventType.WORKFLOW_COMPONENT_START, WorkflowEvent.class);
        map.put(LogEventType.WORKFLOW_COMPONENT_END, WorkflowEvent.class);
        map.put(LogEventType.WORKFLOW_COMPONENT_ERROR, WorkflowEvent.class);
        map.put(LogEventType.WORKFLOW_BRANCH, WorkflowEvent.class);
        
        // LLM 事件
        map.put(LogEventType.LLM_CALL_START, LLMEvent.class);
        map.put(LogEventType.LLM_CALL_END, LLMEvent.class);
        map.put(LogEventType.LLM_CALL_ERROR, LLMEvent.class);
        map.put(LogEventType.LLM_STREAM_CHUNK, LLMEvent.class);
        
        // Tool 事件
        map.put(LogEventType.TOOL_CALL_START, ToolEvent.class);
        map.put(LogEventType.TOOL_CALL_END, ToolEvent.class);
        map.put(LogEventType.TOOL_CALL_ERROR, ToolEvent.class);
        
        // Memory 事件
        map.put(LogEventType.MEMORY_STORE, MemoryEvent.class);
        map.put(LogEventType.MEMORY_RETRIEVE, MemoryEvent.class);
        map.put(LogEventType.MEMORY_DELETE, MemoryEvent.class);
        map.put(LogEventType.MEMORY_UPDATE, MemoryEvent.class);
        
        // Session 事件
        map.put(LogEventType.SESSION_CREATE, SessionEvent.class);
        map.put(LogEventType.SESSION_UPDATE, SessionEvent.class);
        map.put(LogEventType.SESSION_DELETE, SessionEvent.class);
        
        // Context 事件
        map.put(LogEventType.CONTEXT_ADD_MESSAGE, ContextEvent.class);
        map.put(LogEventType.CONTEXT_CLEAR, ContextEvent.class);
        map.put(LogEventType.CONTEXT_RETRIEVE, ContextEvent.class);
        
        // Retrieval 事件
        map.put(LogEventType.RETRIEVAL_START, RetrievalEvent.class);
        map.put(LogEventType.RETRIEVAL_END, RetrievalEvent.class);
        map.put(LogEventType.RETRIEVAL_ERROR, RetrievalEvent.class);
        
        // Performance 事件
        map.put(LogEventType.PERFORMANCE_METRIC, PerformanceEvent.class);
        
        // User 事件
        map.put(LogEventType.USER_INPUT, UserInteractionEvent.class);
        map.put(LogEventType.USER_FEEDBACK, UserInteractionEvent.class);
        
        // System 事件
        map.put(LogEventType.SYSTEM_START, SystemEvent.class);
        map.put(LogEventType.SYSTEM_SHUTDOWN, SystemEvent.class);
        map.put(LogEventType.SYSTEM_ERROR, SystemEvent.class);
        
        EVENT_CLASS_MAP = Collections.unmodifiableMap(map);
    }
    
    private LogEventFactory() {
        // 工具类，禁止实例化
    }
    
    /**
     * 获取事件类型到事件类的映射
     * 
     * @return 不可变的映射表
     */
    public static Map<LogEventType, Class<? extends BaseLogEvent>> getEventClassMap() {
        return EVENT_CLASS_MAP;
    }
    
    /**
     * 根据事件类型创建对应的事件对象（无额外参数）
     * 
     * <p>使用各事件类的 Builder 创建事件实例。
     * 
     * @param eventType 事件类型
     * @return 创建的事件对象
     */
    public static BaseLogEvent createLogEvent(LogEventType eventType) {
        return createLogEvent(eventType, null);
    }
    
    /**
     * 根据事件类型创建对应的事件对象，支持传入字段参数
     * 
     * <p>对应 Python: create_log_event(event_type, **kwargs)
     * 接受任意关键字参数，自动过滤有效字段，对未定义字段发出警告。
     * 
     * <p>kwargs 中的 key 支持 snake_case 和 camelCase 两种命名风格。
     * 
     * @param eventType 事件类型
     * @param kwargs 事件字段参数（key 为字段名，value 为字段值）
     * @return 创建的事件对象
     */
    public static BaseLogEvent createLogEvent(LogEventType eventType, Map<String, Object> kwargs) {
        Object builder = createLogEventWithBuilder(eventType);
        
        if (kwargs != null && !kwargs.isEmpty()) {
            Class<?> builderClass = builder.getClass();
            
            // 收集 Builder 上所有有效的 setter 方法（参数为1个且返回值为 Builder 自身类型）
            Set<String> validMethodNames = new HashSet<>();
            for (Method method : builderClass.getMethods()) {
                if (method.getParameterCount() == 1 && method.getReturnType() == builderClass) {
                    validMethodNames.add(method.getName());
                }
            }
            
            List<String> ignoredFields = new ArrayList<>();
            
            for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
                String key = entry.getKey();
                // 跳过 event_type / eventType，已经在 createLogEventWithBuilder 中设置
                if ("event_type".equals(key) || "eventType".equals(key)) {
                    continue;
                }
                
                String camelKey = snakeToCamel(key);
                
                if (validMethodNames.contains(camelKey)) {
                    try {
                        for (Method method : builderClass.getMethods()) {
                            if (method.getName().equals(camelKey) && method.getParameterCount() == 1) {
                                Object value = convertValue(entry.getValue(), method.getParameterTypes()[0]);
                                if (value != null || !method.getParameterTypes()[0].isPrimitive()) {
                                    method.invoke(builder, value);
                                }
                                break;
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to set field '{}' on builder: {}", key, e.getMessage());
                    }
                } else {
                    ignoredFields.add(key);
                }
            }
            
            if (!ignoredFields.isEmpty()) {
                Class<? extends BaseLogEvent> eventClass = EVENT_CLASS_MAP.getOrDefault(eventType, SystemEvent.class);
                logger.warn("Ignoring undefined fields for {}: {}",
                    eventClass.getSimpleName(), String.join(", ", ignoredFields));
            }
        }
        
        // 使用反射调用 build() 方法
        try {
            return (BaseLogEvent) builder.getClass().getMethod("build").invoke(builder);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build log event for type: " + eventType, e);
        }
    }
    
    /**
     * 获取事件类型对应的 Builder
     * 
     * @param eventType 事件类型
     * @return 对应类型的 Builder
     */
    public static Object createLogEventWithBuilder(LogEventType eventType) {
        Class<? extends BaseLogEvent> eventClass = EVENT_CLASS_MAP.get(eventType);
        
        if (eventClass == null) {
            // 默认使用 SystemEvent
            return SystemEvent.builder().eventType(eventType);
        }
        
        // 根据类型返回对应的 Builder
        return switch (eventClass.getSimpleName()) {
            case "AgentEvent" -> AgentEvent.builder().eventType(eventType);
            case "WorkflowEvent" -> WorkflowEvent.builder().eventType(eventType);
            case "LLMEvent" -> LLMEvent.builder().eventType(eventType);
            case "ToolEvent" -> ToolEvent.builder().eventType(eventType);
            case "MemoryEvent" -> MemoryEvent.builder().eventType(eventType);
            case "SessionEvent" -> SessionEvent.builder().eventType(eventType);
            case "ContextEvent" -> ContextEvent.builder().eventType(eventType);
            case "RetrievalEvent" -> RetrievalEvent.builder().eventType(eventType);
            case "PerformanceEvent" -> PerformanceEvent.builder().eventType(eventType);
            case "UserInteractionEvent" -> UserInteractionEvent.builder().eventType(eventType);
            case "SystemEvent" -> SystemEvent.builder().eventType(eventType);
            default -> SystemEvent.builder().eventType(eventType);
        };
    }
    
    /**
     * 将 snake_case 字符串转换为 camelCase
     * 
     * @param snakeCase snake_case 字符串
     * @return camelCase 字符串
     */
    private static String snakeToCamel(String snakeCase) {
        if (snakeCase == null || !snakeCase.contains("_")) {
            return snakeCase;
        }
        
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        
        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                result.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * 将值转换为目标类型
     * 
     * @param value 源值
     * @param targetType 目标类型
     * @return 转换后的值
     */
    @SuppressWarnings("unchecked")
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        
        // 类型已匹配
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        
        // 字符串 → 枚举转换
        if (value instanceof String strValue) {
            if (targetType == LogEventType.class) {
                return LogEventType.fromValue(strValue);
            } else if (targetType == LogLevel.class) {
                return LogLevel.fromValue(strValue);
            } else if (targetType == ModuleType.class) {
                return ModuleType.fromValue(strValue);
            } else if (targetType == EventStatus.class) {
                return EventStatus.fromValue(strValue);
            } else if (targetType == Instant.class) {
                return Instant.parse(strValue);
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(strValue);
            } else if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(strValue);
            } else if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(strValue);
            }
        }
        
        // 数值转换
        if (value instanceof Number numValue) {
            if (targetType == Integer.class || targetType == int.class) {
                return numValue.intValue();
            } else if (targetType == Double.class || targetType == double.class) {
                return numValue.doubleValue();
            } else if (targetType == Long.class || targetType == long.class) {
                return numValue.longValue();
            }
        }
        
        // 尝试 toString
        if (targetType == String.class) {
            return value.toString();
        }
        
        return value;
    }
    
    /**
     * 验证事件对象的有效性
     * 
     * @param event 要验证的事件对象
     * @return 如果事件有效则返回 true
     */
    public static boolean validateEvent(BaseLogEvent event) {
        if (event == null) {
            return false;
        }
        
        // 检查必需字段
        if (event.getEventId() == null || event.getEventId().isEmpty()) {
            return false;
        }
        
        // 检查枚举类型是否有效
        if (event.getEventType() == null) {
            return false;
        }
        
        if (event.getLogLevel() == null) {
            return false;
        }
        
        if (event.getModuleType() == null) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 为日志输出脱敏事件数据
     * 
     * @param event 要处理的事件对象
     * @return 脱敏后的事件字典
     */
    public static Map<String, Object> sanitizeEventForLogging(BaseLogEvent event) {
        return sanitizeEventForLogging(event, null);
    }
    
    /**
     * 为日志输出脱敏事件数据
     * 
     * @param event 要处理的事件对象
     * @param sensitiveFields 需要脱敏的字段列表，如果为 null 则使用默认列表
     * @return 脱敏后的事件字典
     */
    public static Map<String, Object> sanitizeEventForLogging(BaseLogEvent event, List<String> sensitiveFields) {
        if (event == null) {
            return new HashMap<>();
        }
        
        List<String> fieldsToSanitize = sensitiveFields != null ? sensitiveFields : DEFAULT_SENSITIVE_FIELDS;
        
        Map<String, Object> eventDict = new HashMap<>(event.toMap());
        
        // 脱敏敏感字段
        for (String fieldName : fieldsToSanitize) {
            if (eventDict.containsKey(fieldName) && eventDict.get(fieldName) != null) {
                eventDict.put(fieldName, "<REDACTED>");
            }
        }
        
        return eventDict;
    }
    
    /**
     * 获取默认的敏感字段列表
     * 
     * @return 不可变的敏感字段列表
     */
    public static List<String> getDefaultSensitiveFields() {
        return DEFAULT_SENSITIVE_FIELDS;
    }
    
    /**
     * 根据事件类型获取对应的事件类
     * 
     * @param eventType 事件类型
     * @return 对应的事件类，如果未找到则返回 null
     */
    public static Class<? extends BaseLogEvent> getEventClass(LogEventType eventType) {
        return EVENT_CLASS_MAP.get(eventType);
    }
    
    /**
     * 获取所有支持的事件类型
     * 
     * @return 事件类型集合
     */
    public static Set<LogEventType> getSupportedEventTypes() {
        return EVENT_CLASS_MAP.keySet();
    }
}

