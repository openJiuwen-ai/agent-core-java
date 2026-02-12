// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.logging;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LogManager
 * 
 * @since 0.1.4
 */
class LogManagerTest {
    
    @BeforeEach
    void setUp() {
        LogManager.clear();
    }
    
    @AfterEach
    void tearDown() {
        LogManager.clear();
    }
    
    @Test
    void testInitialize() {
        // Act
        LogManager.initialize();
        
        // Assert - should be able to call multiple times
        LogManager.initialize();
    }
    
    @Test
    void testRegisterLogger() {
        // Arrange
        LoggerProtocol logger = new SimpleTestLogger("test");
        
        // Act
        LogManager.registerLogger("test", logger);
        
        // Assert
        assertTrue(LogManager.hasLogger("test"));
        assertSame(logger, LogManager.getLogger("test"));
    }
    
    @Test
    void testGetLoggerCreatesDefault() {
        // Act
        LoggerProtocol logger = LogManager.getLogger("new_logger");
        
        // Assert
        assertNotNull(logger);
        assertTrue(LogManager.hasLogger("new_logger"));
    }
    
    @Test
    void testRemoveLogger() {
        // Arrange
        LoggerProtocol logger = new SimpleTestLogger("test");
        LogManager.registerLogger("test", logger);
        
        // Act
        LoggerProtocol removed = LogManager.removeLogger("test");
        
        // Assert
        assertSame(logger, removed);
        assertFalse(LogManager.hasLogger("test"));
    }
    
    @Test
    void testClear() {
        // Arrange
        LogManager.registerLogger("logger1", new SimpleTestLogger("logger1"));
        LogManager.registerLogger("logger2", new SimpleTestLogger("logger2"));
        
        // Act
        LogManager.clear();
        
        // Assert
        assertFalse(LogManager.hasLogger("logger1"));
        assertFalse(LogManager.hasLogger("logger2"));
    }
    
    @Test
    void testGetLoggerTypes() {
        // Arrange
        LogManager.registerLogger("logger1", new SimpleTestLogger("logger1"));
        LogManager.registerLogger("logger2", new SimpleTestLogger("logger2"));
        
        // Act
        var types = LogManager.getLoggerTypes();
        
        // Assert
        assertEquals(2, types.size());
        assertTrue(types.contains("logger1"));
        assertTrue(types.contains("logger2"));
    }
    
    @Test
    void testRegisterNullLoggerThrows() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            LogManager.registerLogger("test", null);
        });
    }
    
    /**
     * Simple test logger implementation
     */
    private static class SimpleTestLogger implements LoggerProtocol {
        private final String name;
        
        SimpleTestLogger(String name) {
            this.name = name;
        }
        
        @Override
        public void debug(String msg, Object... args) {}
        
        @Override
        public void info(String msg, Object... args) {}
        
        @Override
        public void warning(String msg, Object... args) {}
        
        @Override
        public void error(String msg, Object... args) {}
        
        @Override
        public void critical(String msg, Object... args) {}
        
        @Override
        public void exception(String msg, Throwable cause) {}
        
        @Override
        public void log(int level, String msg, Object... args) {}
        
        @Override
        public void setLevel(int level) {}
        
        @Override
        public java.util.Map<String, Object> getConfig() {
            return java.util.Map.of("name", name);
        }
        
        @Override
        public void reconfigure(java.util.Map<String, Object> config) {}
        
        @Override
        public void addHandler(Object handler) {}
        
        @Override
        public void removeHandler(Object handler) {}
        
        @Override
        public void addFilter(Object filter) {}
        
        @Override
        public void removeFilter(Object filter) {}
        
        @Override
        public Object getLogger() {
            return this;
        }
    }
}

