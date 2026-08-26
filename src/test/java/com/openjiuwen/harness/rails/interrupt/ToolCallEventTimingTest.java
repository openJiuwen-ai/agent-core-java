// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.harness.rails.interrupt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 回归测试 for gitcode issue #131：中断工具（ask_user）的 tool_call 事件应在中断前正常发射。
 * <p>
 * 排序约定为"higher value runs first"（降序）。ToolTrackingRail 优先级调高到 100，
 * 使其先于中断 rail（priority 90）执行，从而在中断前发射 tool_call 事件。
 * <p>
 * 本测试用 RecordingToolCallRail（priority=100，等价于 ToolTrackingRail 的 beforeToolCall）
 * 记录 beforeToolCall 是否被调用，从而确定性验证 tool_call 事件是否在中断前发射。
 */
class ToolCallEventTimingTest {
    private static final String TEST_PROVIDER = "Issue131Repro";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    private String currentAgentId;
    private String currentSessionId;

    ToolCallEventTimingTest() {
        ensureFactoryRegistered();
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        Runner.setConfig(RunnerConfig.DEFAULT);
    }

    @AfterEach
    void cleanup() {
        if (currentAgentId != null) {
            try {
                Runner.resourceMgr().removeTool("ask_user_tool", currentAgentId, TagMatchStrategy.ALL, true);
            } catch (RuntimeException ignored) {
            }
        }
        if (currentSessionId != null) {
            try {
                CheckpointerFactory.getCheckpointer().release(currentSessionId);
            } catch (RuntimeException ignored) {
            }
            try {
                Runner.release(currentSessionId);
            } catch (RuntimeException ignored) {
            }
        }
        try {
            Runner.stop();
        } catch (RuntimeException ignored) {
        }
        Runner.setConfig(RunnerConfig.DEFAULT);
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
    }

    @Test
    void interruptTool_toolCallEventEmittedBeforeInterrupt() {
        currentAgentId = "issue131-interrupt-agent";
        currentSessionId = "issue131-interrupt-session";
        ReActAgent agent = newAgent(currentAgentId);
        Tool askUserTool = createAskUserTool();
        Runner.resourceMgr().addTool(askUserTool, agent.getCard().getId());
        agent.getAbilityManager().add(askUserTool.getCard());

        RecordingToolCallRail recordingRail = new RecordingToolCallRail();
        agent.registerRail(recordingRail);
        agent.registerRail(new AskUserInterruptRail());

        List<Object> firstTurn = runStream(agent, currentSessionId);

        assertThat(recordingRail.beforeToolCallTools)
                .as("issue#131 回归：中断工具(ask_user)的 tool_call 事件应在中断前正常发射"
                        + "（priority=100 的发射 rail 先于 priority=90 的中断 rail 执行并发射 tool_call）")
                .contains("ask_user");

        OutputSchema interactionChunk = findInteractionChunk(firstTurn);
        assertNotNull(interactionChunk, "中断应发生并发射 __interaction__ 事件");
        InteractionOutput interactionOutput = assertInstanceOf(InteractionOutput.class, interactionChunk.getPayload());
        assertEquals("ask-user-call", interactionOutput.getId());
    }

    @Test
    void normalTool_toolCallEventEmittedBeforeExecution() {
        currentAgentId = "issue131-normal-agent";
        currentSessionId = "issue131-normal-session";
        ReActAgent agent = newAgent(currentAgentId);
        Tool askUserTool = createAskUserTool();
        Runner.resourceMgr().addTool(askUserTool, agent.getCard().getId());
        agent.getAbilityManager().add(askUserTool.getCard());

        RecordingToolCallRail recordingRail = new RecordingToolCallRail();
        agent.registerRail(recordingRail);
        agent.registerRail(new ConfirmInterruptRail());

        runStream(agent, currentSessionId);

        assertThat(recordingRail.beforeToolCallTools)
                .as("普通工具应在执行前发射 tool_call 事件")
                .contains("ask_user");
    }

