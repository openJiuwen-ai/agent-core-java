/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.defaults;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.BaseLogEvent;
import com.openjiuwen.core.common.logging.LogEventType;
import com.openjiuwen.core.common.logging.LogLevel;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 默认日志实现
 * 
 * <p>实现 LoggerProtocol 接口，提供完整的日志功能：
 * <ul>
 *   <li>支持控制台和文件输出</li>
 *   <li>支持日志轮转</li>
 *   <li>支持结构化日志</li>
 *   <li>自动控制字符清理</li>
 *   <li>自动上下文信息注入</li>
 *   <li>自动调用者信息检测</li>
 * </ul>
 * 
 * <p>对应 Python: default/default_impl.py::DefaultLogger
 */
public class DefaultLogger implements LoggerProtocol {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 控制字符映射表，用于清理日志消息中的控制字符
     * 
     * <p>对应 Python: DefaultLogger._CONTROL_CHAR_MAP
     */
    private static final Map<Character, String> CONTROL_CHAR_MAP = Map.of(
        '\r', "\\r",
        '\n', "\\n",
        '\t', "\\t",
        '\b', "\\b",
        '\u000B', "\\v",
        '\f', "\\f",
        '\0', "\\0"
    );
    
    private final String logType;
    private Map<String, Object> config;
    private final Logger logger;
    
    /**
     * 创建默认日志实例
     * 
     * @param logType 日志类型标识
     * @param config 日志配置
     */
    public DefaultLogger(String logType, Map<String, Object> config) {
        this.logType = logType;
        this.config = new HashMap<>(config);
        this.logger = LoggerFactory.getLogger(logType);
        setupLogger();
    }
    
    /**
     * 设置日志器
     * 
     * <p>根据配置设置日志级别、输出目标和格式化器。
     * 
     * <p>对应 Python: DefaultLogger._setup_logger()
     */
    @SuppressWarnings("unchecked")
    private void setupLogger() {
        // 解析日志级别
        Level level = mapToLogbackLevel(config.get("level"));
        
        // 获取输出目标和日志文件路径
        Object outputObj = config.getOrDefault("output", List.of("console"));
        List<String> output;
        if (outputObj instanceof List) {
            output = (List<String>) outputObj;
        } else {
            output = List.of("console");
        }
        
        String logFile = (String) config.getOrDefault("log_file", logType + ".log");
        
        // 验证日志文件路径
        try {
            LoggingUtils.normalizeAndValidateLogPath(logFile);
        } catch (Exception e) {
            // 路径验证失败时仅使用控制台输出
            output = List.of("console");
        }
        
        // 获取 Logback 上下文
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            // 不是 Logback 环境，仅设置 MDC
            MDC.put("log_type", logType);
            return;
        }
        
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logbackLogger = context.getLogger(logType);
        
        // 设置日志级别
        logbackLogger.setLevel(level);
        
        // 不继承根日志器的 appender，避免重复输出
        logbackLogger.setAdditive(false);
        
        // 清除已有 appender
        logbackLogger.detachAndStopAllAppenders();
        
        // 获取日志格式
        String logbackPattern = convertToLogbackPattern(
            (String) config.get("format"));
        
        // 添加控制台 appender
        if (output.contains("console")) {
            ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
            consoleAppender.setContext(context);
            consoleAppender.setName(logType + "-console");
            
            PatternLayoutEncoder consoleEncoder = new PatternLayoutEncoder();
            consoleEncoder.setContext(context);
            consoleEncoder.setPattern(logbackPattern);
            consoleEncoder.start();
            consoleAppender.setEncoder(consoleEncoder);
            
            ContextFilter consoleFilter = new ContextFilter(logType);
            consoleFilter.setContext(context);
            consoleFilter.start();
            consoleAppender.addFilter(consoleFilter);
            
            consoleAppender.start();
            logbackLogger.addAppender(consoleAppender);
        }
        
