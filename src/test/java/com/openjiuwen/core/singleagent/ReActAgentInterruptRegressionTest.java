package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
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
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ReActAgentInterruptRegressionTest {

    private static final String TEST_PROVIDER = "SingleAgentInterruptRegression";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    ReActAgentInterruptRegressionTest() {
        ensureFactoryRegistered();
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        Runner.setConfig(RunnerConfig.DEFAULT);
    }

    @AfterEach
    void cleanup() {
        Runner.resourceMgr().removeTool("ask_user_tool", "interrupt-regression-agent", TagMatchStrategy.ALL, true);
        CheckpointerFactory.getCheckpointer().release("interrupt-session");
        Runner.release("interrupt-session");
        Runner.stop();
        Runner.setConfig(RunnerConfig.DEFAULT);
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
    }

    @Test
    void railLifecycleHooksAreCalled() {
        ReActAgent agent = newAgent("lifecycle-agent");
        TrackingRail rail = new TrackingRail();

        agent.registerRail(rail);
        assertTrue(rail.initCalled.get(), "rail init should be called on registration");

        agent.unregisterRail(rail);
        assertTrue(rail.uninitCalled.get(), "rail uninit should be called on unregistration");
    }

    @Test
    void beforeInvokeForceFinishSkipsAgentLoop() {
        ReActAgent agent = newAgent("force-finish-agent");
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> beforeInvoke(AgentCallbackContext ctx) {
                ctx.requestForceFinish(Map.of(
                        "output", "FORCE_FINISHED",
                        "result_type", "answer"
                ));
                return completed();
            }
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) agent.invoke(
                Map.of("query", "ignored", "conversation_id", "force-finish-session"),
                new AgentSession("force-finish-session", null, agent.getCard())
        ).toCompletableFuture().join();

        assertEquals("FORCE_FINISHED", result.get("output"));
        assertEquals("answer", result.get("result_type"));
    }

    @Test
    void askUserInterruptResumesAndToolReceivesSession() {
        ReActAgent agent = newAgent("interrupt-regression-agent");
        Tool askUserTool = createAskUserTool();
        Runner.resourceMgr().addTool(askUserTool, agent.getCard().getId());
        agent.getAbilityManager().add(askUserTool.getCard());
        agent.registerRail(new AskUserInterruptRail());

        List<Object> firstTurn = runStream(agent, Map.of(
                "query", "start interrupt flow",
                "conversation_id", "interrupt-session"
        ));

        OutputSchema interactionChunk = findInteractionChunk(firstTurn);
        assertNotNull(interactionChunk, "first turn should emit an interaction chunk");
        InteractionOutput interactionOutput = assertInstanceOf(InteractionOutput.class, interactionChunk.getPayload());
        assertEquals("ask-user-call", interactionOutput.getId());

        InteractiveInput resumeInput = new InteractiveInput();
        resumeInput.update("ask-user-call", "Alice");

        AgentSessionApi resumedSession = new AgentSession("interrupt-session", null, agent.getCard());
        List<Object> secondTurn = collect(agent.stream(
                Map.of("query", resumeInput, "conversation_id", "interrupt-session"),
                resumedSession,
                List.of(StreamMode.OUTPUT)
        ));

        String finalOutput = extractFinalOutput(secondTurn);
        assertTrue(finalOutput.contains("Alice"), "final answer should contain resumed user input");
    }

    @Test
    void streamShouldYieldAssistantDeltaBeforeFinalAnswer() {
        ReActAgent agent = newAgent("streaming-regression-agent");
        Model model = Mockito.mock(Model.class);
        try {
            when(model.stream(any(List.class), any(ModelInvokeOptions.class)))
                    .thenAnswer(invocation -> List.of(
                            AssistantMessageChunk.builder().content("delta:stream progressively").build(),
                            AssistantMessageChunk.builder().content("FINAL:stream progressively").build()
                    ).iterator());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        agent.setLlm(model);
        AgentSessionApi session = new AgentSession("streaming-session", null, agent.getCard());

        Iterator<Object> iterator = agent.stream(
                Map.of("query", "stream progressively", "conversation_id", "streaming-session"),
                session,
                List.of(StreamMode.OUTPUT)
        );
        List<Object> firstChunks = takeChunks(iterator, 2);

        assertThat(firstChunks).isNotEmpty();
        assertThat(firstChunks.get(0)).isInstanceOf(OutputSchema.class);
        OutputSchema firstChunk = (OutputSchema) firstChunks.get(0);
        assertThat(firstChunk.getType()).isEqualTo("answer");
        assertThat(firstChunk.getPayload()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstPayload = (Map<String, Object>) firstChunk.getPayload();
        assertThat(firstPayload).containsEntry("delta", "delta:stream progressively");

        List<Object> allChunks = new ArrayList<Object>(firstChunks);
        iterator.forEachRemaining(allChunks::add);
        String finalOutput = extractFinalOutput(allChunks);
        assertThat(finalOutput).contains("FINAL:stream progressively");
    }

    private ReActAgent newAgent(String agentId) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(agentId)
                .name(agentId)
                .description("interrupt regression agent")
                .build());

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of(
                        "role", "system",
                        "content", "You are a testing agent. Call ask_user once, then answer with the tool result."
                )))
                .maxIterations(4)
                .build()
                .configureModelClient(
                        TEST_PROVIDER,
                        "test-key",
                        "mirror://single-agent-interrupt",
                        "interrupt-test-model",
                        false
                );
        agent.configure(config);
        return agent;
    }

    private Tool createAskUserTool() {
        ToolCard card = ToolCard.builder()
                .id("ask_user_tool")
                .name("ask_user")
                .description("collect user input")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "response", Map.of("type", "string", "description", "user response")
                        ),
                        "required", List.of("response")
                ))
                .build();

        return new LocalFunction(card, (inputs) -> {
            String response = String.valueOf(inputs.get("response"));
            return "response=" + response;
        });
    }

    private List<Object> runStream(ReActAgent agent, Map<String, Object> inputs) {
        AgentSessionApi session = new AgentSession("interrupt-session", null, agent.getCard());
        return collect(agent.stream(inputs, session, List.of(StreamMode.OUTPUT)));
    }

    private List<Object> collect(Iterator<Object> iterator) {
        List<Object> items = new ArrayList<Object>();
        iterator.forEachRemaining(items::add);
        return items;
    }

    private List<Object> takeChunks(Iterator<Object> iterator, int maxItems) {
        List<Object> items = new ArrayList<Object>();
        for (int i = 0; i < maxItems && iterator.hasNext(); i++) {
            items.add(iterator.next());
        }
        return items;
    }

    private OutputSchema findInteractionChunk(List<Object> chunks) {
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema) {
                OutputSchema schema = (OutputSchema) chunk;
                if ("__interaction__".equals(schema.getType())) {
                    return schema;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractFinalOutput(List<Object> chunks) {
        for (int i = chunks.size() - 1; i >= 0; i--) {
            Object chunk = chunks.get(i);
            if (chunk instanceof OutputSchema) {
                OutputSchema schema = (OutputSchema) chunk;
                if (schema.getPayload() instanceof Map<?, ?>) {
                    Map<String, Object> payload = (Map<String, Object>) schema.getPayload();
                    Object outerOutput = payload.get("output");
                    if (outerOutput instanceof Map<?, ?>) {
                        return String.valueOf(((Map<?, ?>) outerOutput).get("output"));
                    }
                }
            }
        }
        return "";
    }

    private static void ensureFactoryRegistered() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new InterruptTestModelFactory());
        }
    }

    private static final class TrackingRail extends AgentRail {
        private final AtomicBoolean initCalled = new AtomicBoolean(false);
        private final AtomicBoolean uninitCalled = new AtomicBoolean(false);

        @Override
        public void init(BaseAgent agent) {
            initCalled.set(true);
        }

        @Override
        public void uninit(BaseAgent agent) {
            uninitCalled.set(true);
        }
    }

    private static final class AskUserInterruptRail extends AgentRail {
        private AskUserInterruptRail() {
        }

        @Override
        public CompletionStage<Void> beforeToolCall(AgentCallbackContext ctx) {
            Object rawToolCall = ctx.getExtra().get("tool_call");
            if (rawToolCall instanceof ToolCall toolCall) {
                Object userInput = ctx.getExtra().get("user_input");
                if (userInput == null) {
                    ctx.requestForceFinish(Map.of(
                            "output", "INTERRUPT:" + toolCall.getId(),
                            "result_type", "interaction",
                            "interaction_id", toolCall.getId(),
                            "message", "Please provide your name"
                    ));
                } else {
                    String escaped = String.valueOf(userInput).replace("\\", "\\\\").replace("\"", "\\\"");
                    ctx.getExtra().put("approved_args", "{\"response\":\"" + escaped + "\"}");
                }
            }
            return completed();
        }
    }

    private static final class InterruptTestModelFactory implements Model.ModelClientFactory {
        @Override
        public String providerName() {
            return TEST_PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            return new InterruptTestModelClient(modelConfig, clientConfig);
        }
    }

    private static final class InterruptTestModelClient extends BaseModelClient {

        private InterruptTestModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser,
                                       Float timeout, Map<String, Object> kwargs) {
            List<BaseMessage> messageList = toMessages(messages);
            String lastToolContent = findLastContent(messageList, "tool");
            if (lastToolContent == null) {
                return AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("ask-user-call")
                                .name("ask_user")
                                .arguments("{\"question\":\"Please provide your name\"}")
                                .build()))
                        .build();
            }
            return new AssistantMessage("FINAL:" + lastToolContent);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            List<BaseMessage> messageList = toMessages(messages);
            String lastToolContent = findLastContent(messageList, "tool");
            if (lastToolContent == null) {
                String userContent = findLastContent(messageList, "user");
                return List.of(
                        AssistantMessageChunk.builder()
                                .content("delta:" + userContent)
                                .build(),
                        AssistantMessageChunk.builder()
                                .content("")
                                .toolCalls(List.of(ToolCall.builder()
                                        .id("ask-user-call")
                                        .name("ask_user")
                                        .arguments("{\"question\":\"Please provide your name\"}")
                                        .build()))
                                .build()
                ).iterator();
            }
            return List.of(
                    AssistantMessageChunk.builder()
                            .content("delta:" + lastToolContent)
                            .build(),
                    AssistantMessageChunk.builder()
                            .content("FINAL:" + lastToolContent)
                            .build()
            ).iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<com.openjiuwen.core.foundation.llm.schema.UserMessage> messages,
                                                     String model, String size, String negativePrompt, int n,
                                                     boolean promptExtend, boolean watermark, int seed,
                                                     Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<com.openjiuwen.core.foundation.llm.schema.UserMessage> messages,
                                                      String model, String voice, String languageType,
                                                      Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(List<com.openjiuwen.core.foundation.llm.schema.UserMessage> messages,
                                                     String imgUrl, String audioUrl, String model, String size,
                                                     String resolution, int duration, boolean promptExtend,
                                                     boolean watermark, String negativePrompt, Integer seed,
                                                     Map<String, Object> kwargs) {
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
