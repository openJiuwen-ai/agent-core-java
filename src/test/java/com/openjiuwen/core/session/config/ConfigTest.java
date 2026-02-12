/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.config;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.session.SessionConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Config class and related functions.
 */
class ConfigTest {
    
    /**
     * Concrete implementation of Config for testing.
     */
    static class ConcreteConfig extends Config {
    }
    
    @Nested
    @DisplayName("trySetEnv Tests")
    class TrySetEnvTests {
        
        @Test
        @DisplayName("float type conversion from string")
        void testFloatTypeConversionFromString() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, "10.5");
            assertEquals(10.5, envConfigs.get("test_key"));
        }
        
        @Test
        @DisplayName("float type conversion from int")
        void testFloatTypeConversionFromInt() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, 10);
            assertEquals(10.0, envConfigs.get("test_key"));
        }
        
        @Test
        @DisplayName("float type conversion with invalid value keeps config unchanged")
        void testFloatTypeConversionInvalidValue() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, "invalid");
            assertFalse(envConfigs.containsKey("test_key"));
        }
        
        @Test
        @DisplayName("int type conversion from string")
        void testIntTypeConversionFromString() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", SessionConstants.LOOP_NUMBER_MAX_LIMIT_ENV_KEY, "100");
            assertEquals(100, envConfigs.get("test_key"));
        }
        
        @Test
        @DisplayName("int type conversion from int")
        void testIntTypeConversionFromInt() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", SessionConstants.LOOP_NUMBER_MAX_LIMIT_ENV_KEY, 100);
            assertEquals(100, envConfigs.get("test_key"));
        }
        
        @Test
        @DisplayName("int type conversion with invalid value keeps config unchanged")
        void testIntTypeConversionInvalidValue() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", SessionConstants.LOOP_NUMBER_MAX_LIMIT_ENV_KEY, "invalid");
            assertFalse(envConfigs.containsKey("test_key"));
        }
        
        @Test
        @DisplayName("bool type conversion from bool")
        void testBoolTypeConversionFromBool() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, true);
            assertEquals(true, envConfigs.get("test_key"));
        }
        
        @Test
        @DisplayName("bool type conversion from string 'true'")
        void testBoolTypeConversionFromStringTrue() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, "true");
            assertEquals(true, envConfigs.get("test_key"));
        }
        
        @Test
        @DisplayName("bool type conversion from string 'false'")
        void testBoolTypeConversionFromStringFalse() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, "false");
            assertEquals(false, envConfigs.get("test_key"));
        }
        
        @Test
        @DisplayName("bool type conversion is case-insensitive")
        void testBoolTypeConversionCaseInsensitive() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, "TRUE");
            assertEquals(true, envConfigs.get("test_key"));
        }
        
        @Test
        @DisplayName("bool type conversion with invalid value keeps config unchanged")
        void testBoolTypeConversionInvalidValue() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", SessionConstants.FORCE_DEL_WORKFLOW_STATE_ENV_KEY, "invalid");
            assertFalse(envConfigs.containsKey("test_key"));
        }
        
        @Test
        @DisplayName("null value does nothing")
        void testNullValueDoesNothing() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, null);
            assertFalse(envConfigs.containsKey("test_key"));
        }
        
        @Test
        @DisplayName("unknown type passes through")
        void testUnknownTypePassesThrough() {
            Map<String, Object> envConfigs = new HashMap<>();
            Config.trySetEnv(envConfigs, "test_key", "UNKNOWN_ENV_KEY", "some_value");
            assertEquals("some_value", envConfigs.get("test_key"));
        }
    }
    
    @Nested
    @DisplayName("Config Tests")
    class ConfigTests {
        
        private ConcreteConfig config;
        
        @BeforeEach
        void setUp() {
            config = new ConcreteConfig();
        }
        
        @Test
        @DisplayName("construction loads builtin configs")
        void testConstructionLoadsBuiltinConfigs() {
            assertEquals(60, config.getEnv(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT));
            assertEquals(-1, config.getEnv(SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT));
            assertEquals(-1, config.getEnv(SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT));
            assertEquals(-1, config.getEnv(SessionConstants.COMP_STREAM_CALL_TIMEOUT_KEY));
            assertEquals(SessionConstants.LOOP_NUMBER_MAX_LIMIT_DEFAULT, 
                config.getEnv(SessionConstants.LOOP_NUMBER_MAX_LIMIT_KEY));
            assertEquals(false, config.getEnv(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY));
        }
        
        @Test
        @DisplayName("setEnvs updates config")
        void testSetEnvsUpdatesConfig() {
            config.setEnvs(Map.of("custom_key", "custom_value"));
            assertEquals("custom_value", config.getEnv("custom_key"));
        }
        
        @Test
        @DisplayName("setEnvs with null does nothing")
        void testSetEnvsWithNullDoesNothing() {
            Object original = config.getEnv(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT);
            config.setEnvs(null);
            assertEquals(original, config.getEnv(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT));
        }
        
        @Test
        @DisplayName("setEnvs overwrites existing")
        void testSetEnvsOverwritesExisting() {
            config.setEnvs(Map.of(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT, 120));
            assertEquals(120, config.getEnv(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT));
        }
        
        @Test
        @DisplayName("getEnv with default")
        void testGetEnvWithDefault() {
            Object result = config.getEnv("nonexistent_key", "default_value");
            assertEquals("default_value", result);
        }
        
        @Test
        @DisplayName("getEnv without default returns null")
        void testGetEnvWithoutDefaultReturnsNull() {
            Object result = config.getEnv("nonexistent_key");
            assertNull(result);
        }
        
        @Test
        @DisplayName("getWorkflowConfig with null raises")
        void testGetWorkflowConfigWithNullRaises() {
            JiuWenBaseException exception = assertThrows(JiuWenBaseException.class,
                () -> config.getWorkflowConfig(null));
            assertTrue(exception.getMessage().contains("workflow_id is invalid"));
        }
        
        @Test
        @DisplayName("getWorkflowConfig returns null for nonexistent")
        void testGetWorkflowConfigReturnsNullForNonexistent() {
            Object result = config.getWorkflowConfig("nonexistent");
            assertNull(result);
        }
        
        @Test
        @DisplayName("addWorkflowConfig with null workflowId raises")
        void testAddWorkflowConfigWithNullWorkflowIdRaises() {
            JiuWenBaseException exception = assertThrows(JiuWenBaseException.class,
                () -> config.addWorkflowConfig(null, Map.of("some", "config")));
            assertTrue(exception.getMessage().contains("workflow_id is invalid"));
        }
        
        @Test
        @DisplayName("addWorkflowConfig with null config raises")
        void testAddWorkflowConfigWithNullConfigRaises() {
            JiuWenBaseException exception = assertThrows(JiuWenBaseException.class,
                () -> config.addWorkflowConfig("workflow1", null));
            assertTrue(exception.getMessage().contains("workflow config is invalid"));
        }
        
        @Test
        @DisplayName("add and get workflow config work together")
        void testAddAndGetWorkflowConfig() {
            Map<String, Object> workflowConfig = Map.of("name", "test_workflow", "version", "1.0");
            config.addWorkflowConfig("workflow1", workflowConfig);
            Object result = config.getWorkflowConfig("workflow1");
            assertEquals(workflowConfig, result);
        }
        
        @Test
        @DisplayName("getAgentConfig returns null initially")
        void testGetAgentConfigReturnsNullInitially() {
            assertNull(config.getAgentConfig());
        }
        
        @Test
        @DisplayName("set and get agent config work together")
        void testSetAndGetAgentConfig() {
            Map<String, Object> agentConfig = Map.of("agent_name", "test_agent");
            config.setAgentConfig(agentConfig);
            assertEquals(agentConfig, config.getAgentConfig());
        }
    }
    
    @Nested
    @DisplayName("WorkflowSessionVars Tests")
    class WorkflowSessionVarsTests {
        
        @Test
        @DisplayName("thread-local session vars work")
        void testThreadLocalSessionVars() {
            Config.clearWorkflowSessionVars();
            
            Config.setWorkflowSessionVar("key1", "value1");
            assertEquals("value1", Config.getWorkflowSessionVars().get("key1"));
            
            Config.clearWorkflowSessionVars();
            assertNull(Config.getWorkflowSessionVars().get("key1"));
        }
    }
    
    /**
     * Python: TestLoadEnvConfigs
     * 测试_load_env_configs函数
     * Note: Java中使用ThreadLocal session vars来模拟配置覆盖
     */
    @Nested
    @DisplayName("LoadEnvConfigs Tests")
    class LoadEnvConfigsTests {
        
        @Test
        @DisplayName("loads from workflow session vars")
        void testLoadsFromWorkflowSessionVars() {
            // Python: token = workflow_session_vars.set({WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY: "180"})
            //         try:
            //             configs = _load_env_configs()
            //             assert configs.get(WORKFLOW_EXECUTE_TIMEOUT) == 180.0
            //         finally:
            //             workflow_session_vars.reset(token)
            try {
                Config.clearWorkflowSessionVars();
                Config.setWorkflowSessionVar(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, "180");
                
                // Create a new config to trigger loadEnvConfigs
                ConcreteConfig config = new ConcreteConfig();
                assertEquals(180.0, config.getEnv(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT));
            } finally {
                Config.clearWorkflowSessionVars();
            }
        }
        
        @Test
        @DisplayName("workflow session vars overrides os environ")
        void testWorkflowSessionVarsOverridesOsEnviron() {
            // Python: with patch.dict(os.environ, {WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY: "120"}):
            //             token = workflow_session_vars.set({WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY: "180"})
            //             try:
            //                 configs = _load_env_configs()
            //                 assert configs.get(WORKFLOW_EXECUTE_TIMEOUT) == 180.0
            //             finally:
            //                 workflow_session_vars.reset(token)
            // 
            // Note: In Java, we can't easily mock System.getenv(), but we can verify that
            // session vars take precedence by setting them and checking the result
            try {
                Config.clearWorkflowSessionVars();
                // Set session var to override any potential OS env
                Config.setWorkflowSessionVar(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, "180");
                
                ConcreteConfig config = new ConcreteConfig();
                // Session var should override the default (60) or OS env
                assertEquals(180.0, config.getEnv(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT));
            } finally {
                Config.clearWorkflowSessionVars();
            }
        }
        
        @Test
        @DisplayName("env overrides from os environ (simulated)")
        void testEnvOverridesFromOsEnvironSimulated() {
            // Python: with patch.dict(os.environ, {WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY: "300"}):
            //             config = ConcreteConfig()
            //             assert config.get_env(WORKFLOW_EXECUTE_TIMEOUT) == 300.0
            //
            // Note: Since we can't mock System.getenv() easily in Java, we test the
            // session vars mechanism which has the same override behavior
            try {
                Config.clearWorkflowSessionVars();
                Config.setWorkflowSessionVar(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT_ENV_KEY, "300");
                
                ConcreteConfig config = new ConcreteConfig();
                assertEquals(300.0, config.getEnv(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT));
            } finally {
                Config.clearWorkflowSessionVars();
            }
        }
    }
}

