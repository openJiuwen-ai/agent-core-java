// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Log Manager
 * 
 * <p>Provides logger creation, registration, and retrieval functionality.
 * Thread-safe implementation using ConcurrentHashMap.
 * 
 * @since 0.1.4
 */
public class LogManager {
    
    private static final Map<String, LoggerProtocol> loggers = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;
    private static Class<? extends LoggerProtocol> defaultLoggerClass;
    
    private LogManager() {
        // Utility class
    }
    
    /**
     * Set default logger class
     * 
     * @param loggerClass the logger class
     */
    public static synchronized void setDefaultLoggerClass(Class<? extends LoggerProtocol> loggerClass) {
        defaultLoggerClass = loggerClass;
    }
    
    /**
     * Initialize log manager
     * 
     * <p>Idempotent operation - can be called multiple times safely.
     * Loads log configuration and creates loggers for all configured types.
     * 
     * <p>Corresponds to Python: LogManager.initialize()
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        
        Class<? extends LoggerProtocol> loggerClass = getDefaultLoggerClassInternal();
        Object logConfig = getLogConfig();
        
        if (logConfig != null) {
            try {
                // Call logConfig.getAllConfigs() to get all config entries
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> allConfigs = 
                    (Map<String, Map<String, Object>>) logConfig.getClass()
                        .getMethod("getAllConfigs").invoke(logConfig);
                
                for (Map.Entry<String, Map<String, Object>> entry : allConfigs.entrySet()) {
                    String logType = entry.getKey();
                    Map<String, Object> config = entry.getValue();
                    if (!loggers.containsKey(logType)) {
                        loggers.put(logType, createLoggerInstance(loggerClass, logType, config));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load log configuration", e);
            }
        } else {
            throw new RuntimeException(
                "LogConfig not available. Please ensure it is properly configured.");
        }
        
        initialized = true;
    }
    
    /**
     * Register custom logger
     * 
     * @param logType the log type identifier
     * @param logger the logger instance
     * @throws IllegalArgumentException if logger is invalid
     */
    public static void registerLogger(String logType, LoggerProtocol logger) {
        if (logger == null) {
            throw new IllegalArgumentException("Logger cannot be null");
        }
        
        loggers.put(logType, logger);
    }
    
    /**
     * Get logger by type
     * 
     * <p>Creates default logger if not exists, using LogConfig if available.
     * 
     * <p>Corresponds to Python: LogManager.get_logger(log_type)
     * 
     * @param logType the log type identifier
     * @return the logger instance
     */
    public static LoggerProtocol getLogger(String logType) {
        if (!initialized) {
            initialize();
        }
        
        return loggers.computeIfAbsent(logType, key -> {
            Class<? extends LoggerProtocol> loggerClass = getDefaultLoggerClassInternal();
            Object logConfig = getLogConfig();
            
            if (logConfig != null) {
                try {
                    // Call logConfig.getCustomConfig(logType, null)
                    @SuppressWarnings("unchecked")
                    Map<String, Object> config = (Map<String, Object>) logConfig.getClass()
                        .getMethod("getCustomConfig", String.class, Map.class)
                        .invoke(logConfig, key, null);
                    return createLoggerInstance(loggerClass, key, config);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create logger for '" + key + "'", e);
                }
            } else {
                throw new RuntimeException(
                    "LogConfig not available. Cannot create logger for '" + key + "'.");
            }
        });
    }
    
    /**
     * Remove logger
     * 
     * @param logType the log type
     * @return the removed logger, or null if not found
     */
    public static LoggerProtocol removeLogger(String logType) {
        return loggers.remove(logType);
    }
    
    /**
     * Get all registered loggers
     * 
     * <p>Corresponds to Python: LogManager.get_all_loggers()
     * 
     * @return an unmodifiable copy of the loggers map
     */
    public static Map<String, LoggerProtocol> getAllLoggers() {
        return java.util.Collections.unmodifiableMap(new java.util.HashMap<>(loggers));
    }
    
