/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeTemplate 单元测试
 */
@DisplayName("CodeTemplate - 状态码模板生成工具测试")
class CodeTemplateTest {
    
    @Nested
    @DisplayName("常量集合测试")
    class ConstantsTest {
        
        @Test
        @DisplayName("ALLOWED_SCOPES 包含所有预期的作用域")
        void allowedScopesContainsExpectedValues() {
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("WORKFLOW"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("COMPONENT"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("AGENT"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("TOOL"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("MODEL"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("SESSION"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("GRAPH"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("CONTROLLER"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("RUNNER"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("PROMPT"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("COMMON"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("CONTEXT"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("TOOLCHAIN"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("MEMORY"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("RETRIEVAL"));
            assertTrue(CodeTemplate.ALLOWED_SCOPES.contains("SYS_OPERATION"));
            assertEquals(16, CodeTemplate.ALLOWED_SCOPES.size());
        }
        
        @Test
        @DisplayName("ALLOWED_FAILURE_TYPES 包含所有预期的失败类型")
        void allowedFailureTypesContainsExpectedValues() {
            // Validation types
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("INVALID"));
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("NOT_FOUND"));
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("NOT_SUPPORTED"));
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("CONFIG_ERROR"));
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("PARAM_ERROR"));
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("TYPE_ERROR"));
            
            // Framework types
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("INIT_FAILED"));
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("CALL_FAILED"));
            
            // Execution types
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("EXECUTION_ERROR"));
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("RUNTIME_ERROR"));
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("PROCESS_ERROR"));
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("TIMEOUT"));
            assertTrue(CodeTemplate.ALLOWED_FAILURE_TYPES.contains("INTERRUPTED"));
            
            assertEquals(13, CodeTemplate.ALLOWED_FAILURE_TYPES.size());
        }
    }
    
    @Nested
    @DisplayName("exceptionSemanticFromFailure 测试")
    class ExceptionSemanticTest {
        
        @Test
        @DisplayName("Validation 类型失败返回 ValidationError")
        void validationTypesReturnValidationError() {
            assertEquals("ValidationError", CodeTemplate.exceptionSemanticFromFailure("INVALID"));
            assertEquals("ValidationError", CodeTemplate.exceptionSemanticFromFailure("NOT_FOUND"));
            assertEquals("ValidationError", CodeTemplate.exceptionSemanticFromFailure("NOT_SUPPORTED"));
            assertEquals("ValidationError", CodeTemplate.exceptionSemanticFromFailure("CONFIG_ERROR"));
            assertEquals("ValidationError", CodeTemplate.exceptionSemanticFromFailure("PARAM_ERROR"));
        }
        
        @Test
        @DisplayName("Framework 类型失败返回 FrameworkError")
        void frameworkTypesReturnFrameworkError() {
            assertEquals("FrameworkError", CodeTemplate.exceptionSemanticFromFailure("INIT_FAILED"));
            assertEquals("FrameworkError", CodeTemplate.exceptionSemanticFromFailure("CALL_FAILED"));
        }
        
        @Test
        @DisplayName("其他类型失败返回 ExecutionError")
        void otherTypesReturnExecutionError() {
            assertEquals("ExecutionError", CodeTemplate.exceptionSemanticFromFailure("EXECUTION_ERROR"));
            assertEquals("ExecutionError", CodeTemplate.exceptionSemanticFromFailure("RUNTIME_ERROR"));
            assertEquals("ExecutionError", CodeTemplate.exceptionSemanticFromFailure("TIMEOUT"));
            assertEquals("ExecutionError", CodeTemplate.exceptionSemanticFromFailure("UNKNOWN_TYPE"));
        }
    }
    
    @Nested
    @DisplayName("codeRangeByScope 测试")
    class CodeRangeTest {
        
        @Test
        @DisplayName("已知作用域返回正确的代码范围")
        void knownScopesReturnCorrectRanges() {
            assertEquals("100000–100999", CodeTemplate.codeRangeByScope("WORKFLOW"));
            assertEquals("101000–119999", CodeTemplate.codeRangeByScope("COMPONENT"));
            assertEquals("120000–129999", CodeTemplate.codeRangeByScope("AGENT"));
            assertEquals("130000–139999", CodeTemplate.codeRangeByScope("RUNNER"));
            assertEquals("140000–149999", CodeTemplate.codeRangeByScope("GRAPH"));
            assertEquals("150000–154999", CodeTemplate.codeRangeByScope("CONTEXT"));
            assertEquals("155000-157999", CodeTemplate.codeRangeByScope("RETRIEVAL"));
            assertEquals("158000-159999", CodeTemplate.codeRangeByScope("MEMORY"));
            assertEquals("160000–179999", CodeTemplate.codeRangeByScope("TOOLCHAIN"));
            assertEquals("180000-180999", CodeTemplate.codeRangeByScope("PROMPT"));
            assertEquals("181000–181999", CodeTemplate.codeRangeByScope("MODEL"));
            assertEquals("182000-182999", CodeTemplate.codeRangeByScope("TOOL"));
            assertEquals("188000-188999", CodeTemplate.codeRangeByScope("COMMON"));
            assertEquals("190000–198999", CodeTemplate.codeRangeByScope("SESSION"));
            assertEquals("199000–199999", CodeTemplate.codeRangeByScope("SYS_OPERATION"));
        }
        
