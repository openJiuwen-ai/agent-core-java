// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for error building and handling
 * 
 * @since 0.1.4
 */
class ErrorsTest {
    
    @Test
    void testBuildErrorReturnsInstance() {
        // Arrange
        Map<String, Object> details = new HashMap<>();
        details.put("tool", "xyz");
        
        // Act
        BaseError error = ErrorBuilder.build(
                StatusCode.AGENT_TOOL_EXECUTION_ERROR,
                "failed",
                details,
                null,
                null
        );
        
        // Assert
        assertNotNull(error);
        assertInstanceOf(BaseError.class, error);
        assertEquals(StatusCode.AGENT_TOOL_EXECUTION_ERROR.getCode(), error.getCode());
        assertEquals(details, error.getDetails());
        assertEquals("failed", error.getCustomMessage());
    }
    
    @Test
    void testRaiseErrorRaisesCorrectType() {
        // Arrange
        Class<? extends BaseError> expectedClass = StatusExceptionMapper.getExceptionClass(
                StatusCode.AGENT_TOOL_EXECUTION_ERROR
        );
        
        // Act & Assert
        assertThrows(expectedClass, () -> {
            ErrorBuilder.raise(StatusCode.AGENT_TOOL_EXECUTION_ERROR, "fail");
        });
    }
    
    @Test
    void testBuildErrorMapsToManualOverride() {
        // Arrange
        StatusCode status = StatusCode.AGENT_TOOL_NOT_FOUND;
        Class<? extends BaseError> exceptionClass = StatusExceptionMapper.getExceptionClass(status);
        
        // Act
        BaseError error = ErrorBuilder.build(status);
        
        // Assert
        assertNotNull(error);
        assertInstanceOf(exceptionClass, error);
    }
    
    @Test
    void testFormatTemplateMissingKeySafe() {
        // Arrange
        String template = StatusCode.WORKFLOW_COMPONENT_RUNTIME_ERROR.getMessage();
        
        // Act
        String rendered = BaseError.formatTemplate(template, null);
        
        // Assert
        assertNotNull(rendered);
        assertTrue(rendered.contains("{error_msg}"), 
                "Template should show missing placeholders");
    }
    
    @Test
    void testBaseErrorToDict() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("error_msg", "test error");
        
        BaseError error = new BaseError(
                StatusCode.WORKFLOW_INPUT_INVALID,
                "custom message",
                null,
                null,
                params
        );
        
        // Act
        Map<String, Object> dict = error.toMap();
        
        // Assert
        assertNotNull(dict);
        assertEquals(StatusCode.WORKFLOW_INPUT_INVALID.getCode(), dict.get("code"));
        assertEquals(StatusCode.WORKFLOW_INPUT_INVALID.name(), dict.get("status"));
        assertTrue(((String) dict.get("message")).contains("test error"));
        assertEquals("custom message", dict.get("raw_message"));
        assertEquals(params, dict.get("params"));
    }
    
    @Test
    void testBaseErrorToJson() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("error_msg", "test");
        
        BaseError error = new BaseError(
                StatusCode.WORKFLOW_INPUT_INVALID,
                null,
                null,
                null,
                params
        );
        
        // Act
        String json = error.toJson();
        
        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"code\""));
        assertTrue(json.contains("\"status\""));
        assertTrue(json.contains("\"message\""));
    }
    
    @Test
    void testErrorBuilderWithBuilder() {
        // Arrange & Act
        BaseError error = BaseError.builder(StatusCode.MODEL_CALL_FAILED)
                .msg("custom message")
                .param("error_msg", "connection timeout")
                .details(Map.of("model", "gpt-4"))
                .build();
        
        // Assert
        assertNotNull(error);
        assertEquals(StatusCode.MODEL_CALL_FAILED.getCode(), error.getCode());
        assertEquals("custom message", error.getCustomMessage());
        assertTrue(error.getTemplateMessage().contains("connection timeout"));
    }
    
    @Test
    void testSystemError() {
        // Act & Assert
        assertThrows(FrameworkError.class, () -> {
            ErrorBuilder.systemError(
                    StatusCode.MODEL_CONFIG_ERROR,
                    new RuntimeException("config issue"),
                    null
            );
        });
    }
    
    @Test
    void testValidateError() {
        // Act & Assert
        assertThrows(ValidationError.class, () -> {
            ErrorBuilder.validateError(
                    StatusCode.WORKFLOW_INPUT_INVALID,
                    null,
                    Map.of("error_msg", "invalid input")
            );
        });
    }
    
    @Test
    void testTerminate() {
        // Act & Assert
        assertThrows(Termination.class, () -> {
            ErrorBuilder.terminate(StatusCode.SUCCESS, null);
        });
    }
    
    @Test
    void testExceptionHierarchy() {
        // Test that exception hierarchy is correct
        WorkflowError workflowError = new WorkflowError(StatusCode.WORKFLOW_EXECUTION_RUNTIME_ERROR);
        assertInstanceOf(ExecutionError.class, workflowError);
        assertInstanceOf(BaseError.class, workflowError);
        assertTrue(workflowError.isRecoverable());
        assertFalse(workflowError.isFatal());
        
        FrameworkError frameworkError = new FrameworkError(StatusCode.ERROR);
        assertInstanceOf(BaseError.class, frameworkError);
        assertFalse(frameworkError.isRecoverable());
        assertTrue(frameworkError.isFatal());
        
        ValidationError validationError = new ValidationError(StatusCode.WORKFLOW_INPUT_INVALID);
        assertInstanceOf(BaseError.class, validationError);
        assertFalse(validationError.isRecoverable());
        assertFalse(validationError.isFatal());
    }
}