        // 添加文件 appender
        if (output.contains("file")) {
            try {
                String absLogFile;
                try {
                    absLogFile = Paths.get(logFile).toAbsolutePath().normalize().toString();
                } catch (Exception e) {
                    absLogFile = logFile;
                }
                
                // 确保日志目录存在
                Path logDir = Paths.get(absLogFile).getParent();
                if (logDir != null) {
                    try {
                        Files.createDirectories(logDir);
                    } catch (IOException e) {
                        throw new JiuWenBaseException(
                            StatusCode.COMMON_LOG_PATH_INIT_FAILED.getCode(),
                            StatusCode.COMMON_LOG_PATH_INIT_FAILED.getMessage()
                                .replace("{error_msg}", 
                                    "the log_dir is `" + logDir + "`, error detail: " + e.getMessage())
                        );
                    }
                }
                
                // 获取配置参数
                int backupCount = getIntConfig("backup_count", 20);
                int maxBytes = LoggingUtils.getLogMaxBytes(
                    config.getOrDefault("max_bytes", 20 * 1024 * 1024));
                String logFilePattern = (String) config.get("log_file_pattern");
                String backupFilePattern = (String) config.get("backup_file_pattern");
                
                // 创建 SafeRollingFileAppender
                SafeRollingFileAppender fileAppender = new SafeRollingFileAppender();
                fileAppender.setContext(context);
                fileAppender.setName(logType + "-file");
                fileAppender.setFile(absLogFile);
                
                if (logFilePattern != null) {
                    fileAppender.setLogFilePattern(logFilePattern);
                }
                if (backupFilePattern != null) {
                    fileAppender.setBackupFilePattern(backupFilePattern);
                }
                
                // 配置滚动策略
                FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
                rollingPolicy.setContext(context);
                rollingPolicy.setParent(fileAppender);
                rollingPolicy.setFileNamePattern(absLogFile + ".%i");
                rollingPolicy.setMinIndex(1);
                rollingPolicy.setMaxIndex(backupCount);
                rollingPolicy.start();
                
                // 配置触发策略
                SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
                triggeringPolicy.setContext(context);
                triggeringPolicy.setMaxFileSize(new FileSize(maxBytes));
                triggeringPolicy.start();
                
                fileAppender.setRollingPolicy(rollingPolicy);
                fileAppender.setTriggeringPolicy(triggeringPolicy);
                
                // 配置编码器
                PatternLayoutEncoder fileEncoder = new PatternLayoutEncoder();
                fileEncoder.setContext(context);
                fileEncoder.setPattern(logbackPattern);
                fileEncoder.setCharset(StandardCharsets.UTF_8);
                fileEncoder.start();
                fileAppender.setEncoder(fileEncoder);
                
                // 添加上下文过滤器
                ContextFilter fileFilter = new ContextFilter(logType);
                fileFilter.setContext(context);
                fileFilter.start();
                fileAppender.addFilter(fileFilter);
                
                fileAppender.start();
                logbackLogger.addAppender(fileAppender);
                
            } catch (JiuWenBaseException e) {
                throw e;
            } catch (Exception e) {
                // 文件 appender 创建失败时打印警告，不阻塞
                System.err.println("Warning: Failed to create file appender for " + logType + ": " + e.getMessage());
            }
        }
        