    private ReActAgent newAgent(String agentId) {
        ReActAgent agent =
                new ReActAgent(AgentCard.builder().id(agentId).name(agentId).description("issue131 repro agent").build());

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content",
                        "You are a testing agent. Call ask_user once, then answer with the tool result.")))
                .maxIterations(4).build().configureModelClient(TEST_PROVIDER, "test-key", "mirror://issue131-repro",
                        "issue131-test-model", false);
        agent.configure(config);
        return agent;
    }

    private Tool createAskUserTool() {
        ToolCard card = ToolCard.builder().id("ask_user_tool").name("ask_user").description("collect user input")
                .inputParams(Map.of("type", "object", "properties",
                        Map.of("response", Map.of("type", "string", "description", "user response")), "required",
                        List.of("response")))
                .build();

        return new LocalFunction(card, (inputs, kwargs) -> {
            Session session = (Session) kwargs.get("session");
            String response = String.valueOf(inputs.get("response"));
            return "response=" + response + ",session=" + (session != null ? session.getSessionId() : "null");
        });
    }

    private List<Object> runStream(ReActAgent agent, String sessionId) {
        AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard());
        return collect(agent.stream(Map.of("query", "start interrupt flow", "conversation_id", sessionId), session,
                List.of(StreamMode.OUTPUT)));
    }

    private List<Object> collect(Iterator<Object> iterator) {
        List<Object> items = new ArrayList<Object>();
        iterator.forEachRemaining(items::add);
        return items;
    }

    private OutputSchema findInteractionChunk(List<Object> chunks) {
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema schema && "__interaction__".equals(schema.getType())) {
                return schema;
            }
        }
        return null;
    }

    private static void ensureFactoryRegistered() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new Issue131ModelFactory());
        }
    }

    private static final class RecordingToolCallRail extends AgentRail {
        final List<String> beforeToolCallTools = new ArrayList<String>();

        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public void beforeToolCall(AgentCallbackContext ctx) {
            if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                beforeToolCallTools.add(inputs.getToolName());
            }
        }
    }

    private static final class AskUserInterruptRail extends BaseInterruptRail {
        private AskUserInterruptRail() {
            super(List.of("ask_user"));
        }

        @Override
        public Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> getCallbacks() {
            Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks =
                    new EnumMap<>(AgentCallbackEvent.class);
            callbacks.put(AgentCallbackEvent.BEFORE_TOOL_CALL, this::beforeToolCall);
            return callbacks;
        }

        @Override
        protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
            if (userInput == null) {
                return interrupt(InterruptRequest.builder().interruptId(toolCall.getId())
                        .message("Please provide your name").context(Map.of("tool_call_id", toolCall.getId())).build());
            }
            String escaped = String.valueOf(userInput).replace("\\", "\\\\").replace("\"", "\\\"");
            return approve("{\"response\":\"" + escaped + "\"}");
        }
    }

    private static final class ConfirmInterruptRail extends BaseInterruptRail {
        private ConfirmInterruptRail() {
            super(List.of("confirm"));
        }

        @Override
        public Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> getCallbacks() {
            Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks =
                    new EnumMap<>(AgentCallbackEvent.class);
            callbacks.put(AgentCallbackEvent.BEFORE_TOOL_CALL, this::beforeToolCall);
            return callbacks;
        }

        @Override
        protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
            return approve();
        }
    }

    private static final class Issue131ModelFactory implements Model.ModelClientFactory {
        @Override
        public String providerName() {
            return TEST_PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            return new Issue131ModelClient(modelConfig, clientConfig);
        }
    }

    private static final class Issue131ModelClient extends BaseModelClient {
        private Issue131ModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            List<BaseMessage> messageList = toMessages(messages);
            String lastToolContent = findLastContent(messageList, "tool");
            if (lastToolContent == null) {
                return AssistantMessage.builder().content("").toolCalls(List.of(ToolCall.builder().id("ask-user-call")
                        .name("ask_user").arguments("{\"response\":\"Alice\"}").build())).build();
            }
            return new AssistantMessage("FINAL:" + lastToolContent);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            List<BaseMessage> messageList = toMessages(messages);
            String lastToolContent = findLastContent(messageList, "tool");
            if (lastToolContent == null) {
                String userContent = findLastContent(messageList, "user");
                return List.of(AssistantMessageChunk.builder().content("delta:" + userContent).build(),
                        AssistantMessageChunk.builder().content("")
                                .toolCalls(List.of(ToolCall.builder().id("ask-user-call").name("ask_user")
                                        .arguments("{\"response\":\"Alice\"}").build()))
                                .build())
                        .iterator();
            }
            return List.of(AssistantMessageChunk.builder().content("delta:" + lastToolContent).build(),
                    AssistantMessageChunk.builder().content("FINAL:" + lastToolContent).build()).iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(
                List<com.openjiuwen.core.foundation.llm.schema.UserMessage> messages, String model, String size,
                String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(
                List<com.openjiuwen.core.foundation.llm.schema.UserMessage> messages, String model, String voice,
                String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(
                List<com.openjiuwen.core.foundation.llm.schema.UserMessage> messages, String imgUrl, String audioUrl,
                String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark,
                String negativePrompt, Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        private List<BaseMessage> toMessages(Object messages) {
            List<BaseMessage> result = new ArrayList<BaseMessage>();
            if (messages instanceof List<?>) {
                List<?> list = (List<?>) messages;
                for (Object item : list) {
                    if (item instanceof BaseMessage) {
                        result.add((BaseMessage) item);
                    }
                }
            }
            return result;
        }

        private String findLastContent(List<BaseMessage> messages, String role) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                BaseMessage message = messages.get(i);
                if (role.equals(message.getRole())) {
                    return message.getContentAsString();
                }
            }
            return null;
        }
    }
}
