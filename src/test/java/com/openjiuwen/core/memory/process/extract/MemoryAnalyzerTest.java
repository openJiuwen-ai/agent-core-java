package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemoryAnalyzerTest {

    @AfterEach
    void clearPromptCache() {
        com.openjiuwen.core.memory.prompt.PromptApplier.getInstance().clearCache();
    }

    @Test
    void analyzeInjectsForbiddenVariablesIntoPrompt() throws Exception {
        Model model = mock(Model.class);
        doReturn(new AssistantMessage("""
                ```json
                {
                  \"has_key_information\": true,
                  \"variables\": [],
                  \"summary\": \"summary\"
                }
                ```
                """)).when(model).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        AgentMemoryConfig config = AgentMemoryConfig.builder()
                .memVariables(List.of(Param.string("nickname", "user nickname", false)))
                .build();

        MemoryAnalyzerResult result = MemoryAnalyzer.analyze(
                List.of(new BaseMessage("user", "我叫小王，手机号是13800001111")),
                List.of(new BaseMessage("assistant", "你好")),
                Map.entry("test-model", model),
                config,
                128,
                "手机号,证件号"
        );

        assertNotNull(result);
        ArgumentCaptor<Object> inputCaptor = ArgumentCaptor.forClass(Object.class);
        verify(model).invoke(inputCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        @SuppressWarnings("unchecked")
        List<BaseMessage> modelInput = (List<BaseMessage>) inputCaptor.getValue();
        String prompt = modelInput.get(0).getContentAsString();
        assertTrue(prompt.contains("禁止记忆变量定义如下："));
        assertTrue(prompt.contains("手机号,证件号"));
        assertTrue(prompt.contains("variable_key"));
    }

    @Test
    void analyzeNormalizesBlankForbiddenVariablesToNone() throws Exception {
        Model model = mock(Model.class);
        doReturn(new AssistantMessage("""
                ```json
                {
                  \"has_key_information\": false,
                  \"variables\": [],
                  \"summary\": \"\"
                }
                ```
                """)).when(model).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        MemoryAnalyzer.analyze(
                List.of(new BaseMessage("user", "hello")),
                null,
                Map.entry("test-model", model),
                AgentMemoryConfig.builder().build(),
                64,
                "  "
        );

        ArgumentCaptor<Object> inputCaptor = ArgumentCaptor.forClass(Object.class);
        verify(model).invoke(inputCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        @SuppressWarnings("unchecked")
        List<BaseMessage> modelInput = (List<BaseMessage>) inputCaptor.getValue();
        String prompt = modelInput.get(0).getContentAsString();
        assertTrue(prompt.contains("禁止记忆变量定义如下："));
        assertTrue(prompt.contains("None"));
    }

    @Test
    void analyzeParsesReturnedVariables() throws Exception {
        Model model = mock(Model.class);
        doReturn(new AssistantMessage("""
                ```json
                {
                  \"has_key_information\": true,
                  \"variables\": [
                    {\"variable_key\": \"nickname\", \"variable_value\": \"小王\"}
                  ],
                  \"summary\": \"用户昵称是小王\"
                }
                ```
                """)).when(model).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        MemoryAnalyzerResult result = MemoryAnalyzer.analyze(
                List.of(new BaseMessage("user", "我叫小王")),
                null,
                Map.entry("test-model", model),
                AgentMemoryConfig.builder()
                        .memVariables(List.of(Param.string("nickname", "user nickname", false)))
                        .build(),
                64,
                null
        );

        assertEquals(1, result.getVariables().size());
        assertEquals("nickname", result.getVariables().get(0).getVariableKey());
        assertEquals("小王", result.getVariables().get(0).getVariableValue());
        assertEquals("用户昵称是小王", result.getSummary());
    }
}
