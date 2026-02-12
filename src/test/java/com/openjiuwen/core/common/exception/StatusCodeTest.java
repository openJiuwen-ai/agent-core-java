// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StatusCode enum
 * 
 * @since 0.1.4
 */
class StatusCodeTest {
    
    @Test
    void testBasicStatusCodes() {
        assertEquals(0, StatusCode.SUCCESS.getCode());
        assertEquals("success", StatusCode.SUCCESS.getMessage());
        
        assertEquals(-1, StatusCode.ERROR.getCode());
        assertEquals("error", StatusCode.ERROR.getMessage());
    }
    
    @Test
    void testWorkflowStatusCodes() {
        StatusCode code = StatusCode.WORKFLOW_INPUT_INVALID;
        assertEquals(100000, code.getCode());
        assertTrue(code.getMessage().contains("workflow input is invalid"));
        assertTrue(code.getMessage().contains("{error_msg}"));
    }
    
    @Test
    void testFromCode() {
        StatusCode status = StatusCode.fromCode(100000);
        assertEquals(StatusCode.WORKFLOW_INPUT_INVALID, status);
        
        StatusCode status2 = StatusCode.fromCode(0);
        assertEquals(StatusCode.SUCCESS, status2);
        
        StatusCode unknownStatus = StatusCode.fromCode(999999);
        assertEquals(StatusCode.ERROR, unknownStatus);
    }
    
    @Test
    void testFormatMessage() {
        Map<String, Object> params = new HashMap<>();
        params.put("error_msg", "invalid parameter");
        
        StatusCode code = StatusCode.WORKFLOW_INPUT_INVALID;
        String formatted = code.formatMessage(params);
        
        assertTrue(formatted.contains("invalid parameter"));
        assertFalse(formatted.contains("{error_msg}"));
    }
    
    @Test
    void testFormatMessageWithTimeout() {
        Map<String, Object> params = new HashMap<>();
        params.put("timeout", 30);
        params.put("error_msg", "operation timed out");
        
        StatusCode code = StatusCode.WORKFLOW_INVOKE_TIMEOUT;
        String formatted = code.formatMessage(params);
        
        assertTrue(formatted.contains("30"));
        assertTrue(formatted.contains("operation timed out"));
    }
    
    @Test
    void testFormatMessageWithoutParams() {
        StatusCode code = StatusCode.SUCCESS;
        String formatted = code.formatMessage(null);
        
        assertEquals("success", formatted);
    }
    
    @Test
    void testFormatMessageWithMissingParams() {
        StatusCode code = StatusCode.WORKFLOW_INPUT_INVALID;
        String formatted = code.formatMessage(null);
        
        // Should have <missing:error_msg> placeholder
        assertTrue(formatted.contains("{error_msg}"));
    }
    
    @Test
    void testAllStatusCodesHaveUniqueNames() {
        Map<String, StatusCode> names = new HashMap<>();
        for (StatusCode status : StatusCode.values()) {
            String name = status.name();
            assertFalse(names.containsKey(name), 
                    "Duplicate status code name: " + name);
            names.put(name, status);
        }
    }
    
    @Test
    void testAllStatusCodesHaveMessages() {
        for (StatusCode status : StatusCode.values()) {
            assertNotNull(status.getMessage(), 
                    "StatusCode " + status.name() + " has null message");
            assertFalse(status.getMessage().isEmpty(), 
                    "StatusCode " + status.name() + " has empty message");
        }
    }
    
    @Test
    void testCodeRanges() {
        // Verify status codes are in expected ranges
        assertTrue(StatusCode.WORKFLOW_INPUT_INVALID.getCode() >= 100000);
        assertTrue(StatusCode.WORKFLOW_INPUT_INVALID.getCode() < 110000);
        
        assertTrue(StatusCode.AGENT_TOOL_NOT_FOUND.getCode() >= 120000);
        assertTrue(StatusCode.AGENT_TOOL_NOT_FOUND.getCode() < 130000);
        
        assertTrue(StatusCode.MODEL_CALL_FAILED.getCode() >= 180000);
        assertTrue(StatusCode.MODEL_CALL_FAILED.getCode() < 190000);
    }
    
    @Test
    void testAgentStatusCodes() {
        assertEquals(120000, StatusCode.AGENT_TOOL_NOT_FOUND.getCode());
        assertEquals(120001, StatusCode.AGENT_TOOL_EXECUTION_ERROR.getCode());
        assertEquals(120002, StatusCode.AGENT_TASK_NOT_SUPPORT.getCode());
    }
    
    @Test
    void testModelStatusCodes() {
        assertEquals(181000, StatusCode.MODEL_PROVIDER_INVALID.getCode());
        assertEquals(181001, StatusCode.MODEL_CALL_FAILED.getCode());
        assertEquals(181002, StatusCode.MODEL_SERVICE_CONFIG_ERROR.getCode());
    }
    
    @Test
    void testRetrievalStatusCodes() {
        assertEquals(155000, StatusCode.RETRIEVAL_EMBEDDING_INPUT_INVALID.getCode());
        assertEquals(155100, StatusCode.RETRIEVAL_INDEXING_CHUNK_SIZE_INVALID.getCode());
        assertEquals(155200, StatusCode.RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT.getCode());
    }
}

