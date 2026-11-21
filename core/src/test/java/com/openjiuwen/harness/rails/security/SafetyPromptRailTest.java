/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.harness.rails.security;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Tag;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.rails.CallbackContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises safety injection at the actual model boundary, without a remote model. */
class SafetyPromptRailTest {
    @Test
    @DisplayName("首次及后续模型请求均包含一份安全段，并保留业务提示和用户消息")
    void callModel_whenCalledAgain_includesOneSafetySectionPerRequest() {
        // Given: an isolated agent with the safety rail and a recording model.
        try (Fixture f = new Fixture()) {
            f.agent.addPromptBuilderSection("business_rules", "fixture business rule", 90);
            // When: prepare and send two independent requests.
            f.call(false);
            f.call(false);

            // Then: verify safety injection without requiring identical request text.
            assertEquals(2, f.requests.size());
            assertTrue(f.requests.get(0).contains("fixture business rule"));
            for (String request : f.requests) {
                assertOneSafetySection(request);
                assertTrue(request.contains("System"));
                assertTrue(request.contains("hi"));
            }
        }
    }

    @Test
    @DisplayName("复用内部 ReActAgent 的两个会话，首轮请求均包含安全段")
    void invoke_whenAgentIsReused_includesSafetyInEachSession() {
        // Given: an isolated agent with the safety rail and a recording model.
        try (Fixture f = new Fixture()) {
            // When: execute through the agent entry point.
            f.agent.invoke(Map.of("query", "hi"), f.session());
            f.agent.invoke(Map.of("query", "hi"), f.session());

            // Then: each session's first model request includes safety.
            assertEquals(2, f.requests.size());
            f.requests.forEach(SafetyPromptRailTest::assertOneSafetySection);
        }
    }

    @Test
    @DisplayName("流式调用的首轮模型请求包含安全段")
    void callModel_whenStreaming_includesSafetyOnFirstRequest() {
        // Given: an isolated agent with the safety rail and a recording model.
        try (Fixture f = new Fixture()) {
            // When: execute through the agent entry point.
            f.call(true);

            // Then: safety reaches the streaming model boundary.
            assertOneSafetySection(f.requests.get(0));
        }
    }

    @Test
    @DisplayName("保持 builder 不变，切换语言后使用新语言的安全段")
    void callModel_whenLanguageChanges_replacesSafetyLanguage() {
        // Given: an isolated agent with the safety rail and a recording model.
        try (Fixture f = new Fixture()) {
            f.call(false);
            var builder = f.agent.getSystemPromptBuilder();

            // When: change language on the existing builder.
            builder.setLanguage("en");
            f.call(false);

            // Then: the request uses the new language without replacing the builder.
            assertSame(builder, f.agent.getSystemPromptBuilder());
            assertTrue(f.requests.get(1).contains("# Safety"));
            assertFalse(f.requests.get(1).contains("# 安全原则"));
        }
    }

    @Test
    @DisplayName("模型首次失败并触发真实重试流程，两次尝试均包含安全段")
    void callModel_whenFirstAttemptFails_includesSafetyOnRetry() {
        // Given: an isolated agent with the safety rail and a recording model.
        try (Fixture f = new Fixture()) {
            f.failFirst = true;
            f.agent.registerRail(new AgentRail() {
                @Override
                public void onModelException(AgentCallbackContext context) {
                    if (context.getRetryAttempt() == 0) context.requestRetry(0);
                }
            }).toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
            // When: execute the model request, including the configured retry.
            f.call(false);

            // Then: both attempts reach the model with safety instructions.
            assertEquals(2, f.requests.size());
            f.requests.forEach(SafetyPromptRailTest::assertOneSafetySection);
        }
    }

