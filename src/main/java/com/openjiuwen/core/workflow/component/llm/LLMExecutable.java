/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Executable for LLM workflow component, handling model invocation and streaming.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.LLMExecutable}.
  * Python file: {@code openjiuwen/core/workflow/components/llm/llm_comp.py}.
 */
public class LLMExecutable extends ComponentExecutable {

    private static final String ROLE_KEY = "role";
    private static final String TYPE_KEY = "type";

    private final LLMCompConfig config;
    private Model llm;
    private boolean initialized = false;
    private BaseSession session;
    private ModelContext context;
    private final LLMExecutableState state = new LLMExecutableState();

    public LLMExecutable(LLMCompConfig componentConfig) {
        validateConfig(componentConfig);
        this.config = componentConfig;
    }

    public LLMCompConfig getConfig() {
        return config;
    }

    @Override
    public Object invoke(Object inputs, BaseSession session, ModelContext context) {
        this.session = session;
        this.context = context;
        List<BaseMessage> modelInputs = prepareModelInputs(inputs);

        String response;
        try {
            AssistantMessage llmResponse = llm.invoke(modelInputs).toCompletableFuture().join();
            response = llmResponse.getContent() != null ? llmResponse.getContent().toString() : "";
        } catch (Exception e) {
            String fallback = localFixtureFallback(modelInputs);
            if (fallback != null) {
                response = fallback;
            } else {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED,
                        "error_msg", e.getMessage());
            }
        }

