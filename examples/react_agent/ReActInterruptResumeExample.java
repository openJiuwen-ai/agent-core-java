/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.react_agent;

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
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
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
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.interrupt.BaseInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal example for single-agent tool interruption / resume.
 */
public final class ReActInterruptResumeExample {

    private static final String TEST_PROVIDER = "ReActInterruptResumeExampleProvider";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    private ReActInterruptResumeExample() {
    }

    public static void main(String[] args) {
        ensureFactoryRegistered();
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        Runner.setConfig(RunnerConfig.DEFAULT);

        ReActAgent agent = buildAgent();
        Tool askUserTool = createAskUserTool();
        Runner.resourceMgr().addTool(askUserTool, agent.getCard().getId());
        agent.getAbilityManager().add(askUserTool.getCard());
        agent.registerRail(new AskUserInterruptRail());

        try {
            String sessionId = "interrupt-example-session";

            List<Object> firstTurn = collect(agent.stream(
                    Map.of("query", "start", "conversation_id", sessionId),
                    AgentSession.createAgentSession(sessionId, null, agent.getCard()),
                    List.of(StreamMode.OUTPUT)
            ));

            OutputSchema interactionChunk = findInteractionChunk(firstTurn);
            if (interactionChunk == null) {
                throw new IllegalStateException("No interaction chunk returned");
            }
            InteractionOutput interactionOutput = (InteractionOutput) interactionChunk.getPayload();

            System.out.println("First turn interaction id: " + interactionOutput.getId());
            System.out.println("First turn payload: " + interactionOutput.getValue());

            InteractiveInput resumeInput = new InteractiveInput();
            resumeInput.update(interactionOutput.getId(), "Alice");

            AgentSessionApi resumedSession = AgentSession.createAgentSession(sessionId, null, agent.getCard());
            List<Object> secondTurn = collect(agent.stream(
                    Map.of("query", resumeInput, "conversation_id", sessionId),
                    resumedSession,
                    List.of(StreamMode.OUTPUT)
            ));

            System.out.println("Second turn final output: " + extractFinalOutput(secondTurn));
            System.out.println("Tool saw session: " + resumedSession.getState("tool_saw_session"));
            System.out.println("Tool session id: " + resumedSession.getState("tool_session_id"));
        } finally {
            Runner.resourceMgr().removeTool("ask_user_tool_example", agent.getCard().getId(), TagMatchStrategy.ALL, true);
            CheckpointerFactory.getCheckpointer().release("interrupt-example-session");
            Runner.release("interrupt-example-session");
            Runner.stop();
        }
    }

    private static ReActAgent buildAgent() {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id("interrupt-resume-example-agent")
                .name("interrupt-resume-example-agent")
                .description("interrupt resume example")
                .build());

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of(
                        "role", "system",
                        "content", "Call ask_user first. After the tool returns, answer with the tool result."
                )))
                .maxIterations(4)
                .build()
                .configureModelClient(
                        TEST_PROVIDER,
                        "example-key",
                        "mirror://interrupt-example",
                        "interrupt-example-model",
                        false
                );
        agent.configure(config);
        return agent;
    }

    private static Tool createAskUserTool() {
        ToolCard card = ToolCard.builder()
                .id("ask_user_tool_example")
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

        return new LocalFunction(card, (inputs, kwargs) -> {
            AgentSessionApi session = kwargs.get("session") instanceof AgentSessionApi typed ? typed : null;
            if (session != null) {
                session.updateState(Map.of(
                        "tool_saw_session", Boolean.TRUE,
                        "tool_session_id", session.getSessionId()
                ));
            }
            return "response=" + inputs.get("response") + ",session="
                    + (session != null ? session.getSessionId() : "null");
        });
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> items = new ArrayList<Object>();
        iterator.forEachRemaining(items::add);
        return items;
    }

    private static OutputSchema findInteractionChunk(List<Object> chunks) {
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
    private static String extractFinalOutput(List<Object> chunks) {
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
            Model.registerFactory(new ExampleModelFactory());
        }
    }

    private static final class AskUserInterruptRail extends BaseInterruptRail {
        private AskUserInterruptRail() {
            super(List.of("ask_user"));
        }

        @Override
        protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
            if (userInput == null) {
                return interrupt(InterruptRequest.builder()
                        .interruptId(toolCall.getId())
                        .message("Please provide your name")
                        .context(Map.of("tool_call_id", toolCall.getId()))
                        .build());
            }
            String escaped = String.valueOf(userInput).replace("\\", "\\\\").replace("\"", "\\\"");
            return approve("{\"response\":\"" + escaped + "\"}");
        }
    }

    private static final class ExampleModelFactory implements Model.ModelClientFactory {
        @Override
        public String providerName() {
            return TEST_PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            return new ExampleModelClient(modelConfig, clientConfig);
        }
    }

    private static final class ExampleModelClient extends BaseModelClient {

        private ExampleModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser,
                                       Float timeout, Map<String, Object> kwargs) {
            String lastToolContent = findLastToolContent(messages);
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
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                     String negativePrompt, int n, boolean promptExtend,
                                                     boolean watermark, int seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark, String negativePrompt,
                                                     Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        private String findLastToolContent(Object messages) {
            if (messages instanceof List<?>) {
                List<?> list = (List<?>) messages;
                for (int i = list.size() - 1; i >= 0; i--) {
                    Object item = list.get(i);
                    if (item instanceof BaseMessage) {
                        BaseMessage message = (BaseMessage) item;
                        if ("tool".equals(message.getRole())) {
                            return message.getContentAsString();
                        }
                    }
                }
            }
            return null;
        }
    }
}
