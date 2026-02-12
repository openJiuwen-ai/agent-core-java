// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.multiagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GroupConfig.
 */
class GroupConfigTest {
    
    @Test
    void testDefaultConstructor() {
        GroupConfig config = new GroupConfig();
        
        assertEquals(10, config.getMaxAgents());
        assertEquals(100, config.getMaxConcurrentMessages());
        assertEquals(30.0, config.getMessageTimeout(), 0.001);
    }
    
    @Test
    void testConstructorWithTwoParams() {
        GroupConfig config = new GroupConfig(5, 60.0);
        
        assertEquals(5, config.getMaxAgents());
        assertEquals(100, config.getMaxConcurrentMessages()); // default
        assertEquals(60.0, config.getMessageTimeout(), 0.001);
    }
    
    @Test
    void testConstructorWithAllParams() {
        GroupConfig config = new GroupConfig(20, 50, 120.0);
        
        assertEquals(20, config.getMaxAgents());
        assertEquals(50, config.getMaxConcurrentMessages());
        assertEquals(120.0, config.getMessageTimeout(), 0.001);
    }
    
    @Test
    void testConfigureMaxAgents() {
        GroupConfig config = new GroupConfig();
        
        GroupConfig result = config.configureMaxAgents(15);
        
        assertSame(config, result); // chaining
        assertEquals(15, config.getMaxAgents());
    }
    
    @Test
    void testConfigureTimeout() {
        GroupConfig config = new GroupConfig();
        
        GroupConfig result = config.configureTimeout(45.0);
        
        assertSame(config, result); // chaining
        assertEquals(45.0, config.getMessageTimeout(), 0.001);
    }
    
    @Test
    void testConfigureConcurrency() {
        GroupConfig config = new GroupConfig();
        
        GroupConfig result = config.configureConcurrency(200);
        
        assertSame(config, result); // chaining
        assertEquals(200, config.getMaxConcurrentMessages());
    }
    
    @Test
    void testChainingMultipleMethods() {
        GroupConfig config = new GroupConfig()
            .configureMaxAgents(25)
            .configureTimeout(90.0)
            .configureConcurrency(150);
        
        assertEquals(25, config.getMaxAgents());
        assertEquals(90.0, config.getMessageTimeout(), 0.001);
        assertEquals(150, config.getMaxConcurrentMessages());
    }
    
    @Test
    void testSettersAndGetters() {
        GroupConfig config = new GroupConfig();
        
        config.setMaxAgents(30);
        config.setMaxConcurrentMessages(500);
        config.setMessageTimeout(180.0);
        
        assertEquals(30, config.getMaxAgents());
        assertEquals(500, config.getMaxConcurrentMessages());
        assertEquals(180.0, config.getMessageTimeout(), 0.001);
    }
    
    @Test
    void testExtras() {
        GroupConfig config = new GroupConfig();
        
        config.setExtra("customKey", "customValue")
              .setExtra("numericKey", 42);
        
        assertEquals("customValue", config.getExtra("customKey"));
        assertEquals(42, config.getExtra("numericKey"));
        assertNull(config.getExtra("nonexistent"));
    }
    
    @Test
    void testToString() {
        GroupConfig config = new GroupConfig(5, 50, 60.0);
        
        String str = config.toString();
        
        assertTrue(str.contains("5"));
        assertTrue(str.contains("50"));
        assertTrue(str.contains("60.0"));
    }
}