    @Test
    @DisplayName("前序 Rail 整条覆盖系统消息后，保留其结果，不强行重新注入安全段")
    void callModel_whenEarlierRailRewritesMessages_preservesEditsWithoutReinjection() {
        // Given: an isolated agent with the safety rail and a recording model.
        try (Fixture f = new Fixture()) {
            AgentRail edits = new AgentRail() {
                @Override
                public void beforeModelCall(AgentCallbackContext context) {
                    ModelCallInputs inputs = (ModelCallInputs) context.getInputs();
                    ((BaseMessage) inputs.getMessages().get(0)).setContent("projected system");
                    inputs.getMessages().add(new SystemMessage("keep directive"));
                }
            };
            edits.setPriority(100);
            f.agent.registerRail(edits).toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
            // When: the earlier rail runs after the request has been prepared.
            f.call(false);

            // Then: respect the explicit system-message replacement after preparation.
            assertTrue(f.requests.get(0).contains("projected system"));
            assertTrue(f.requests.get(0).contains("keep directive"));
            assertFalse(f.requests.get(0).contains("# 安全原则"));
        }
    }

    @Test
    @DisplayName("卸载安全 Rail 后，新请求不再包含此前注入的安全段")
    void unregisterRail_whenPreviouslyUsed_removesSafetyFromNewRequests() {
        // Given: an isolated agent with the safety rail and a recording model.
        try (Fixture f = new Fixture()) {
            f.call(false);

            // When: unregister the rail and prepare a new request.
            f.owner.unregisterRail(f.rail).orTimeout(5, TimeUnit.SECONDS).join();
            f.call(false);

            // Then: only the request sent before unregistering contains safety.
            assertTrue(f.requests.get(0).contains("# 安全原则"));
            assertFalse(f.requests.get(1).contains("# 安全原则"));
        }
    }

    @Test
    @DisplayName("初始系统提示词为空时，每次请求仍包含一份安全段")
    void callModel_whenSystemPromptIsEmpty_includesOneSafetySection() {
        // Given: an isolated agent with the safety rail and a recording model.
        try (Fixture f = new Fixture()) {
            ReActAgentConfig empty = new ReActAgentConfig();
            empty.setPromptTemplate(List.of());
            f.agent.configure(empty);
            f.rail.beforeInvoke(new CallbackContext(f.owner, Map.of()));
            // When: prepare and send two independent requests.
            f.call(false);
            f.call(false);

            // Then: verify safety injection without requiring identical request text.
            assertEquals(2, f.requests.size());
            for (String request : f.requests) {
                assertOneSafetySection(request);
                assertTrue(request.contains("hi"));
            }
        }
    }

    @Test
    @DisplayName("注册回调：保留已改写消息的内容、角色、元数据及合法工具描述")
    void beforeModelCall_whenRequestIsProjected_preservesMessageAttributesAndTools() {
        // Given: an isolated agent with the safety rail and a recording model.
        try (Fixture f = new Fixture()) {
            SystemMessage projected = new SystemMessage("projected system", "policy");
            projected.setMetadata(Map.of("source", "other-rail"));
            UserMessage user = new UserMessage("hi");
            ModelCallInputs inputs = ModelCallInputs.builder()
                    .messages(List.of(projected, user)).build();
            ToolInfo tool = ToolInfo.builder().name("query_balance")
                    .description("Query the current user's balance")
                    .parameters(Map.of("type", "object", "properties", Map.of())).build();
            inputs.setTools(List.of(tool));
            AgentCallbackContext callback = new AgentCallbackContext(f.agent);
            callback.setInputs(inputs);
            // When: dispatch the legacy handler through the registered callback.
            f.rail.getCallbacks().get(AgentCallbackEvent.BEFORE_MODEL_CALL)
                    .handle(callback).toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();

            // Then: inspect values, without requiring message identity or a fixed index.
            List<BaseMessage> messages = inputs.getMessages().stream()
                    .map(BaseMessage.class::cast).toList();
            List<BaseMessage> projectedMessages = messages.stream()
                    .filter(message -> "projected system".equals(message.getContentAsString())).toList();
            assertEquals(1, projectedMessages.size());
            BaseMessage actual = projectedMessages.get(0);
            assertEquals("system", actual.getRole());
            assertEquals("policy", actual.getName());
            assertEquals(Map.of("source", "other-rail"), actual.getMetadata());
            assertEquals(1, messages.stream().filter(message -> "user".equals(message.getRole())
                    && "hi".equals(message.getContentAsString())).count());
            assertEquals(List.of(tool), inputs.getTools());
            assertEquals(2, messages.size());
        }
    }

