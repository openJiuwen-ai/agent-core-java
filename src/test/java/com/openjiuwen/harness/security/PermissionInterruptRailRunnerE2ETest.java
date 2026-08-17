/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.security.PermissionInterruptRail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runner-level E2E for the tool guardrail (issue #71). Drives a {@link ReActAgent} through
 * {@link Runner#runAgentStreaming} with a scripted mock LLM that emits bash/read_file/
 * write_file tool calls, while a {@link PermissionInterruptRail} (built via
 * {@link PermissionFactory}) enforces the dual pipeline end to end:
 * <ul>
 *   <li>bash {@code cat /etc/hosts} - rule {@code cat *} allow - tool runs and returns content;</li>
 *   <li>bash {@code curl http://x} - rule {@code curl *} deny - tool skipped, [PERMISSION_DENIED];</li>
 *   <li>bash {@code rm -rf /tmp/secret} - rule {@code rm *} deny - tool skipped, [PERMISSION_DENIED];</li>
 *   <li>{@code read_file /etc/hosts} - file_guard read=allow - tool runs and returns content;</li>
 *   <li>{@code write_file /etc/hosts} - file_guard write=deny - tool skipped, [PERMISSION_DENIED].</li>
 * </ul>
 * This complements {@link ToolGuardrailAcceptanceTest}, which invokes the rail directly on a
 * synthetic context, by exercising the real agent loop, the Runner resource manager, and the
 * tool-execution callback where {@code _skip_tool} is honored.
 *
 * @since 0.1.15
 */
class PermissionInterruptRailRunnerE2ETest {

    private static final String TEST_PROVIDER = "GuardrailRunnerE2EMirror";
    private static final String DENIED_MARKER = "[PERMISSION_DENIED]";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    private final Set<String> toolNames = ConcurrentHashMap.newKeySet();
    private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

    PermissionInterruptRailRunnerE2ETest() {
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
    void bashCatAllow_runsToolAndReturnsContent() {
        AtomicInteger bashCalls = new AtomicInteger();
        ReActAgent agent = newAgent("cat-allow", bashCalls, new AtomicInteger(), new AtomicInteger());
        String sessionId = uniqueSessionId("cat-allow");

        String output = runAgent(agent, "请用 CAT 查看主机配置", sessionId);

        assertThat(bashCalls.get()).isEqualTo(1);
        assertThat(output).contains("BASH_OK");
        assertThat(output).doesNotContain(DENIED_MARKER);
    }

    @Test
    void bashCurlDeny_skipsToolAndReturnsPermissionDenied() {
        AtomicInteger bashCalls = new AtomicInteger();
        ReActAgent agent = newAgent("curl-deny", bashCalls, new AtomicInteger(), new AtomicInteger());
        String sessionId = uniqueSessionId("curl-deny");

        String output = runAgent(agent, "请用 CURL 抓取外部地址", sessionId);

        assertThat(bashCalls.get()).isZero();
        assertThat(output).contains(DENIED_MARKER);
    }

    @Test
    void bashRmDeny_skipsToolAndReturnsPermissionDenied() {
        AtomicInteger bashCalls = new AtomicInteger();
        ReActAgent agent = newAgent("rm-deny", bashCalls, new AtomicInteger(), new AtomicInteger());
        String sessionId = uniqueSessionId("rm-deny");

        String output = runAgent(agent, "请用 RM 删除临时数据", sessionId);

        assertThat(bashCalls.get()).isZero();
        assertThat(output).contains(DENIED_MARKER);
    }

    @Test
    void etcHostsReadFileGuardAllow_runsTool() {
        AtomicInteger readCalls = new AtomicInteger();
        ReActAgent agent = newAgent("hosts-read", new AtomicInteger(), readCalls, new AtomicInteger());
        String sessionId = uniqueSessionId("hosts-read");

        String output = runAgent(agent, "请用 READ 读取 hosts 文件", sessionId);

        assertThat(readCalls.get()).isEqualTo(1);
        assertThat(output).contains("READ_OK");
        assertThat(output).doesNotContain(DENIED_MARKER);
    }

    @Test
    void etcHostsWriteFileGuardDeny_skipsTool() {
        AtomicInteger writeCalls = new AtomicInteger();
        ReActAgent agent = newAgent("hosts-write", new AtomicInteger(), new AtomicInteger(), writeCalls);
        String sessionId = uniqueSessionId("hosts-write");

        String output = runAgent(agent, "请用 WRITE 修改 hosts 文件", sessionId);

        assertThat(writeCalls.get()).isZero();
        assertThat(output).contains(DENIED_MARKER);
    }

    @Test
    void multiRail_blocked_observerSeesPermissionDeniedAndPriorityOrdering() {
        AtomicInteger bashCalls = new AtomicInteger();
        List<String> observed = new ArrayList<>();
        ReActAgent agent = newAgent("multi-rail-blocked", bashCalls, new AtomicInteger(),
                new AtomicInteger());
        ToolCallLoggerRail observer = new ToolCallLoggerRail(observed);
        agent.registerRail(observer);
        String sessionId = uniqueSessionId("multi-rail-blocked");

        String output = runAgent(agent, "请用 CURL 抓取外部地址", sessionId);

        PermissionInterruptRail permissionRail = PermissionFactory.buildPermissionInterruptRail(
                acceptancePermissions(), ToolPermissionHost.builder().build(), Path.of("/work"));
        assertThat(permissionRail.getPriority()).isEqualTo(90);
        assertThat(observer.getPriority()).isLessThan(permissionRail.getPriority());
        assertThat(bashCalls.get()).isZero();
        assertThat(output).contains(DENIED_MARKER);
        assertThat(observed).anyMatch(entry -> entry.contains(DENIED_MARKER));
    }

    @Test
    void multiRail_passed_observerSeesRealResultAndPermissionRailDoesNotBlock() {
        AtomicInteger bashCalls = new AtomicInteger();
        List<String> observed = new ArrayList<>();
        ReActAgent agent = newAgent("multi-rail-passed", bashCalls, new AtomicInteger(),
                new AtomicInteger());
        ToolCallLoggerRail observer = new ToolCallLoggerRail(observed);
        agent.registerRail(observer);
        String sessionId = uniqueSessionId("multi-rail-passed");

        String output = runAgent(agent, "请用 CAT 查看主机配置", sessionId);

        assertThat(bashCalls.get()).isEqualTo(1);
        assertThat(output).contains("BASH_OK");
        assertThat(observed).anyMatch(entry -> entry.contains("BASH_OK"));
        assertThat(observed).noneMatch(entry -> entry.contains(DENIED_MARKER));
    }

    private String uniqueSessionId(String tag) {
        String sessionId = "guardrail-" + tag + "-" + UUID.randomUUID();
        sessionIds.add(sessionId);
        return sessionId;
    }

    private String runAgent(ReActAgent agent, String query, String sessionId) {
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

    private ReActAgent newAgent(String tag, AtomicInteger bashCalls, AtomicInteger readCalls,
            AtomicInteger writeCalls) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(tag).name(tag).description("guardrail runner e2e agent").build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(4)
                .promptTemplate(List.of(Map.of(
                        "role", "system",
                        "content", "你是一个测试助手，严格按照用户指令调用工具并总结结果。")))
                .build());
        agent.setLlm(newModel());
        LocalFunction bashTool = countedTool("bash_" + tag, "bash",
                "command", bashCalls, "BASH_OK");
        LocalFunction readTool = countedTool("read_file_" + tag, "read_file",
                "file_path", readCalls, "READ_OK");
        LocalFunction writeTool = countedTool("write_file_" + tag, "write_file",
                "file_path", writeCalls, "WRITE_OK");
        toolNames.add(bashTool.getCard().getId());
        toolNames.add(readTool.getCard().getId());
        toolNames.add(writeTool.getCard().getId());
        Runner.resourceMgr().addTool(bashTool, null);
        Runner.resourceMgr().addTool(readTool, null);
        Runner.resourceMgr().addTool(writeTool, null);
        agent.getAbilityManager().add(List.of(bashTool.getCard(), readTool.getCard(),
                writeTool.getCard()));
        agent.registerRail(PermissionFactory.buildPermissionInterruptRail(
                acceptancePermissions(), ToolPermissionHost.builder().build(),
                Path.of("/work")));
        return agent;
    }

    private static LocalFunction countedTool(String toolId, String toolName, String argKey,
            AtomicInteger counter, String resultPrefix) {
        ToolCard card = ToolCard.builder().id(toolId).name(toolName)
                .description("guardrail e2e tool " + toolName).build();
        return new LocalFunction(card, inputs -> {
            counter.incrementAndGet();
            return resultPrefix + ":" + String.valueOf(inputs.get(argKey));
        });
    }

    private static Model newModel() {
        ensureFactoryRegistered();
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientId("guardrail-runner-e2e").clientProvider(TEST_PROVIDER)
                .apiKey("test-key").apiBase("mirror://guardrail-runner-e2e").build();
        return new Model(clientConfig, ModelRequestConfig.builder()
                .modelName("guardrail-e2e-model").build());
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
                    return new GuardrailE2EModelClient(modelConfig, clientConfig);
                }
            });
        }
    }

    private static Map<String, Object> acceptancePermissions() {
        Map<String, Object> cfg = new java.util.LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("schema", "tiered_policy");
        cfg.put("permission_mode", "normal");
        cfg.put("tools", Map.of("bash", "ask"));
        cfg.put("defaults", Map.of("*", "allow"));
        cfg.put("rules", List.of(
                Map.of("id", "cat", "tools", List.of("bash"),
                        "pattern", "cat *", "action", "allow"),
                Map.of("id", "curl", "tools", List.of("bash"),
                        "pattern", "curl *", "action", "deny"),
                Map.of("id", "rm", "tools", List.of("bash"),
                        "pattern", "rm *", "action", "deny")));
        cfg.put("approval_overrides", List.of());
        Map<String, Object> fileGuard = new java.util.LinkedHashMap<>();
        fileGuard.put("enabled", true);
        fileGuard.put("defaults", Map.of("read", "allow", "write", "allow", "exec", "ask"));
        fileGuard.put("paths", List.of(Map.of(
                "path", "/etc/hosts", "read", "allow", "write", "deny", "exec", "deny",
                "match", "prefix")));
        cfg.put("file_guard", fileGuard);
        return cfg;
    }

    /**
     * Stateless scripted LLM that needs no API key or network. It inspects the last user
     * message and emits a single tool call selected by a keyword (CAT/CURL/RM/READ/WRITE);
     * once a tool result is present it returns a final text answer echoing that result.
     */
    private static final class GuardrailE2EModelClient extends BaseModelClient {
        private GuardrailE2EModelClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
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
            if (user.contains("CAT")) {
                return toolCall("bash", "{\"command\":\"cat /etc/hosts\"}");
            }
            if (user.contains("CURL")) {
                return toolCall("bash", "{\"command\":\"curl http://x\"}");
            }
            if (user.contains("RM")) {
                return toolCall("bash", "{\"command\":\"rm -rf /tmp/secret\"}");
            }
            if (user.contains("READ")) {
                return toolCall("read_file", "{\"file_path\":\"/etc/hosts\"}");
            }
            if (user.contains("WRITE")) {
                return toolCall("write_file", "{\"file_path\":\"/etc/hosts\",\"content\":\"x\"}");
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

    /**
     * Low-priority observer rail that records the tool result seen in {@code afterToolCall}. With
     * priority 5 it runs strictly after the {@link PermissionInterruptRail} (priority 90) in the
     * before-phase, so its after-phase observation reflects the permission decision: a synthesized
     * {@code [PERMISSION_DENIED]} result for rejected calls, or the real tool output for approved
     * calls. This mirrors the JiuwenTest {@code team_skills_081} observer assertions.
     */
    private static final class ToolCallLoggerRail extends AgentRail {
        private final List<String> sink;

        ToolCallLoggerRail(List<String> sink) {
            this.sink = sink;
        }

        @Override
        public int getPriority() {
            return 5;
        }

        @Override
        public void afterToolCall(AgentCallbackContext ctx) {
            if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                Object result = inputs.getToolResult();
                String toolName = inputs.getToolName() != null ? inputs.getToolName() : "";
                sink.add(toolName + "=" + (result != null ? String.valueOf(result) : "null"));
            }
        }
    }

    private record MessageView(String role, String content) {
    }
}
