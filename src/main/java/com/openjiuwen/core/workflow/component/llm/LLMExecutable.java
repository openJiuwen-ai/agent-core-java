/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.session.NodeSessionApi;
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
 * 
 * @since 0.1.7
 */
public class LLMExecutable extends ComponentExecutable {
    private static final String ROLE_KEY = "role";
    private static final String TYPE_KEY = "type";

    private final LLMCompConfig config;
    private Model llm;
    private boolean initialized = false;
    private NodeSessionApi session;
    private ModelContext context;

    /**
     * LLMExecutableState.
     * 
     * @since 0.1.7
     */
    private final LLMExecutableState state = new LLMExecutableState();

    /**
     * LLMExecutable.
     * 
     * @param componentConfig componentConfig
     * @since 0.1.7
     */
    public LLMExecutable(LLMCompConfig componentConfig) {
        validateConfig(componentConfig);
        this.config = componentConfig;
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LLMCompConfig getConfig() {
        return config;
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        this.session = session;
        this.context = context;
        List<BaseMessage> modelInputs = prepareModelInputs(inputs);
        writeUserMessageToContext(modelInputs);

        String response;
        try {
            AssistantMessage llmResponse =
                llm.invoke(modelInputs, null, null, null, null, null, null, null, null, null);
            response = llmResponse.getContent() != null ? llmResponse.getContent().toString() : "";
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED, "error_msg", e.getMessage());
        }

        writeAssistantMessageToContext(response);
        return createOutput(response);
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
        this.session = session;
        this.context = context;

        if (config.isCacheStream()) {
            state.clear();
        }

        String responseFormatType =
            config.getResponseFormat() != null ? (String) config.getResponseFormat().getOrDefault(TYPE_KEY, "") : "";

        try {
            if (WorkflowLLMResponseType.JSON.getValue().equals(responseFormatType)) {
                return invokeForJsonFormat(inputs);
            } else {
                return streamWithChunks(inputs);
            }
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED, "error_msg", e.getMessage());
        }
    }

    /**
     * Get the final output from cached stream content.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getStreamOutput() {
        if (config.isCacheStream()) {
            Map<String, Object> finalResult =
                state.buildFinalResult(config.getResponseFormat(), config.getOutputConfig());
            return finalResult.isEmpty() ? null : finalResult;
        }
        return null;
    }

    // ==================== Private Methods ====================

    /**
     * initializeIfNeeded.
     * 
     * @since 0.1.7
     */
    private void initializeIfNeeded() {
        if (!initialized) {
            try {
                llm = createLLMInstance();
                initialized = true;
            } catch (Exception e) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INIT_FAILED, "error_msg",
                        "failed to initialize llm: " + e.getMessage());
            }
        }
    }

    /**
     * createLLMInstance.
     * 
     * @return the result
     * @since 0.1.7
     */
    private Model createLLMInstance() {
        if (config.getModelId() != null) {
            // In Java, Runner.resourceMgr.getModel() would be called here;
            if (config.getModelClientConfig() == null || config.getModelConfig() == null) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED, "error_msg",
                        "failed to create llm instance: model config is null");
            }
            return new Model(config.getModelClientConfig(), config.getModelConfig());
        }
        if (config.getModelClientConfig() == null || config.getModelConfig() == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED, "error_msg",
                    "failed to create llm instance");
        }
        return new Model(config.getModelClientConfig(), config.getModelConfig());
    }

    @SuppressWarnings("unchecked")
    /**
     * prepareModelInputs.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * getModelInput.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    private List<BaseMessage> getModelInput(Map<String, Object> inputs) {
        List<BaseMessage> systemPrompt = buildSystemPrompt(inputs);
        List<BaseMessage> userPrompt = buildUserPromptContent(inputs);
        List<BaseMessage> allPrompts = insertHistoryToSystemAndUserPrompt(systemPrompt, userPrompt);
        return LLMPromptFormatter.formatPrompt(allPrompts, config.getResponseFormat(), config.getOutputConfig());
    }

    /**
     * buildSystemPrompt.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    private List<BaseMessage> buildSystemPrompt(Map<String, Object> inputs) {
        if (config.getSystemPromptTemplate() != null || config.getUserPromptTemplate() != null) {
            if (config.getSystemPromptTemplate() == null) {
                return new ArrayList<>();
            }
            PromptTemplate pt = PromptTemplate.builder().content(List.of(config.getSystemPromptTemplate())).build();
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
            systemMessages.add(SystemMessage.builder().content(m.getOrDefault("content", "").toString()).build());
        }
        PromptTemplate pt = PromptTemplate.builder().content(systemMessages).build();
        return pt.format(inputs).toMessages();
    }

    /**
     * buildUserPromptContent.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    private List<BaseMessage> buildUserPromptContent(Map<String, Object> inputs) {
        if (config.getSystemPromptTemplate() != null || config.getUserPromptTemplate() != null) {
            if (config.getUserPromptTemplate() == null) {
                return List.of(UserMessage.builder().content("").build());
            }
            PromptTemplate pt = PromptTemplate.builder().content(List.of(config.getUserPromptTemplate())).build();
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

        UserMessage um = UserMessage.builder().content(userPromptMap.getOrDefault("content", "").toString()).build();
        PromptTemplate pt = PromptTemplate.builder().content(List.of(um)).build();
        return pt.format(inputs).toMessages();
    }

    /**
     * insertHistoryToSystemAndUserPrompt.
     * 
     * @param systemPrompt systemPrompt
     * @param userPrompt userPrompt
     * @return the result
     * @since 0.1.7
     */
    private List<BaseMessage> insertHistoryToSystemAndUserPrompt(List<BaseMessage> systemPrompt,
            List<BaseMessage> userPrompt) {
        List<BaseMessage> result = new ArrayList<>(systemPrompt);
        if (config.isEnableHistory() && context != null) {
            List<BaseMessage> chatHistory = context.getMessages();
            if (chatHistory != null) {
                result.addAll(chatHistory);
            }
        }
        result.addAll(userPrompt);
        return result;
    }

    /**
     * Write the current user message to workflow context when history is enabled.
     *
     * @param modelInputs model inputs
     * @since 0.1.7
     */
    private void writeUserMessageToContext(List<BaseMessage> modelInputs) {
        if (!config.isEnableHistory() || context == null) {
            return;
        }
        for (int index = modelInputs.size() - 1; index >= 0; index--) {
            BaseMessage message = modelInputs.get(index);
            if (!MessageRole.USER.getValue().equals(message.getRole())) {
                continue;
            }
            Object content = message.getContent();
            if (content != null && !content.toString().isEmpty()) {
                context.addMessages(UserMessage.builder().content(content).build());
            }
            return;
        }
    }

    /**
     * Write the current assistant message to workflow context when history is enabled.
     *
     * @param content assistant response content
     * @since 0.1.7
     */
    private void writeAssistantMessageToContext(String content) {
        if (config.isEnableHistory() && context != null && content != null && !content.isEmpty()) {
            context.addMessages(new AssistantMessage(content));
        }
    }

    /**
     * createOutput.
     * 
     * @param llmOutput llmOutput
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> createOutput(String llmOutput) {
        try {
            return OutputFormatter.formatResponse(llmOutput, config.getResponseFormat(), config.getOutputConfig());
        } catch (BaseError e) {
            if (e.getCode() == StatusCode.COMPONENT_LLM_CONFIG_INVALID.getCode()) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_EXECUTION_PROCESS_ERROR, "error_msg",
                        e.getMessage());
            }
            throw e;
        }
    }

    /**
     * invokeForJsonFormat.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    private Iterator<Object> invokeForJsonFormat(Object inputs) {
        List<BaseMessage> modelInputs = prepareModelInputs(inputs);

        String llmOutputContent;
        try {
            AssistantMessage llmOutput = llm.invoke(modelInputs, null, null, null, null, null, null, null, null, null);
            llmOutputContent = llmOutput.getContent() != null ? llmOutput.getContent().toString() : "";
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED, "error_msg", e.getMessage());
        }

        if (config.isCacheStream()) {
            state.accumulateContent(llmOutputContent);
        }

        Object output = createOutput(llmOutputContent);
        return Collections.singletonList(output).iterator();
    }

    /**
     * streamWithChunks.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    private Iterator<Object> streamWithChunks(Object inputs) {
        List<BaseMessage> modelInputs = prepareModelInputs(inputs);

        Iterator<AssistantMessageChunk> llmStream;
        try {
            llmStream = llm.stream(modelInputs, null, null, null, null, null, null, null, null, null);
        } catch (Exception e) {
            if (config.isCacheStream()) {
                state.clear();
            }
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED, "error_msg", e.getMessage());
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
                    Map<String, Object> formattedRes =
                        OutputFormatter.formatResponse(content, config.getResponseFormat(), config.getOutputConfig());
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

    /**
     * validateConfig.
     * 
     * @param cfg cfg
     * @since 0.1.7
     */
    private void validateConfig(LLMCompConfig cfg) {
        validateTemplate(cfg.getTemplateContent(), cfg.getSystemPromptTemplate(), cfg.getUserPromptTemplate());
        validateResponseFormat(cfg.getResponseFormat(), cfg.getOutputConfig());
        validateOutputConfig(cfg.getOutputConfig());
    }

    /**
     * validateTemplate.
     * 
     * @param templateContent templateContent
     * @param systemPromptTemplate systemPromptTemplate
     * @param userPromptTemplate userPromptTemplate
     * @since 0.1.7
     */
    private void validateTemplate(List<Map<String, Object>> templateContent, SystemMessage systemPromptTemplate,
            UserMessage userPromptTemplate) {
        if (systemPromptTemplate != null || userPromptTemplate != null || templateContent == null
                || templateContent.isEmpty()) {
            return;
        }

        boolean containsUserMessage = false;
        for (Map<String, Object> element : templateContent) {
            if ("user".equals(element.get(ROLE_KEY))) {
                containsUserMessage = true;
            }
            if (containsUserMessage && "system".equals(element.get(ROLE_KEY))) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_TEMPLATE_CONFIG_ERROR, "error_msg",
                        "system message must be before user message");
            }
        }
        if (!containsUserMessage) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_TEMPLATE_CONFIG_ERROR, "error_msg",
                    "user message is required");
        }
    }

    /**
     * validateResponseFormat.
     * 
     * @param responseFormat responseFormat
     * @param outputConfig outputConfig
     * @since 0.1.7
     */
    private void validateResponseFormat(Map<String, Object> responseFormat, Map<String, Object> outputConfig) {
        if (responseFormat == null || responseFormat.isEmpty()) {
            return;
        }
        String resType = (String) responseFormat.get(TYPE_KEY);
        if (resType == null || (!resType.equals("text") && !resType.equals("markdown") && !resType.equals("json"))) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_RESPONSE_CONFIG_INVALID, "error_msg",
                    "response format '" + resType + "' is invalid");
        }
        if (("text".equals(resType) || "markdown".equals(resType)) && outputConfig != null
                && outputConfig.size() != 1) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_RESPONSE_CONFIG_INVALID, "error_msg",
                    "output config must contain exactly one parameter for text or markdown response type");
        }
    }

    /**
     * validateOutputConfig.
     * 
     * @param outputConfig outputConfig
     * @since 0.1.7
     */
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
                throw ErrorHelper.buildError(StatusCode.COMPONENT_LLM_CONFIG_ERROR, "error_msg",
                        "output config parameter is empty");
            }
        }
    }
}