    @Test
    @DisplayName("范围保护：模型前回调修改 builder 不重新生成本轮消息")
    void callModel_whenEarlierRailOnlyUpdatesBuilder_keepsPreparedRequest() {
        // Given: an isolated agent with the safety rail and a recording model.
        try (Fixture f = new Fixture()) {
            AgentRail edits = new AgentRail() {
                @Override
                public void beforeModelCall(AgentCallbackContext context) {
                    // This is a patch-scope check, not a contract that all builder edits
                    // must be deferred by the framework.
                    f.agent.addPromptBuilderSection("compression", "deferred instruction", 90);
                    ((ModelCallInputs) context.getInputs()).getMessages().add(new SystemMessage("keep directive"));
                }
            };
            edits.setPriority(100);
            f.agent.registerRail(edits).toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
            // When: prepare the request before executing the two rails.
            f.call(false);

            // Then: the prepared safety prompt remains, without rendering later builder edits.
            assertTrue(f.requests.get(0).contains("# 安全原则"));
            assertTrue(f.requests.get(0).contains("keep directive"));
            assertFalse(f.requests.get(0).contains("deferred instruction"));
        }
    }

    @Test
    @DisplayName("前序回调选择的模型优先于旧参数，实际模型请求仍包含安全段")
    void callModel_whenEarlierRailSelectsModel_preservesSelectionAndInjectsSafety() {
        String firstId = "safety-first-" + UUID.randomUUID();
        String secondId = "safety-second-" + UUID.randomUUID();
        var resources = Runner.resourceMgr();
        String originalDefault = resources.getDefaultModelId();
        try (Fixture first = new Fixture(); Fixture second = new Fixture()) {
            resources.addModel(firstId, first.agent::getLlm, List.of(Tag.GLOBAL));
            resources.addModel(secondId, second.agent::getLlm, List.of(Tag.GLOBAL));
            AgentRail selection = new AgentRail() {
                @Override
                public void beforeModelCall(AgentCallbackContext context) {
                    context.getExtra().put("target_model_id", firstId);
                    context.setDynamicModelId(secondId);
                }
            };
            selection.setPriority(100);
            first.agent.registerRail(selection).toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();

            first.call(false);

            assertTrue(first.requests.isEmpty());
            assertEquals(1, second.requests.size());
            assertOneSafetySection(second.requests.get(0));
        } finally {
            for (String id : List.of(firstId, secondId)) {
                if (resources.listModelIds().contains(id)) {
                    resources.removeModelForce(id, true);
                }
            }
            resources.setDefaultModelId(originalDefault);
        }
    }

    @Test
    @DisplayName("旧回调通过原 forward 写回模型选择、工具和额外字段")
    void beforeModelCall_whenLegacyHookChangesState_preservesWriteback() {
        SafetyPromptRail custom = new SafetyPromptRail() {
            @Override
            public void beforeModelCall(CallbackContext context) {
                super.beforeModelCall(context);
                assertEquals("model-1", context.getDynamicModelId());
                PromptSection section = new PromptSection(SectionName.SAFETY,
                        Map.of("cn", "custom safety for " + context.getDynamicModelId(),
                                "en", "custom safety for " + context.getDynamicModelId()), 85);
                getSystemPromptBuilder().removeSection(SectionName.SAFETY);
                getSystemPromptBuilder().addSection(section);
                context.put("safety_section", section);
                context.setDynamicModelId("model-3");
                context.put("marker", "changed");
                context.put("tools", List.of());
            }
        };
        try (Fixture f = new Fixture(custom)) {
            AgentCallbackContext context = new AgentCallbackContext(f.agent);
            ModelCallInputs inputs = new ModelCallInputs();
            inputs.getMessages().add(new UserMessage("hi"));
            inputs.setTools(List.of(ToolInfo.builder().name("query_balance").build()));
            context.setInputs(inputs);
            context.setDynamicModelId("model-1");
            context.getExtra().put("target_model_id", "model-2");

            custom.getCallbacks().get(AgentCallbackEvent.BEFORE_MODEL_CALL)
                    .handle(context).toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();

            assertEquals("model-3", context.getDynamicModelId());
            assertEquals("changed", context.getExtra().get("marker"));
            assertTrue(inputs.getTools().isEmpty());
            assertEquals(1, inputs.getMessages().size());
        }
    }