    /**
     * Clear all loggers and reset state
     * 
     * <p>Corresponds to Python: LogManager.reset()
     */
    public static synchronized void clear() {
        loggers.clear();
        initialized = false;
        defaultLoggerClass = null;
    }
    
    /**
     * Check if logger exists
     * 
     * @param logType the log type
     * @return true if logger exists
     */
    public static boolean hasLogger(String logType) {
        return loggers.containsKey(logType);
    }
    
    /**
     * Get all registered logger types
     * 
     * @return the logger types
     */
    public static java.util.Set<String> getLoggerTypes() {
        return java.util.Collections.unmodifiableSet(loggers.keySet());
    }
    
    /**
     * Get the default logger class, trying to load DefaultLogger if not set
     * 
     * <p>Corresponds to Python: LogManager._get_default_logger_class()
     */
    private static Class<? extends LoggerProtocol> getDefaultLoggerClassInternal() {
        if (defaultLoggerClass == null) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends LoggerProtocol> cls = (Class<? extends LoggerProtocol>)
                    Class.forName("com.openjiuwen.core.common.logging.defaults.DefaultLogger");
                defaultLoggerClass = cls;
            } catch (ClassNotFoundException e) {
                // DefaultLogger not available
            }
        }
        return defaultLoggerClass;
    }
    
    /**
     * Get the LogConfig singleton instance
     * 
     * <p>Corresponds to Python: LogManager._get_log_config()
     */
    private static Object getLogConfig() {
        try {
            Class<?> logConfigClass = Class.forName(
                "com.openjiuwen.core.common.logging.defaults.LogConfig");
            return logConfigClass.getMethod("getInstance").invoke(null);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Create a logger instance using the given logger class and configuration
     */
    private static LoggerProtocol createLoggerInstance(
            Class<? extends LoggerProtocol> loggerClass, String logType, Map<String, Object> config) {
        if (loggerClass != null) {
            try {
                return loggerClass.getConstructor(String.class, Map.class)
                    .newInstance(logType, config);
            } catch (Exception e) {
                // Fallback to simple logger
            }
        }
        return new SimpleLogger(logType);
    }
    
    /**
     * Simple logger implementation (placeholder)
     */
    private static class SimpleLogger implements LoggerProtocol {
        private final String name;
        
        SimpleLogger(String name) {
            this.name = name;
        }
        
        @Override
        public void debug(String msg, Object... args) {
            log("DEBUG", msg, args);
        }
        
        @Override
        public void info(String msg, Object... args) {
            log("INFO", msg, args);
        }
        
        @Override
        public void warning(String msg, Object... args) {
            log("WARNING", msg, args);
        }
        
        @Override
        public void error(String msg, Object... args) {
            log("ERROR", msg, args);
        }
        
        @Override
        public void critical(String msg, Object... args) {
            log("CRITICAL", msg, args);
        }
        
        @Override
        public void exception(String msg, Throwable cause) {
            System.err.printf("[%s] [%s] %s%n", name, "ERROR", msg);
            if (cause != null) {
                cause.printStackTrace();
            }
        }
        
        @Override
        public void log(int level, String msg, Object... args) {
            log(String.valueOf(level), msg, args);
        }
        
        private void log(String level, String msg, Object... args) {
            String formatted = String.format(msg, args);
            System.out.printf("[%s] [%s] %s%n", name, level, formatted);
        }
        
        @Override
        public void setLevel(int level) {
            // No-op in simple implementation
        }
        
        @Override
        public Map<String, Object> getConfig() {
            return Map.of("name", name);
        }
        
        @Override
        public void reconfigure(Map<String, Object> config) {
            // No-op in simple implementation
        }
        
        @Override
        public void addHandler(Object handler) {
            // No-op in simple implementation
        }
        
        @Override
        public void removeHandler(Object handler) {
            // No-op in simple implementation
        }
        
        @Override
        public void addFilter(Object filter) {
            // No-op in simple implementation
        }
        
        @Override
        public void removeFilter(Object filter) {
            // No-op in simple implementation
        }
        
        @Override
        public Object getLogger() {
            return this;
        }
    }
}

