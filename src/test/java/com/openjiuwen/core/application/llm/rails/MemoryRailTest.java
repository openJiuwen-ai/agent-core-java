/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm.rails;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.memory.MemInfo;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for application-level LLM memory rail.
 *
 * <p>Mirrors Python's {@code MemoryRail} behavior in
 * {@code openjiuwen.core.application.llm_agent.rails.memory_rail}.</p>
 */
class MemoryRailTest {

    @Test
    void beforeInvokeSkipsResumePath() {
        FakeMemoryClient memory = new FakeMemoryClient();
        MemoryRail rail = new MemoryRail("scope_001", new AgentMemoryConfig(), memory);
        Map<String, Object> extra = new HashMap<>();
        extra.put("user_id", "user_001");
        extra.put("is_resume", true);
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .inputs(InvokeInputs.builder().query("hello").build())
                .extra(extra)
                .build();

        rail.beforeInvoke(ctx);

        assertFalse(ctx.getExtra().containsKey(MemoryRail.MEMORY_VARIABLES_KEY));
        assertEquals(0, memory.getVariablesCalls);
        assertEquals(0, memory.searchUserMemCalls);
    }

    @Test
    @SuppressWarnings("unchecked")
    void beforeInvokeLoadsConfiguredMemoryFields() {
        FakeMemoryClient memory = new FakeMemoryClient();
        memory.variables.put("nickname", "Jun");
        memory.variables.put("ignored", "value");
        memory.fragmentResults.add(MemResult.builder()
                .memInfo(MemInfo.builder().content("偏好：数学").build())
                .build());

        AgentMemoryConfig config = AgentMemoryConfig.builder()
                .memVariables(List.of(Param.string("nickname", "nickname", false)))
                .enableLongTermMem(true)
                .enableUserProfile(true)
                .enableSemanticMemory(false)
                .enableEpisodicMemory(false)
                .enableSummaryMemory(false)
                .build();
        MemoryRail rail = new MemoryRail("scope_001", config, memory);
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .inputs(InvokeInputs.builder().query("1+2").build())
                .extra(new HashMap<>(Map.of("user_id", "user_001")))
                .build();

        rail.beforeInvoke(ctx);

        Map<String, Object> memoryVariables =
                (Map<String, Object>) ctx.getExtra().get(MemoryRail.MEMORY_VARIABLES_KEY);
        String variableJson = String.valueOf(memoryVariables.get("sys_memory_variables"));
        String longTermJson = String.valueOf(memoryVariables.get("sys_long_term_memory"));

        assertTrue(variableJson.contains("\"nickname\":\"Jun\""));
        assertFalse(variableJson.contains("ignored"));
        assertTrue(longTermJson.contains("用户画像记忆"));
        assertTrue(longTermJson.contains("偏好：数学"));
        assertEquals("1+2", ctx.getExtra().get(MemoryRail.ORIGINAL_QUERY_KEY));
        assertEquals("user_001", memory.lastUserId);
        assertEquals("scope_001", memory.lastScopeId);
        assertEquals("1+2", memory.lastQuery);
    }

    @Test
    void afterInvokeWritesOnlyAnswerTurns() {
        FakeMemoryClient memory = new FakeMemoryClient();
        MemoryRail rail = new MemoryRail("scope_001", new AgentMemoryConfig(), memory);
        Map<String, Object> extra = new HashMap<>();
        extra.put("user_id", "user_001");
        extra.put(MemoryRail.ORIGINAL_QUERY_KEY, "hello");
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .inputs(InvokeInputs.builder()
                        .conversationId("conv_001")
                        .result(Map.of("result_type", "answer", "output", "hi"))
                        .build())
                .extra(extra)
                .build();

        rail.afterInvoke(ctx);

        assertEquals(1, memory.addMessagesCalls);
        assertEquals("user_001", memory.lastUserId);
        assertEquals("scope_001", memory.lastScopeId);
        assertEquals("conv_001", memory.lastSessionId);
        assertEquals(2, memory.addedMessages.size());
        assertEquals("user", memory.addedMessages.get(0).getRole());
        assertEquals("hello", memory.addedMessages.get(0).getContentAsString());
        assertEquals("assistant", memory.addedMessages.get(1).getRole());
        assertEquals("hi", memory.addedMessages.get(1).getContentAsString());
    }