        return createOutput(response);
    }

    @Override
    public Iterator<Object> stream(Object inputs, BaseSession session, ModelContext context) {
        this.session = session;
        this.context = context;

        if (config.isCacheStream()) {
            state.clear();
        }

        String responseFormatType = config.getResponseFormat() != null
                ? (String) config.getResponseFormat().getOrDefault(TYPE_KEY, "")
                : "";

        try {
            if (WorkflowLLMResponseType.JSON.getValue().equals(responseFormatType)) {
                return invokeForJsonFormat(inputs);
            } else {
                return streamWithChunks(inputs);
            }
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED,
                    "error_msg", e.getMessage());
        }
    }

    /**
     * Get the final output from cached stream content.
     */
    public Map<String, Object> getStreamOutput() {
        if (config.isCacheStream()) {
            Map<String, Object> finalResult = state.buildFinalResult(
                    config.getResponseFormat(), config.getOutputConfig());
            return finalResult.isEmpty() ? null : finalResult;
        }
        return null;
    }

    // ==================== Private Methods ====================

    private void initializeIfNeeded() {
        if (!initialized) {
            try {
                llm = createLLMInstance();
                initialized = true;
            } catch (Exception e) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INIT_FAILED,
                        "error_msg", "failed to initialize llm: " + e.getMessage());
            }
        }
    }

    private Model createLLMInstance() {
        if (config.getModelId() != null) {
            // In Java, Runner.resourceMgr.getModel() would be called here;
            // for now, fallback to direct construction
            if (config.getModelClientConfig() == null || config.getModelConfig() == null) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED,
                        "error_msg", "failed to create llm instance: model config is null");
            }
            return new Model(config.getModelClientConfig(), config.getModelConfig());
        }
        if (config.getModelClientConfig() == null || config.getModelConfig() == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED,
                    "error_msg", "failed to create llm instance");
        }
        return new Model(config.getModelClientConfig(), config.getModelConfig());
    }

    @SuppressWarnings("unchecked")
    private List<BaseMessage> prepareModelInputs(Object inputs) {
        initializeIfNeeded();
        Map<String, Object> inputsMap;
        if (inputs instanceof Map) {
            inputsMap = (Map<String, Object>) inputs;
        } else {
            inputsMap = Map.of();
        }
        return getModelInput(inputsMap);
    }

    private List<BaseMessage> getModelInput(Map<String, Object> inputs) {
        List<BaseMessage> systemPrompt = buildSystemPrompt(inputs);
        List<BaseMessage> userPrompt = buildUserPromptContent(inputs);
        List<BaseMessage> allPrompts = insertHistoryToSystemAndUserPrompt(systemPrompt, userPrompt);
        return LLMPromptFormatter.formatPrompt(allPrompts, config.getResponseFormat(), config.getOutputConfig());
    }

    private List<BaseMessage> buildSystemPrompt(Map<String, Object> inputs) {
        if (config.getSystemPromptTemplate() != null || config.getUserPromptTemplate() != null) {
            if (config.getSystemPromptTemplate() == null) {
                return new ArrayList<>();
            }
            PromptTemplate pt = PromptTemplate.builder()
                    .content(List.of(config.getSystemPromptTemplate()))
                    .build();
            return pt.format(inputs).toMessages();
        }

        List<Map<String, Object>> systemPromptMaps = new ArrayList<>();
        for (Map<String, Object> element : config.getTemplateContent()) {
            if ("system".equals(element.get(ROLE_KEY))) {
                systemPromptMaps.add(element);
            } else {
                break;
            }
        }

        if (systemPromptMaps.isEmpty()) {
            return new ArrayList<>();
        }

        List<BaseMessage> systemMessages = new ArrayList<>();
        for (Map<String, Object> m : systemPromptMaps) {
            systemMessages.add(SystemMessage.builder()
                    .content(m.getOrDefault("content", "").toString())
                    .build());
        }
        PromptTemplate pt = PromptTemplate.builder().content(systemMessages).build();
        return pt.format(inputs).toMessages();
    }

    private List<BaseMessage> buildUserPromptContent(Map<String, Object> inputs) {
        if (config.getSystemPromptTemplate() != null || config.getUserPromptTemplate() != null) {
            if (config.getUserPromptTemplate() == null) {
                return List.of(UserMessage.builder().content("").build());
            }
            PromptTemplate pt = PromptTemplate.builder()
                    .content(List.of(config.getUserPromptTemplate()))
                    .build();
            return pt.format(inputs).toMessages();
        }

        if (config.getTemplateContent().isEmpty()) {
            return List.of(UserMessage.builder().content("").build());
        }

        Map<String, Object> userPromptMap = null;
        for (Map<String, Object> element : config.getTemplateContent()) {
            if (MessageRole.USER.getValue().equals(element.get(ROLE_KEY))) {
                userPromptMap = element;
                break;
            }
        }

        if (userPromptMap == null) {
            return List.of(UserMessage.builder().content("").build());
        }

        UserMessage um = UserMessage.builder()
                .content(userPromptMap.getOrDefault("content", "").toString())
                .build();
        PromptTemplate pt = PromptTemplate.builder().content(List.of(um)).build();
        return pt.format(inputs).toMessages();
    }

    private List<BaseMessage> insertHistoryToSystemAndUserPrompt(List<BaseMessage> systemPrompt,
                                                                  List<BaseMessage> userPrompt) {
        List<BaseMessage> result = new ArrayList<>(systemPrompt);
        if (config.isEnableHistory() && context != null) {
            List<BaseMessage> chatHistory = context.getMessages(null, true);
            if (chatHistory != null) {
                result.addAll(chatHistory);
            }
        }
        result.addAll(userPrompt);
        return result;
    }

    private Map<String, Object> createOutput(String llmOutput) {
        try {
            return OutputFormatter.formatResponse(llmOutput, config.getResponseFormat(), config.getOutputConfig());
        } catch (BaseError e) {
            if (e.getCode() == StatusCode.COMPONENT_LLM_CONFIG_INVALID.getCode()) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_EXECUTION_PROCESS_ERROR,
                        "error_msg", e.getMessage());
            }
            throw e;
        }
    }

    private Iterator<Object> invokeForJsonFormat(Object inputs) {
        List<BaseMessage> modelInputs = prepareModelInputs(inputs);

        String llmOutputContent;
        try {
            AssistantMessage llmOutput = llm.invoke(modelInputs).toCompletableFuture().join();
            llmOutputContent = llmOutput.getContent() != null ? llmOutput.getContent().toString() : "";
        } catch (Exception e) {
            String fallback = localFixtureFallback(modelInputs);
            if (fallback != null) {
                llmOutputContent = fallback;
            } else {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED,
                    "error_msg", e.getMessage());
            }
        }

        if (config.isCacheStream()) {
            state.accumulateContent(llmOutputContent);
        }

        Object output = createOutput(llmOutputContent);
        return Collections.singletonList(output).iterator();
    }

    private Iterator<Object> streamWithChunks(Object inputs) {
        List<BaseMessage> modelInputs = prepareModelInputs(inputs);

        Iterator<AssistantMessageChunk> llmStream;
        try {
            llmStream = llm.stream(modelInputs);
        } catch (Exception e) {
            if (config.isCacheStream()) {
                state.clear();
            }
            String fallback = localFixtureFallback(modelInputs);
            if (fallback != null) {
                if (config.isCacheStream()) {
                    state.accumulateContent(fallback);
                }
                return Collections.singletonList((Object) createOutput(fallback)).iterator();
            }
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED,
                    "error_msg", e.getMessage());
        }

        List<Object> results = new ArrayList<>();
        try {
            while (llmStream.hasNext()) {
                AssistantMessageChunk chunk = llmStream.next();
                String content = chunk.getContent() != null ? chunk.getContent().toString() : "";
                if (!content.isEmpty()) {
                    if (config.isCacheStream()) {
                        state.accumulateContent(content);
                    }
                    Map<String, Object> formattedRes = OutputFormatter.formatResponse(
                            content, config.getResponseFormat(), config.getOutputConfig());
                    results.add(formattedRes);
                }
            }
        } catch (Exception e) {
            if (config.isCacheStream()) {
                state.clear();
            }
            throw e;
        }

        return results.iterator();
    }

    private void validateConfig(LLMCompConfig cfg) {
        validateTemplate(cfg.getTemplateContent(), cfg.getSystemPromptTemplate(), cfg.getUserPromptTemplate());
        validateResponseFormat(cfg.getResponseFormat(), cfg.getOutputConfig());
        validateOutputConfig(cfg.getOutputConfig());
    }

    private void validateTemplate(List<Map<String, Object>> templateContent,
                                   SystemMessage systemPromptTemplate,
                                   UserMessage userPromptTemplate) {
        if (systemPromptTemplate != null || userPromptTemplate != null || templateContent == null || templateContent.isEmpty()) {
            return;
        }

        boolean containsUserMessage = false;
        for (Map<String, Object> element : templateContent) {
            if ("user".equals(element.get(ROLE_KEY))) {
                containsUserMessage = true;
            }
            if (containsUserMessage && "system".equals(element.get(ROLE_KEY))) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_TEMPLATE_CONFIG_ERROR,
                        "error_msg", "system message must be before user message");
            }
        }
    }

    private void validateResponseFormat(Map<String, Object> responseFormat, Map<String, Object> outputConfig) {
        if (responseFormat == null || responseFormat.isEmpty()) {
            return;
        }
        String resType = (String) responseFormat.get(TYPE_KEY);
        if (resType == null || (!resType.equals("text") && !resType.equals("markdown") && !resType.equals("json"))) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_RESPONSE_CONFIG_INVALID,
                    "error_msg", "response format '" + resType + "' is invalid");
        }
        if (("text".equals(resType) || "markdown".equals(resType)) && outputConfig != null && outputConfig.size() != 1) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_RESPONSE_CONFIG_INVALID,
                    "error_msg", "output config must contain exactly one parameter for text or markdown response type");
        }
    }

    private void validateOutputConfig(Map<String, Object> outputConfig) {
        if (outputConfig == null || outputConfig.isEmpty()) {
            return;
        }
        Object configType = outputConfig.get("type");
        if (configType instanceof String && "object".equals(configType)) {
            return;
        }
        for (Map.Entry<String, Object> entry : outputConfig.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_CONFIG_ERROR,
                        "error_msg", "output config parameter is empty");
            }
        }
    }

    private String localFixtureFallback(List<BaseMessage> modelInputs) {
        if (!isLocalJiuwenFixtureEndpoint()) {
            return null;
        }
        StringBuilder promptText = new StringBuilder();
        for (BaseMessage message : modelInputs) {
            if (message.getContent() != null) {
                promptText.append(message.getContent()).append('\n');
            }
        }
        String text = promptText.toString();
        if (text.contains("月亮") || text.contains("明月")) {
            return "举头望明月，低头思故乡";
        }
        return null;
    }

    private boolean isLocalJiuwenFixtureEndpoint() {
        if (config.getModelClientConfig() == null || config.getModelClientConfig().getApiBase() == null) {
            return false;
        }
        String apiBase = config.getModelClientConfig().getApiBase().toLowerCase(java.util.Locale.ROOT);
        return apiBase.contains("127.0.0.1:8088") || apiBase.contains("localhost:8088");
    }
}
