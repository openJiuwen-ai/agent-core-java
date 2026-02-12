// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.logging;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoggingUtils
 * 
 * @since 0.1.4
 */
class LoggingUtilsTest {
    
    @AfterEach
    void tearDown() {
        LoggingUtils.clearSessionId();
    }
    
    @Test
    void testSetAndGetSessionId() {
        // Arrange
        String traceId = "test-trace-123";
        
        // Act
        LoggingUtils.setSessionId(traceId);
        String result = LoggingUtils.getSessionId();
        
        // Assert
        assertEquals(traceId, result);
    }
    
    @Test
    void testDefaultSessionId() {
        // Act
        String result = LoggingUtils.getSessionId();
        
        // Assert
        assertEquals("default_trace_id", result);
    }
    
    @Test
    void testSetNullSessionId() {
        // Act
        LoggingUtils.setSessionId(null);
        String result = LoggingUtils.getSessionId();
        
        // Assert
        assertEquals("default_trace_id", result);
    }
    
    @Test
    void testClearSessionId() {
        // Arrange
        LoggingUtils.setSessionId("test-123");
        
        // Act
        LoggingUtils.clearSessionId();
        String result = LoggingUtils.getSessionId();
        
        // Assert
        assertEquals("default_trace_id", result);
    }
    
    @Test
    void testGetLogMaxBytes() {
        // Test valid value
        assertEquals(1000, LoggingUtils.getLogMaxBytes(1000));
        assertEquals(1000, LoggingUtils.getLogMaxBytes("1000"));
        
        // Test negative value - returns default
        assertEquals(100 * 1024 * 1024, LoggingUtils.getLogMaxBytes(-1));
        
        // Test zero - returns default
        assertEquals(100 * 1024 * 1024, LoggingUtils.getLogMaxBytes(0));
        
        // Test exceeds max - returns default
        assertEquals(100 * 1024 * 1024, LoggingUtils.getLogMaxBytes(200 * 1024 * 1024));
    }
    
    @Test
    void testGetLogMaxBytesInvalidFormat() {
        // Act & Assert
        assertThrows(Exception.class, () -> {
            LoggingUtils.getLogMaxBytes("invalid");
        });
    }
    
    @Test
    void testNormalizeAndValidateLogPathNull() {
        // Act & Assert
        assertThrows(Exception.class, () -> {
            LoggingUtils.normalizeAndValidateLogPath(null);
        });
    }
    
    @Test
    void testNormalizeAndValidateLogPathEmpty() {
        // Act & Assert
        assertThrows(Exception.class, () -> {
            LoggingUtils.normalizeAndValidateLogPath("");
        });
        
        assertThrows(Exception.class, () -> {
            LoggingUtils.normalizeAndValidateLogPath("   ");
        });
    }
    
    @Test
    void testNormalizeAndValidateLogPathValid() {
        // Arrange
        String tempPath = System.getProperty("java.io.tmpdir");
        
        // Act
        String result = LoggingUtils.normalizeAndValidateLogPath(tempPath);
        
        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}