        @Test
        @DisplayName("未知作用域返回 'custom'")
        void unknownScopeReturnsCustom() {
            assertEquals("custom", CodeTemplate.codeRangeByScope("UNKNOWN"));
            assertEquals("custom", CodeTemplate.codeRangeByScope(""));
        }
    }
    
    @Nested
    @DisplayName("generateStatusCode 测试")
    class GenerateStatusCodeTest {
        
        @Test
        @DisplayName("生成基本状态码模板")
        void generatesBasicStatusCodeTemplate() {
            var template = CodeTemplate.generateStatusCode("WORKFLOW", "input", "INVALID");
            
            assertEquals("WORKFLOW_input_INVALID", template.name());
            assertEquals("100000–100999", template.codeSuggestion());
            assertEquals("workflow input is invalid, reason: {error_msg}", template.messageTemplate());
            assertEquals("ValidationError", template.exceptionSemantic());
        }
        
        @Test
        @DisplayName("生成带 detail 的状态码模板")
        void generatesStatusCodeTemplateWithDetail() {
            var template = CodeTemplate.generateStatusCode("AGENT", "state", "RUNTIME_ERROR", "execution");
            
            assertEquals("AGENT_execution_state_RUNTIME_ERROR", template.name());
            assertEquals("120000–129999", template.codeSuggestion());
            assertEquals("agent state runtime error, reason: {error_msg}", template.messageTemplate());
            assertEquals("ExecutionError", template.exceptionSemantic());
        }
        
        @Test
        @DisplayName("无效的 scope 抛出异常")
        void invalidScopeThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> 
                CodeTemplate.generateStatusCode("INVALID_SCOPE", "subject", "INVALID"));
        }
        
        @Test
        @DisplayName("无效的 failureType 抛出异常")
        void invalidFailureTypeThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> 
                CodeTemplate.generateStatusCode("WORKFLOW", "subject", "INVALID_FAILURE"));
        }
    }
    
    @Nested
    @DisplayName("generateErrorMessageTemplate 测试")
    class GenerateMessageTemplateTest {
        
        @Test
        @DisplayName("INVALID 类型消息模板")
        void invalidTypeMessageTemplate() {
            var template = CodeTemplate.generateErrorMessageTemplate("WORKFLOW", "input", "INVALID", true);
            
            assertEquals("workflow input is invalid, reason: {error_msg}", template.template());
            assertTrue(template.params().contains("error_msg"));
        }
        
        @Test
        @DisplayName("NOT_FOUND 类型消息模板")
        void notFoundTypeMessageTemplate() {
            var template = CodeTemplate.generateErrorMessageTemplate("AGENT", "config", "NOT_FOUND", true);
            
            assertEquals("agent config not found, reason: {error_msg}", template.template());
        }
        
        @Test
        @DisplayName("TIMEOUT 类型消息模板包含 timeout 参数")
        void timeoutTypeIncludesTimeoutParam() {
            var template = CodeTemplate.generateErrorMessageTemplate("TOOL", "call", "TIMEOUT", true);
            
            assertEquals("tool call timeout ({timeout}s), reason: {error_msg}", template.template());
            assertTrue(template.params().contains("timeout"));
            assertTrue(template.params().contains("error_msg"));
        }
        
        @Test
        @DisplayName("withReason=false 不包含 reason")
        void withoutReasonExcludesErrorMsg() {
            var template = CodeTemplate.generateErrorMessageTemplate("WORKFLOW", "input", "INVALID", false);
            
            assertEquals("workflow input is invalid", template.template());
            assertFalse(template.params().contains("error_msg"));
        }
        
        @Test
        @DisplayName("不支持的 failureType 抛出异常")
        void unsupportedFailureTypeThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> 
                CodeTemplate.generateErrorMessageTemplate("WORKFLOW", "subject", "UNKNOWN_TYPE", true));
        }
    }
    
    @Nested
    @DisplayName("generateStatusCodeSpec 测试")
    class GenerateStatusCodeSpecTest {
        
        @Test
        @DisplayName("从模板生成状态码规格")
        void generatesSpecFromTemplate() {
            var template = CodeTemplate.generateStatusCode("WORKFLOW", "input", "INVALID");
            var spec = CodeTemplate.generateStatusCodeSpec(template, 100000);
            
            assertEquals("WORKFLOW_input_INVALID", spec.name());
            assertEquals(100000, spec.code());
            assertEquals("workflow input is invalid, reason: {error_msg}", spec.message());
        }
    }
    
    @Nested
    @DisplayName("renderEnumMember 测试")
    class RenderEnumMemberTest {
        
        @Test
        @DisplayName("渲染枚举成员代码")
        void rendersEnumMemberCode() {
            var spec = new CodeTemplate.StatusCodeSpec("WORKFLOW_INPUT_INVALID", 100000, 
                "workflow input is invalid, reason: {error_msg}");
            
            String rendered = CodeTemplate.renderEnumMember(spec);
            
            assertEquals("    WORKFLOW_INPUT_INVALID(100000, \"workflow input is invalid, reason: {error_msg}\")", 
                rendered);
        }
    }
}