    @Test
    @DisplayName("初始化即准备持久双语段，模型前检查不将其降为单语临时段")
    void init_preparesPersistentMultilingualSection() {
        try (Fixture f = new Fixture(new SecurityRail())) {
            var builder = f.agent.getSystemPromptBuilder();
            builder.clearTransient();
            assertOneSafetySection(builder.build());
            f.call(false);
            builder.clearTransient();
            builder.setLanguage("en");
            assertTrue(builder.build().contains("# Safety"));
            assertFalse(builder.build().contains("# 安全原则"));
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"cn", "ja", "", "EN"})
    @DisplayName("英文切换为中文或未知语言，本轮立即使用中文安全段")
    void callModel_whenLanguageFallsBack_usesChineseImmediately(String language) {
        try (Fixture f = new Fixture()) {
            f.agent.getSystemPromptBuilder().setLanguage("en");
            f.call(false);
            f.agent.getSystemPromptBuilder().setLanguage(language);
            f.call(false);
            assertTrue(f.requests.get(0).contains("# Safety"));
            assertOneSafetySection(f.requests.get(1));
            assertFalse(f.requests.get(1).contains("# Safety"));
        }
    }

    @Test
    @DisplayName("完整 DeepAgent 调用在重新配置前后首轮均包含安全段")
    void invoke_whenDeepAgentReconfigures_preparesFirstRequest() {
        try (Fixture f = new Fixture()) {
            f.owner.deepConfig().setTaskLoopEnabled(true);
            f.owner.deepConfig().setMaxIterations(1);
            try {
                f.owner.invoke(Map.of("query", "hi"), f.session());
                var oldBuilder = f.agent.getSystemPromptBuilder();
                f.agent.configure(config());
                var newBuilder = f.agent.getSystemPromptBuilder();
                assertNotSame(oldBuilder, newBuilder);

                f.owner.invoke(Map.of("query", "hi"), f.session());
                assertEquals(2, f.requests.size());
                f.requests.forEach(SafetyPromptRailTest::assertOneSafetySection);
                assertSame(newBuilder, f.rail.getSystemPromptBuilder());
                assertFalse(oldBuilder.hasSection(SectionName.SAFETY));
            } finally {
                f.owner.close();
            }
        }
    }

    @Test
    @DisplayName("事件关闭时不预置安全段，重新启用和关闭在外层准备阶段生效")
    void beforeInvoke_whenEventIsDisabled_removesOwnedSection() {
        SafetyPromptRail rail = new SafetyPromptRail();
        rail.setSupportedEvents(List.of());
        try (Fixture f = new Fixture(rail)) {
            f.call(false);
            assertFalse(f.requests.get(0).contains("# 安全原则"));
            rail.setSupportedEvents(List.of(BaseSecurityRail.BEFORE_MODEL_CALL));
            rail.beforeInvoke(new CallbackContext(f.owner, Map.of()));
            f.call(false);
            assertOneSafetySection(f.requests.get(1));
            rail.setSupportedEvents(List.of());
            rail.beforeInvoke(new CallbackContext(f.owner, Map.of()));
            f.call(false);
            assertFalse(f.requests.get(2).contains("# 安全原则"));
        }
    }

