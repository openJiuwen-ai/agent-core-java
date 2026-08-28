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
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
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
import com.openjiuwen.harness.rails.security.PermissionInterruptRail;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class ToolGuardrailE2E003Concurrency {

    private static final String TEST_PROVIDER = "GuardrailConcurrencyE2E";
    private static final String DENIED_MARKER = "[PERMISSION_DENIED]";
    private static final String BASH_PREFIX = "BASH_OK";
    private static final String REJECTED_FEEDBACK = "[ASK_REJECTED]";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    private final Set<String> toolNames = ConcurrentHashMap.newKeySet();
    private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();
    private final List<ExecutorService> executors = new ArrayList<>();

    ToolGuardrailE2E003Concurrency() {
        ensureFactoryRegistered();
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        Runner.setConfig(RunnerConfig.DEFAULT);
    }

    @AfterEach
    void cleanup() {
        for (ExecutorService executor : executors) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        executors.clear();
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
    @DisplayName("ST-C1 同会话连续问两条不同命令记忆不串台")
    void sameSession_twoDistinctCommands_memoryNoBleed() {
        AtomicInteger bashCalls = new AtomicInteger();
        AtomicInteger callbackCount = new AtomicInteger();
        LocalFunction bashTool = countedTool("bash_c1", bashCalls);
        Map<String, Object> permissions = buildPermissions(
                Map.of("bash", "ask"), Map.of("*", "allow"), List.of());
        ToolPermissionHost host = approveHost(true, false, callbackCount);
        ReActAgent agent = newAgent("c1", bashTool, permissions, host);
        String sessionId = uniqueSessionId("c1");

        runAgent(agent, "BASH:curl http://x", sessionId);
        runAgent(agent, "BASH:cat /etc/hosts", sessionId);

        assertThat(callbackCount.get())
                .as("两条不同命令各自独立确认，curl 的记忆不应自动放行 cat")
                .isEqualTo(2);
        assertThat(bashCalls.get()).as("两条命令均应执行").isEqualTo(2);
    }

    @Test
    @DisplayName("ST-C2 同会话连续问同一条命令记忆生效")
    void sameSession_repeatedCommand_autoConfirmHits() {
        AtomicInteger bashCalls = new AtomicInteger();
        AtomicInteger callbackCount = new AtomicInteger();
        LocalFunction bashTool = countedTool("bash_c2", bashCalls);
        Map<String, Object> permissions = buildPermissions(
                Map.of("bash", "ask"), Map.of("*", "allow"), List.of());
        ToolPermissionHost host = approveHost(true, false, callbackCount);
        ReActAgent agent = newAgent("c2", bashTool, permissions, host);
        String sessionId = uniqueSessionId("c2");

        runAgent(agent, "BASH:cat /etc/hosts", sessionId);
        String secondOutput = runAgent(agent, "BASH:cat /etc/hosts", sessionId);

        assertThat(callbackCount.get())
                .as("第二次同命令应命中 autoConfirm 记忆不再问")
                .isEqualTo(1);
        assertThat(bashCalls.get()).as("两次均应执行工具").isEqualTo(2);
        assertThat(secondOutput).contains(BASH_PREFIX);
    }

    @Test
    @DisplayName("ST-C3 多会话并发各自权限不同不串台")
    void concurrentSessions_distinctPermissions_noBleed() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        trackExecutor(executor);

        AtomicInteger aCalls = new AtomicInteger();
        AtomicInteger bCalls = new AtomicInteger();
        AtomicInteger bCallback = new AtomicInteger();
        AtomicInteger cCalls = new AtomicInteger();

        LocalFunction bashA = countedTool("bash_c3_a", aCalls);
        LocalFunction bashB = countedTool("bash_c3_b", bCalls);
        LocalFunction bashC = countedTool("bash_c3_c", cCalls);

        ReActAgent agentA = newAgent("c3-a", bashA, buildPermissions(
                Map.of("bash", "allow"), Map.of("*", "allow"),
                List.of(ruleMap("cat", "cat *", "allow"))), noCallbackHost());
        ReActAgent agentB = newAgent("c3-b", bashB, buildPermissions(
                Map.of("bash", "ask"), Map.of("*", "allow"), List.of()), rejectHost(bCallback));
        ReActAgent agentC = newAgent("c3-c", bashC, buildPermissions(
                Map.of("bash", "allow"), Map.of("*", "allow"),
                List.of(ruleMap("rm", "rm *", "deny"))), noCallbackHost());

        String sidA = uniqueSessionId("c3-a");
        String sidB = uniqueSessionId("c3-b");
        String sidC = uniqueSessionId("c3-c");

        CompletableFuture<String> fa = CompletableFuture.supplyAsync(
                () -> runAgent(agentA, "BASH:cat /etc/hosts", sidA), executor);
        CompletableFuture<String> fb = CompletableFuture.supplyAsync(
                () -> runAgent(agentB, "BASH:curl http://x", sidB), executor);
        CompletableFuture<String> fc = CompletableFuture.supplyAsync(
                () -> runAgent(agentC, "BASH:rm -rf /tmp/x", sidC), executor);

        CompletableFuture.allOf(fa, fb, fc).join();

        String outA = fa.join();
        String outC = fc.join();

        assertThat(aCalls.get()).as("A 应执行 cat").isEqualTo(1);
        assertThat(outA).contains(BASH_PREFIX);
        assertThat(bCalls.get()).as("B 的 curl 应被拒绝不执行").isZero();
        assertThat(bCallback.get()).as("B 应触发一次确认回调").isEqualTo(1);
        assertThat(cCalls.get()).as("C 的 rm 应被拒绝不执行").isZero();
        assertThat(outC).contains(DENIED_MARKER);
    }

    @Test
    @DisplayName("ST-C4 多租户并发上下文不串台")
    void concurrentTenants_contextIsolation_noBleed() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        trackExecutor(executor);

        AtomicInteger aCalls = new AtomicInteger();
        AtomicInteger bCalls = new AtomicInteger();

        LocalFunction bashTenantA = countedTool("bash_c4_a", aCalls);
        LocalFunction bashTenantB = countedTool("bash_c4_b", bCalls);

        ReActAgent agentA = newAgent("c4-a", bashTenantA, buildPermissions(
                Map.of("bash", "allow"), Map.of("*", "allow"), List.of()), noCallbackHost());
        ReActAgent agentB = newAgent("c4-b", bashTenantB, buildPermissions(
                Map.of("bash", "allow"), Map.of("*", "allow"),
                List.of(ruleMap("cat", "cat *", "deny"))), noCallbackHost());

        String sidA = uniqueSessionId("c4-a");
        String sidB = uniqueSessionId("c4-b");

        CompletableFuture<String> fa = CompletableFuture.supplyAsync(() -> {
            TenantContextHolder.setCurrentTenant(
                    TenantContext.builder().tenantId("tenant-a").build());
            try {
                return runAgent(agentA, "BASH:cat /etc/hosts", sidA);
            } finally {
                TenantContextHolder.clearCurrentTenant();
            }
        }, executor);
        CompletableFuture<String> fb = CompletableFuture.supplyAsync(() -> {
            TenantContextHolder.setCurrentTenant(
                    TenantContext.builder().tenantId("tenant-b").build());
            try {
                return runAgent(agentB, "BASH:cat /etc/hosts", sidB);
            } finally {
                TenantContextHolder.clearCurrentTenant();
            }
        }, executor);

        CompletableFuture.allOf(fa, fb).join();

        String outA = fa.join();
        String outB = fb.join();

        assertThat(aCalls.get()).as("租户 A 应执行 cat").isEqualTo(1);
        assertThat(outA).contains(BASH_PREFIX);
        assertThat(bCalls.get()).as("租户 B 的 cat 应被拒绝不执行").isZero();
        assertThat(outB).contains(DENIED_MARKER);
    }

    @Test
    @DisplayName("ST-C5 同会话多并发不同query第二个被拦截")
    void concurrentSameRail_allowAndDeny_noBleed() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        trackExecutor(executor);

        AtomicInteger catCalls = new AtomicInteger();
        AtomicInteger curlCalls = new AtomicInteger();

        LocalFunction catTool = countedTool("bash_c5_cat", catCalls);
        LocalFunction curlTool = countedTool("bash_c5_curl", curlCalls);

        Map<String, Object> permissions = buildPermissions(
                Map.of("bash", "allow"), Map.of("*", "allow"),
                List.of(ruleMap("curl", "curl *", "deny")));
        ReActAgent agent = newAgent("c5", catTool, permissions, noCallbackHost());

        String sidCat = uniqueSessionId("c5-cat");
        String sidCurl = uniqueSessionId("c5-curl");
        agent.getAbilityManager().registerSessionTool(sidCat, catTool);
        agent.getAbilityManager().registerSessionTool(sidCurl, curlTool);

        CompletableFuture<String> fCat = CompletableFuture.supplyAsync(
                () -> runAgent(agent, "BASH:cat /etc/hosts", sidCat), executor);
        CompletableFuture<String> fCurl = CompletableFuture.supplyAsync(
                () -> runAgent(agent, "BASH:curl http://x", sidCurl), executor);

        CompletableFuture.allOf(fCat, fCurl).join();

        String outCat = fCat.join();
        String outCurl = fCurl.join();

        assertThat(catCalls.get()).as("cat 应被放行执行").isEqualTo(1);
        assertThat(outCat).contains(BASH_PREFIX);
        assertThat(curlCalls.get()).as("curl 应被拦截不执行").isZero();
        assertThat(outCurl).contains(DENIED_MARKER);
    }

    private void trackExecutor(ExecutorService executor) {
        executors.add(executor);
    }

    private String uniqueSessionId(String tag) {
        String sessionId = "guardrail-c-" + tag + "-" + UUID.randomUUID();
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

    private ReActAgent newAgent(String tag, LocalFunction bashTool, Map<String, Object> permissions,
            ToolPermissionHost host) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(tag).name(tag).description("guardrail concurrency e2e").build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(4)
                .promptTemplate(List.of(Map.of(
                        "role", "system",
                        "content", "你是一个测试助手，严格按用户指令调用工具并总结结果。")))
                .build());
        agent.setLlm(newModel());
        toolNames.add(bashTool.getCard().getId());
        Runner.resourceMgr().addTool(bashTool, null);
        agent.getAbilityManager().add(List.of(bashTool.getCard()));
        agent.registerRail(PermissionFactory.buildPermissionInterruptRail(
                permissions, host, Path.of("/work")));
        return agent;
    }

    private static LocalFunction countedTool(String toolId, AtomicInteger counter) {
        ToolCard card = ToolCard.builder().id(toolId).name("bash")
                .description("guardrail concurrency e2e bash tool").build();
        return new LocalFunction(card, inputs -> {
            counter.incrementAndGet();
            String result = BASH_PREFIX + ":" + String.valueOf(inputs.get("command"));
            return Collections.singletonList(result).iterator();
        });
    }

    private static Map<String, Object> buildPermissions(Map<String, Object> tools,
            Map<String, Object> defaults, List<Map<String, Object>> rules) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("schema", "tiered_policy");
        cfg.put("permission_mode", "normal");
        cfg.put("tools", tools);
        cfg.put("defaults", defaults);
        cfg.put("rules", rules);
        cfg.put("approval_overrides", List.of());
        return cfg;
    }

    private static Map<String, Object> ruleMap(String id, String pattern, String action) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", id);
        rule.put("tools", List.of("bash"));
        rule.put("pattern", pattern);
        rule.put("action", action);
        return rule;
    }

    private static ToolPermissionHost approveHost(boolean autoConfirm, boolean persistAllow,
            AtomicInteger callbackCount) {
        ToolPermissionHost host = ToolPermissionHost.builder().build();
        host.setRequestPermissionConfirmationFn(request -> {
            callbackCount.incrementAndGet();
            return PermissionConfirmResponse.builder()
                    .approved(true)
                    .autoConfirm(autoConfirm)
                    .persistAllow(persistAllow)
                    .build();
        });
        return host;
    }

    private static ToolPermissionHost rejectHost(AtomicInteger callbackCount) {
        ToolPermissionHost host = ToolPermissionHost.builder().build();
        host.setRequestPermissionConfirmationFn(request -> {
            callbackCount.incrementAndGet();
            return PermissionConfirmResponse.builder()
                    .approved(false)
                    .feedback(REJECTED_FEEDBACK)
                    .build();
        });
        return host;
    }

    private static ToolPermissionHost noCallbackHost() {
        return ToolPermissionHost.builder().build();
    }

    private static Model newModel() {
        ensureFactoryRegistered();
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientId("guardrail-concurrency-e2e").clientProvider(TEST_PROVIDER)
                .apiKey("test-key").apiBase("mirror://guardrail-concurrency-e2e").build();
        return new Model(clientConfig, ModelRequestConfig.builder()
                .modelName("guardrail-concurrency-e2e-model").build());
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
                    return new GuardrailConcurrencyModelClient(modelConfig, clientConfig);
                }
            });
        }
    }

    private static final class GuardrailConcurrencyModelClient extends BaseModelClient {
        private GuardrailConcurrencyModelClient(ModelRequestConfig modelConfig,
                ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                String model, Integer maxTokens, String stop, BaseOutputParser outputParser,
                Float timeout, Map<String, Object> kwargs) {
            List<MessageView> views = toMessageViews(messages);
            if (views.isEmpty()) {
                return new AssistantMessage("FINAL:noop");
            }
            MessageView last = views.get(views.size() - 1);
            if ("tool".equals(last.role())) {
                return new AssistantMessage("FINAL:" + last.content());
            }
            String content = last.content();
            if (content != null && content.startsWith("BASH:")) {
                String command = content.substring("BASH:".length()).trim();
                return toolCall("bash", "{\"command\":\"" + command + "\"}");
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