        // MDC 设置
        MDC.put("log_type", logType);
    }
    
    /**
     * 将 Python 日志格式转换为 Logback 模式
     */
    private String convertToLogbackPattern(String pythonFormat) {
        if (pythonFormat == null || pythonFormat.isEmpty()) {
            return LoggingConstants.DEFAULT_LOG_FORMAT;
        }
        return pythonFormat
            .replace("%(asctime)s.%(msecs)03d", "%d{yyyy-MM-dd HH:mm:ss.SSS}")
            .replace("%(asctime)s", "%d{yyyy-MM-dd HH:mm:ss}")
            .replace("%(log_type)s", "%X{log_type}")
            .replace("%(trace_id)s", "%X{trace_id}")
            .replace("%(levelname)s", "%-5level")
            .replace("%(message)s", "%msg%n")
            .replace("%(filename)s", "%file")
            .replace("%(lineno)d", "%line")
            .replace("%(funcName)s", "%method")
            .replace("%(name)s", "%logger");
    }
    
    /**
     * 将配置中的日志级别映射为 Logback Level
     */
    private Level mapToLogbackLevel(Object levelConfig) {
        if (levelConfig instanceof Integer) {
            int level = (Integer) levelConfig;
            return switch (level) {
                case 10 -> Level.DEBUG;
                case 20 -> Level.INFO;
                case 30 -> Level.WARN;
                case 40 -> Level.ERROR;
                case 50 -> Level.ERROR; // CRITICAL → ERROR (Logback 无 CRITICAL)
                default -> Level.WARN;
            };
        } else if (levelConfig instanceof String) {
            String levelStr = ((String) levelConfig).toUpperCase();
            return switch (levelStr) {
                case "DEBUG" -> Level.DEBUG;
                case "INFO" -> Level.INFO;
                case "WARNING", "WARN" -> Level.WARN;
                case "ERROR" -> Level.ERROR;
                case "CRITICAL", "FATAL" -> Level.ERROR;
                default -> Level.WARN;
            };
        }
        return Level.WARN;
    }
    
    /**
     * 从配置中获取整数值
     */
    private int getIntConfig(String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * 清理消息中的控制字符
     * 
     * <p>对应 Python: DefaultLogger._sanitize_message()
     */
    private String sanitizeMessage(Object msg) {
        if (msg == null) {
            return "";
        }
        
        String message = msg.toString();
        StringBuilder result = new StringBuilder(message.length());
        
        for (char c : message.toCharArray()) {
            int code = (int) c;
            if (code < 32 || code == 127) {
                String replacement = CONTROL_CHAR_MAP.get(c);
                if (replacement != null) {
                    result.append(replacement);
                } else {
                    result.append(String.format("\\x%02x", code));
                }
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * 处理日志消息，支持纯文本和结构化事件
     * 
     * <p>对应 Python: DefaultLogger._process_log_message()
     * 
     * <ul>
     *   <li>如果提供了 event，直接使用（结构化日志）</li>
     *   <li>如果提供了 eventType，使用 create_log_event 创建结构化事件</li>
     *   <li>如果都未提供，返回纯文本消息</li>
     * </ul>
     * 
     * @param logLevel 日志级别
     * @param msg 消息内容
     * @param eventType 事件类型（可选）
     * @param event 结构化事件（可选）
     * @param kwargs 额外参数
     * @return 处理后的消息
     */
    private String processLogMessage(LogLevel logLevel, String msg, LogEventType eventType, 
                                     BaseLogEvent event, Map<String, Object> kwargs) {
        if (event != null) {
            // 使用提供的结构化事件
            Map<String, Object> eventDict = event.toMap();
            eventDict.put("log_level", logLevel.getValue());
            if (msg != null && !msg.trim().isEmpty()) {
                eventDict.put("message", sanitizeMessage(msg));
            } else if (!eventDict.containsKey("message") || eventDict.get("message") == null) {
                eventDict.put("message", "");
            }
            return toJson(eventDict);
        }
        
        if (eventType == null) {
            // 无事件类型，返回纯文本消息
            return sanitizeMessage(msg);
        }
        
        // 创建结构化事件
        Map<String, Object> eventData = new HashMap<>(kwargs);
        
        // 添加 trace_id
        if (!eventData.containsKey("trace_id")) {
            String traceId = LoggingUtils.getSessionId();
            if (!LoggingConstants.DEFAULT_TRACE_ID.equals(traceId)) {
                eventData.put("trace_id", traceId);
            }
        }
        
        // 设置默认模块信息
        if (!eventData.containsKey("module_id")) {
            eventData.put("module_id", logType);
        }
        if (!eventData.containsKey("module_name")) {
            eventData.put("module_name", logType);
        }
        
        // 设置消息
        if (!eventData.containsKey("message")) {
            eventData.put("message", sanitizeMessage(msg));
        }
        eventData.put("log_level", logLevel.getValue());
        eventData.put("event_type", eventType.getValue());
        
        return toJson(eventData);
    }
    
    /**
     * 转换为 JSON 字符串
     */
    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return data.toString();
        }
    }
    
    /**
     * 更新 MDC 上下文
     */
    private void updateMdc() {
        MDC.put("log_type", "performance".equals(logType) ? "perf" : logType);
        MDC.put("trace_id", LoggingUtils.getSessionId());
    }
    
    // ==================== Plain text logging methods ====================
    
    @Override
    public void debug(String msg, Object... args) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.DEBUG, msg, null, null, Map.of());
        logger.debug(processedMsg, args);
    }
    
    @Override
    public void info(String msg, Object... args) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.INFO, msg, null, null, Map.of());
        logger.info(processedMsg, args);
    }
    
    @Override
    public void warning(String msg, Object... args) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.WARNING, msg, null, null, Map.of());
        logger.warn(processedMsg, args);
    }
    
    @Override
    public void error(String msg, Object... args) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.ERROR, msg, null, null, Map.of());
        logger.error(processedMsg, args);
    }
    
    @Override
    public void critical(String msg, Object... args) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.CRITICAL, msg, null, null, Map.of());
        logger.error("[CRITICAL] " + processedMsg, args);
    }
    
    @Override
    public void exception(String msg, Throwable cause) {
        updateMdc();
        Map<String, Object> kwargs = new HashMap<>();
        if (cause != null) {
            kwargs.put("stacktrace", getStackTrace(cause));
        }
        String processedMsg = processLogMessage(LogLevel.ERROR, msg, null, null, kwargs);
        logger.error(processedMsg, cause);
    }
    
    @Override
    public void log(int level, String msg, Object... args) {
        updateMdc();
        LogLevel logLevel = switch (level) {
            case 10 -> LogLevel.DEBUG;
            case 20 -> LogLevel.INFO;
            case 30 -> LogLevel.WARNING;
            case 40 -> LogLevel.ERROR;
            case 50 -> LogLevel.CRITICAL;
            default -> LogLevel.INFO;
        };
        
        String processedMsg = processLogMessage(logLevel, msg, null, null, Map.of());
        
        switch (level) {
            case 10 -> logger.debug(processedMsg, args);
            case 20 -> logger.info(processedMsg, args);
            case 30 -> logger.warn(processedMsg, args);
            case 40, 50 -> logger.error(processedMsg, args);
            default -> logger.info(processedMsg, args);
        }
    }
    
    // ==================== Structured logging methods ====================
    // Corresponds to Python: DefaultLogger.debug(msg, event_type=..., event=..., **kwargs)
    
    @Override
    public void debug(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.DEBUG, msg, eventType, null, kwargs);
        logger.debug(processedMsg);
    }
    
    @Override
    public void debug(String msg, BaseLogEvent event) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.DEBUG, msg, null, event, Map.of());
        logger.debug(processedMsg);
    }
    
    @Override
    public void info(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.INFO, msg, eventType, null, kwargs);
        logger.info(processedMsg);
    }
    
    @Override
    public void info(String msg, BaseLogEvent event) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.INFO, msg, null, event, Map.of());
        logger.info(processedMsg);
    }
    
    @Override
    public void warning(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.WARNING, msg, eventType, null, kwargs);
        logger.warn(processedMsg);
    }
    
    @Override
    public void warning(String msg, BaseLogEvent event) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.WARNING, msg, null, event, Map.of());
        logger.warn(processedMsg);
    }
    
    @Override
    public void error(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.ERROR, msg, eventType, null, kwargs);
        logger.error(processedMsg);
    }
    
    @Override
    public void error(String msg, BaseLogEvent event) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.ERROR, msg, null, event, Map.of());
        logger.error(processedMsg);
    }
    
    @Override
    public void critical(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.CRITICAL, msg, eventType, null, kwargs);
        logger.error("[CRITICAL] " + processedMsg);
    }
    
    @Override
    public void critical(String msg, BaseLogEvent event) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.CRITICAL, msg, null, event, Map.of());
        logger.error("[CRITICAL] " + processedMsg);
    }
    
    @Override
    public void exception(String msg, Throwable cause, LogEventType eventType, Map<String, Object> kwargs) {
        updateMdc();
        Map<String, Object> allKwargs = new HashMap<>(kwargs);
        if (cause != null && !allKwargs.containsKey("stacktrace")) {
            allKwargs.put("stacktrace", getStackTrace(cause));
        }
        String processedMsg = processLogMessage(LogLevel.ERROR, msg, eventType, null, allKwargs);
        logger.error(processedMsg, cause);
    }
    
    @Override
    public void exception(String msg, Throwable cause, BaseLogEvent event) {
        updateMdc();
        String processedMsg = processLogMessage(LogLevel.ERROR, msg, null, event, Map.of());
        logger.error(processedMsg, cause);
    }
    
    // ==================== Configuration methods ====================
    
    @Override
    public void setLevel(int level) {
        this.config.put("level", level);
        // 如果在 Logback 环境下，动态更新日志级别
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            ch.qos.logback.classic.Logger logbackLogger = context.getLogger(logType);
            logbackLogger.setLevel(mapToLogbackLevel(level));
        }
    }
    
    @Override
    public Map<String, Object> getConfig() {
        return new HashMap<>(config);
    }
    
    @Override
    public void reconfigure(Map<String, Object> config) {
        this.config = new HashMap<>(config);
        setupLogger();
    }
    
    @Override
    public void addHandler(Object handler) {
        if (handler instanceof Appender && LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            @SuppressWarnings("unchecked")
            Appender<ILoggingEvent> appender = (Appender<ILoggingEvent>) handler;
            ch.qos.logback.classic.Logger logbackLogger = context.getLogger(logType);
            logbackLogger.addAppender(appender);
        }
    }
    
    @Override
    public void removeHandler(Object handler) {
        if (handler instanceof Appender && LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            @SuppressWarnings("unchecked")
            Appender<ILoggingEvent> appender = (Appender<ILoggingEvent>) handler;
            ch.qos.logback.classic.Logger logbackLogger = context.getLogger(logType);
            logbackLogger.detachAppender(appender);
        }
    }
    
    @Override
    public void addFilter(Object filter) {
        if (filter instanceof ch.qos.logback.core.filter.Filter 
                && LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            ch.qos.logback.classic.Logger logbackLogger = context.getLogger(logType);
            Iterator<Appender<ILoggingEvent>> it = logbackLogger.iteratorForAppenders();
            while (it.hasNext()) {
                @SuppressWarnings("unchecked")
                ch.qos.logback.core.filter.Filter<ILoggingEvent> f = 
                    (ch.qos.logback.core.filter.Filter<ILoggingEvent>) filter;
                it.next().addFilter(f);
            }
        }
    }
    
    @Override
    public void removeFilter(Object filter) {
        // Logback does not support removing individual filters from appenders directly.
        // This is a limitation of the Logback API.
    }
    
    @Override
    public Object getLogger() {
        return logger;
    }
    
    /**
     * 获取日志类型
     */
    public String getLogType() {
        return logType;
    }
    
    /**
     * 获取异常堆栈跟踪
     */
    private String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
