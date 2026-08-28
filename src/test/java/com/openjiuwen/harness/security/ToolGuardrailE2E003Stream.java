/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class ToolGuardrailE2E003Stream {

    private static final String TEST_PROVIDER = "GuardrailStreamE2E";
    private static final String DENIED_MARKER = "[PERMISSION_DENIED]";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    private final Set<String> toolNames = ConcurrentHashMap.newKeySet();
    private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

    ToolGuardrailE2E003Stream() {
        ensureFactoryRegistered();
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        Runner.setConfig(RunnerConfig.DEFAULT);
    }

    @AfterEach
    void cleanup() {
        for (String toolName : toolNames) {
            Runner.resourceMgr().removeTool(toolName, null, TagMatchStrategy.ALL, true);
        }
        for (String sessionId : sessionIds) {
            CheckpointerFactory.getCheckpointer().release(sessionId);
            Runner.release(sessionId);
        }
        Runner.stop();
        Runner.setConfig(RunnerConfig.DEFAULT);
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        toolNames.clear();
        sessionIds.clear();
    }

    @Test
    @DisplayName("ST-S1 流式下拒绝标记看得见")
    void stream_rejectedMarker_visible() {
        AtomicInteger bashCalls = new AtomicInteger();
        ReActAgent agent = newAgent("s1-deny", bashCalls, denyPermissions());
        String sessionId = uniqueSessionId("s1-deny");

        String output = runStream(agent, "BASH:curl http://x", sessionId);

        assertThat(bashCalls.get()).isZero();
        assertThat(output).contains(DENIED_MARKER);
    }

    @Test
    @DisplayName("ST-S2 流式下工具不重复执行")
    void stream_rejectedMarker_appearsOnce() {
        AtomicInteger bashCalls = new AtomicInteger();
        ReActAgent agent = newAgent("s2-once", bashCalls, denyPermissions());
        String sessionId = uniqueSessionId("s2-once");

        String output = runStream(agent, "BASH:curl http://x", sessionId);

        assertThat(bashCalls.get()).isZero();
        assertThat(countOccurrences(output, DENIED_MARKER)).isEqualTo(1);
    }

    @Test
    @DisplayName("ST-S3 流式下中断能让调用方感知")
    void stream_interrupt_visibleToCaller() {
        AtomicInteger bashCalls = new AtomicInteger();
        ReActAgent agent = newAgent("s3-ask", bashCalls, askPermissions());
        String sessionId = uniqueSessionId("s3-ask");

        Iterator<Object> iterator = Runner.runAgentStreaming(agent,
                Map.of("query", "BASH:cat /etc/hosts", "conversation_id", sessionId),
                null, null, List.of(StreamMode.OUTPUT));
        StringBuilder builder = new StringBuilder();
        assertThatCode(() -> iterator.forEachRemaining(chunk -> builder.append(extractText(chunk))))
                .doesNotThrowAnyException();
        String output = builder.toString();

        assertThat(bashCalls.get()).isZero();
        assertThat(output).doesNotContain(DENIED_MARKER);
    }

    private String uniqueSessionId(String tag) {
        String sessionId = "guardrail-stream-" + tag + "-" + UUID.randomUUID();
        sessionIds.add(sessionId);
        return sessionId;
    }

    private String runStream(ReActAgent agent, String query, String sessionId) {
        Iterator<Object> iterator = Runner.runAgentStreaming(agent,
                Map.of("query", query, "conversation_id", sessionId), null, null,
                List.of(StreamMode.OUTPUT));
        StringBuilder builder = new StringBuilder();
        List<Object> chunks = new ArrayList<>();
        iterator.forEachRemaining(chunks::add);
        for (Object chunk : chunks) {
            builder.append(extractText(chunk));
        }
        return builder.toString();
    }

    private static String extractText(Object chunk) {
        if (chunk instanceof OutputSchema outputSchema) {
            return payloadText(outputSchema.getPayload());
        }
        return String.valueOf(chunk);
    }

    private static String payloadText(Object payload) {
        if (payload == null) {
            return "";
        }
        if (payload instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder();
            appendValue(builder, map.get("output"));
            appendValue(builder, map.get("result_type"));
            Object rounds = map.get("rounds");
            if (rounds instanceof List<?> list) {
                for (Object round : list) {
                    if (round instanceof Map<?, ?> roundMap) {
                        appendValue(builder, roundMap.get("output"));
                    }
                }
            }
            return builder.toString();
        }
        return String.valueOf(payload);
    }

    private static void appendValue(StringBuilder builder, Object value) {
        if (value != null) {
            builder.append(String.valueOf(value));
        }
    }

    private static int countOccurrences(String text, String substring) {
        if (text == null || text.isEmpty() || substring == null || substring.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) >= 0) {
            count++;
            index += substring.length();
        }
        return count;
    }

    private ReActAgent newAgent(String tag, AtomicInteger bashCalls, Map<String, Object> permissions) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(tag).name(tag).description("guardrail stream e2e").build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(4)
                .promptTemplate(List.of(Map.of(
                        "role", "system",
                        "content", "你是一个测试助手，严格按用户指令调用工具并总结结果。")))
                .build());
        agent.setLlm(newModel());
        LocalFunction bashTool = countedTool("bash_" + tag, "bash", "command", bashCalls, "BASH_OK");
        toolNames.add(bashTool.getCard().getId());
        Runner.resourceMgr().addTool(bashTool, null);
        agent.getAbilityManager().add(List.of(bashTool.getCard()));
        agent.registerRail(PermissionFactory.buildPermissionInterruptRail(
                permissions, ToolPermissionHost.builder().build(), Path.of("/work")));
        return agent;
    }

    private static LocalFunction countedTool(String toolId, String toolName, String argKey,
            AtomicInteger counter, String resultPrefix) {
        ToolCard card = ToolCard.builder().id(toolId).name(toolName)
                .description("guardrail stream e2e tool " + toolName).build();
        return new LocalFunction(card, inputs -> {
            counter.incrementAndGet();
            String result = resultPrefix + ":" + String.valueOf(inputs.get(argKey));
            return Collections.singletonList(result).iterator();
        });
    }

    private static Model newModel() {
        ensureFactoryRegistered();
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientId("guardrail-stream-e2e").clientProvider(TEST_PROVIDER)
                .apiKey("test-key").apiBase("mirror://guardrail-stream-e2e").build();
        return new Model(clientConfig, ModelRequestConfig.builder()
                .modelName("guardrail-stream-e2e-model").build());
    }

    private static void ensureFactoryRegistered() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new Model.ModelClientFactory() {
                @Override
                public String providerName() {
                    return TEST_PROVIDER;
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return new GuardrailStreamE2EModelClient(modelConfig, clientConfig);
                }
            });
        }
    }

    private static Map<String, Object> denyPermissions() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("schema", "tiered_policy");
        cfg.put("permission_mode", "normal");
        cfg.put("tools", Map.of("bash", "allow"));
        cfg.put("defaults", Map.of("*", "allow"));
        cfg.put("rules", List.of(Map.of(
                "id", "curl", "tools", List.of("bash"),
                "pattern", "curl *", "action", "deny")));
        cfg.put("approval_overrides", List.of());
        return cfg;
    }

    private static Map<String, Object> askPermissions() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("schema", "tiered_policy");
        cfg.put("permission_mode", "normal");
        cfg.put("tools", Map.of("bash", "ask"));
        cfg.put("defaults", Map.of("*", "allow"));
        cfg.put("approval_overrides", List.of());
        return cfg;
    }

    private static final class GuardrailStreamE2EModelClient extends BaseModelClient {
        private GuardrailStreamE2EModelClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                String model, Integer maxTokens, String stop, BaseOutputParser outputParser,
                Float timeout, Map<String, Object> kwargs) {
            List<MessageView> views = toMessageViews(messages);
            boolean hasTool = views.stream().anyMatch(view -> "tool".equals(view.role()));
            if (hasTool) {
                String lastTool = views.stream().filter(view -> "tool".equals(view.role()))
                        .reduce((left, right) -> right).map(view -> view.content).orElse("");
                return new AssistantMessage("FINAL:" + lastTool);
            }
            String user = views.stream().filter(view -> "user".equals(view.role()))
                    .reduce((left, right) -> right).map(view -> view.content).orElse("");
            if (user.startsWith("BASH:")) {
                String command = user.substring("BASH:".length());
                return toolCall("bash", "{\"command\":\"" + command + "\"}");
            }
            if (user.startsWith("READ:")) {
                String filePath = user.substring("READ:".length());
                return toolCall("read_file", "{\"file_path\":\"" + filePath + "\"}");
            }
            return new AssistantMessage("FINAL:noop");
        }

        private static AssistantMessage toolCall(String name, String arguments) {
            return AssistantMessage.builder().content("")
                    .toolCalls(List.of(ToolCall.builder().id("call_" + UUID.randomUUID())
                            .name(name).arguments(arguments).build()))
                    .finishReason("tool_calls").build();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature,
                Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser,
                Float timeout, Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("image generation is not used in this e2e test");
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("speech generation is not used in this e2e test");
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl,
                String audioUrl, String model, String size, String resolution, int duration,
                boolean promptExtend, boolean watermark, String negativePrompt, Integer seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("video generation is not used in this e2e test");
        }

        private static List<MessageView> toMessageViews(Object messages) {
            List<MessageView> result = new ArrayList<>();
            if (messages instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof ToolMessage toolMessage) {
                        result.add(new MessageView("tool", String.valueOf(toolMessage.getContent())));
                    } else if (item instanceof BaseMessage message) {
                        result.add(new MessageView(message.getRole(), message.getContentAsString()));
                    }
                }
            }
            return result;
        }
    }

    private record MessageView(String role, String content) {
    }
}