    @Test
    void reActAgentRendersMemoryPlaceholderAcrossIterations() {
        RecordingModelClient.reset();
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return RecordingModelClient.PROVIDER;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                return new RecordingModelClient(modelConfig, clientConfig);
            }
        });

        Runner.start();
        AddTool tool = new AddTool();
        try {
            Runner.resourceMgr().addTool(tool, null);
            ReActAgent agent = new ReActAgent(AgentCard.builder()
                    .id("memory_prompt_agent")
                    .name("memory_prompt_agent")
                    .description("memory assistant")
                    .build());
            agent.configure(ReActAgentConfig.builder()
                    .modelClientConfig(ModelClientConfig.builder()
                            .clientProvider(RecordingModelClient.PROVIDER)
                            .apiKey("sk-test")
                            .apiBase("https://mock.openjiuwen.local/v1")
                            .verifySsl(false)
                            .build())
                    .modelConfigObj(ModelRequestConfig.builder().modelName("mock-model").build())
                    .promptTemplate(List.of(Map.of("role", "system", "content", "记忆信息：{{sys_long_term_memory}}")))
                    .build());
            agent.getAbilityManager().add(tool.getCard());

            FakeMemoryClient memory = new FakeMemoryClient();
            memory.fragmentResults.add(MemResult.builder()
                    .memInfo(MemInfo.builder().content("偏好：数学").build())
                    .build());
            AgentMemoryConfig memoryConfig = AgentMemoryConfig.builder()
                    .enableLongTermMem(true)
                    .enableUserProfile(true)
                    .enableSemanticMemory(false)
                    .enableEpisodicMemory(false)
                    .enableSummaryMemory(false)
                    .build();
            agent.registerRail(new MemoryRail("scope_001", memoryConfig, memory));

            agent.invoke(Map.of("query", "1+2", "user_id", "user_001"), null);

            assertEquals(2, RecordingModelClient.callHistory.size());
            for (List<BaseMessage> call : RecordingModelClient.callHistory) {
                List<BaseMessage> systemMessages = call.stream()
                        .filter(message -> "system".equals(message.getRole()))
                        .toList();
                assertEquals(1, systemMessages.size());
                String content = systemMessages.getFirst().getContentAsString();
                assertTrue(content.contains("偏好：数学"));
                assertFalse(content.contains("{{sys_long_term_memory}}"));
            }
        } finally {
            Runner.resourceMgr().removeTool(tool.getCard().getId(), null, TagMatchStrategy.ALL, true);
            Runner.stop();
        }
    }

    private static final class FakeMemoryClient implements MemoryRail.MemoryClient {
        private final Map<String, String> variables = new LinkedHashMap<>();
        private final List<MemResult> fragmentResults = new ArrayList<>();
        private final List<MemResult> summaryResults = new ArrayList<>();
        private final List<BaseMessage> addedMessages = new ArrayList<>();
        private int getVariablesCalls;
        private int searchUserMemCalls;
        private int addMessagesCalls;
        private String lastUserId;
        private String lastScopeId;
        private String lastSessionId;
        private String lastQuery;

        @Override
        public Map<String, String> getVariables(Object names, String userId, String scopeId) {
            getVariablesCalls++;
            lastUserId = userId;
            lastScopeId = scopeId;
            return variables;
        }

        @Override
        public List<MemResult> searchUserMem(String query, int num, String userId, String scopeId, double threshold) {
            searchUserMemCalls++;
            lastQuery = query;
            lastUserId = userId;
            lastScopeId = scopeId;
            return fragmentResults;
        }

        @Override
        public List<MemResult> searchUserHistorySummary(String query, int num, String userId, String scopeId,
                                                        double threshold) {
            return summaryResults;
        }

        @Override
        public void addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig, String userId,
                                String scopeId, String sessionId) {
            addMessagesCalls++;
            addedMessages.clear();
            addedMessages.addAll(messages);
            lastUserId = userId;
            lastScopeId = scopeId;
            lastSessionId = sessionId;
        }
    }

    private static final class AddTool extends Tool {
        AddTool() {
            super(ToolCard.builder()
                    .id("add")
                    .name("add")
                    .description("Add two integers")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "a", Map.of("type", "integer"),
                                    "b", Map.of("type", "integer")
                            )
                    ))
                    .build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            int a = Integer.parseInt(String.valueOf(inputs.getOrDefault("a", "0")));
            int b = Integer.parseInt(String.valueOf(inputs.getOrDefault("b", "0")));
            return Map.of("result", a + b);
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of(invoke(inputs, kwargs)).iterator();
        }
    }

    private static final class RecordingModelClient extends BaseModelClient {
        private static final String PROVIDER = "MemoryRailPromptMock";
        private static final List<List<BaseMessage>> callHistory = new ArrayList<>();
        private static int callCount;

        RecordingModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        static void reset() {
            callHistory.clear();
            callCount = 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            callHistory.add(new ArrayList<>((List<BaseMessage>) messages));
            callCount++;
            if (callCount == 1) {
                return AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call_add")
                                .name("add")
                                .arguments("{\"a\":1,\"b\":2}")
                                .build()))
                        .build();
            }
            return AssistantMessage.builder().content("done").build();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.of(AssistantMessageChunk.builder().content("done").build()).iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                     String negativePrompt, int n, boolean promptExtend,
                                                     boolean watermark, int seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("image generation is not used in this test");
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("speech generation is not used in this test");
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark, String negativePrompt,
                                                     Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("video generation is not used in this test");
        }
    }
}