    @Test
    @DisplayName("外层准备不提前覆盖业务后来替换的安全段")
    void beforeInvoke_whenSectionWasReplaced_preservesReplacement() {
        try (Fixture f = new Fixture()) {
            PromptSection replacement = new PromptSection(SectionName.SAFETY,
                    Map.of("cn", "business policy"), 20);
            f.agent.getSystemPromptBuilder().addPersistentSection(replacement);
            f.rail.beforeInvoke(new CallbackContext(f.owner, Map.of()));
            assertSame(replacement, f.agent.getSystemPromptBuilder().getSection(SectionName.SAFETY).orElseThrow());

        }
    }

    @Test
    @DisplayName("真实工具往返前后的两次模型请求各包含一份安全段")
    void invoke_whenToolReturns_preservesOneSafetySectionPerRound() {
        AtomicInteger calls = new AtomicInteger();
        Tool tool = new Tool(ToolCard.builder().id(UUID.randomUUID().toString())
                .name("safety_test_lookup").description("Return a test value")
                .inputParams(Map.of("type", "object", "properties", Map.of())).build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                calls.incrementAndGet();
                return "lookup-result";
            }
        };
        var resources = Runner.resourceMgr();
        resources.addTool(tool);
        try (Fixture f = new Fixture()) {
            f.agent.getAbilityManager().add(tool.getCard());
            f.firstToolCall = ToolCall.builder().id("safety-test-call")
                    .name(tool.getCard().getName()).arguments("{}").build();
            f.agent.invoke(Map.of("query", "hi"), f.session());
            assertEquals(1, calls.get());
            assertEquals(2, f.requests.size());
            f.requests.forEach(SafetyPromptRailTest::assertOneSafetySection);
            assertTrue(f.requests.get(1).contains("lookup-result"));
        } finally {
            resources.removeTool(tool.getCard().getId());
        }
    }

    private static void assertOneSafetySection(String request) {
        assertEquals(1, request.split("# 安全原则", -1).length - 1,
                "Each request should contain exactly one safety section");
    }

    private static ReActAgentConfig config() {
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        return config;
    }

    private static final class Fixture implements AutoCloseable {
        final AgentCard card = new AgentCard(UUID.randomUUID().toString(), "safety-test", "test");
        final ReActAgent agent = new ReActAgent(card);
        final DeepAgent owner = new DeepAgent();
        final SafetyPromptRail rail;
        final List<String> requests = new ArrayList<>();
        boolean failFirst;
        ToolCall firstToolCall;

        Fixture() {
            this(new SafetyPromptRail());
        }

        Fixture(SafetyPromptRail rail) {
            this.rail = rail;
            agent.configure(config());
            agent.setLlm(new Model(new Model.ModelClient() {
                @Override
                public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
                    record(messages);
                    if (failFirst && requests.size() == 1) {
                        return CompletableFuture.failedFuture(new IllegalStateException("test retry"));
                    }
                    if (firstToolCall != null && requests.size() == 1) {
                        return CompletableFuture.completedFuture(AssistantMessage.builder()
                                .toolCalls(List.of(firstToolCall)).build());
                    }
                    return CompletableFuture.completedFuture(new AssistantMessage("ok"));
                }

                @Override
                public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
                    record(messages);
                    return List.of(AssistantMessageChunk.builder().content("ok").build()).iterator();
                }
            }));
            owner.setReactAgent(agent, true);
            owner.addRail(rail);
        }

        private void record(List<BaseMessage> messages) {
            requests.add(messages.stream().map(BaseMessage::getContentAsString)
                    .reduce("", (a, b) -> a + "\n" + b));
        }

        AgentSession session() {
            return new AgentSession(UUID.randomUUID().toString(), Map.of(), card);
        }

        void call(boolean streaming) {
            AgentSession session = session();
            var context = agent.initContext(session);
            context.addMessages(new UserMessage("hi")).toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
            AgentCallbackContext callback = new AgentCallbackContext(agent);
            callback.setSession(session);
            callback.setContext(context);
            callback.getExtra().put("_streaming", streaming);
            agent.callModel(callback, context, List.of());
        }

        @Override
        public void close() {
            owner.unregisterRail(rail).orTimeout(5, TimeUnit.SECONDS).join();
            agent.close();
        }
    }
}
