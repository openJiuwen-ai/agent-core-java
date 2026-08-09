package com.openjiuwen.core.singleagent.agents;

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
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.session.AgentSessionApi;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ReActAgentPromptBuilderTest {

    private static final String PROVIDER = "ReActAgentPromptBuilderTestProvider";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    @Test
    void configureSeedsIdentitySectionFromPromptTemplate() {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id("prompt-builder-agent")
                .name("prompt-builder-agent")
                .description("prompt builder agent")
                .build());

        agent.configure(ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", "base prompt")))
                .build());

        assertThat(agent.getPromptBuilder().hasSection("identity")).isTrue();
        assertThat(agent.getPromptBuilder().build()).isEqualTo("base prompt");
    }

    @Test
    void addPromptBuilderSectionSupportsOverwriteAndBlankRemoval() {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id("prompt-section-agent")
                .name("prompt-section-agent")
                .description("prompt section agent")
                .build());

        agent.addPromptBuilderSection("business_rules", "first rules", 20);
        agent.addPromptBuilderSection("business_rules", "updated rules", 30);

        assertThat(agent.getPromptBuilder().build()).isEqualTo("updated rules");

        agent.addPromptBuilderSection("business_rules", "  ", 30);
        assertThat(agent.getPromptBuilder().build()).isEmpty();
    }

    @Test
    void invokeUsesPromptBuilderSectionsInActualModelCall() {
        ensureFactoryRegistered();

        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id("prompt-invoke-agent")
                .name("prompt-invoke-agent")
                .description("prompt invoke agent")
                .build());
        agent.configure(ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", "base prompt")))
                .maxIterations(2)
                .build()
                .configureModelClient(PROVIDER, "key", "mirror://prompt-builder", "prompt-model", false));
        // invoke() clears transient sections; persist across invoke like Python's
        // add_section (Python has no clear_transient).
        agent.addPersistentPromptBuilderSection("business_rules", "business rules", 20);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) agent.invoke(
                Map.of("query", "hello", "conversation_id", "prompt-builder-session"),
                new com.openjiuwen.core.session.AgentGroupSession("prompt-builder-session")
        ).toCompletableFuture().join();

        assertThat(result.get("result_type")).isEqualTo("answer");
        assertThat(result.get("output")).isEqualTo("base prompt\n\nbusiness rules");
    }

    private static void ensureFactoryRegistered() {
        if (REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new Model.ModelClientFactory() {
                @Override
                public String providerName() {
                    return PROVIDER;
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return new BaseModelClient(modelConfig, clientConfig) {
                        @Override
                        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                                       Integer maxTokens, String stop, BaseOutputParser outputParser,
                                                       Float timeout, Map<String, Object> kwargs) {
                            @SuppressWarnings("unchecked")
                            List<BaseMessage> messageList = (List<BaseMessage>) messages;
                            String systemPrompt = "";
                            for (BaseMessage message : messageList) {
                                if ("system".equals(message.getRole())) {
                                    systemPrompt = message.getContentAsString();
                                    break;
                                }
                            }
                            return new AssistantMessage(systemPrompt);
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
                    };
                }
            });
        }
    }
}
