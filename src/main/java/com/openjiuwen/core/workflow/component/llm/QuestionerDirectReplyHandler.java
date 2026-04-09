  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.session.NodeSessionApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Handles "reply directly" questioner flow: field extraction via LLM,
 * state machine transitions, and follow-up question generation.
 * <p>
 * Mirrors Python's {@code QuestionerDirectReplyHandler}.
 */
public class QuestionerDirectReplyHandler {

    private static final Pattern JSON_BLOCK_PATTERN =
            Pattern.compile("^\\s*```json\\s*|\\s*```\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_BLOCK_SINGLE_PATTERN =
            Pattern.compile("^\\s*'''json\\s*|\\s*'''\\s*$", Pattern.CASE_INSENSITIVE);

    private QuestionerConfig config;
    private Model model;
    private QuestionerState state;
    private PromptTemplate prompt;
    private Object query = "";

    public QuestionerDirectReplyHandler config(QuestionerConfig config) {
        this.config = config;
        return this;
    }

    public QuestionerDirectReplyHandler model(Model model) {
        this.model = model;
        return this;
    }

    public QuestionerDirectReplyHandler state(QuestionerState state) {
        this.state = state;
        return this;
    }

    public QuestionerState getState() {
        return state;
    }

    public QuestionerDirectReplyHandler prompt(PromptTemplate prompt) {
        this.prompt = prompt;
        return this;
    }

    /**
     * Execute the handler based on current state.
     */
    public Map<String, Object> handle(Object inputs, NodeSessionApi session, ModelContext context) {
        return switch (state.getStatus()) {
            case START -> handleStartState(inputs, session, context);
            case USER_INTERACT -> handleUserInteractState(inputs, session, context);
            case END -> handleEndState();
        };
    }

    // ==================== State handlers ====================

    private Map<String, Object> handleStartState(Object inputs, NodeSessionApi session, ModelContext context) {
        QuestionerInput questionerInput = QuestionerUtils.validateInputs(inputs);
        OutputCache output = new OutputCache();
        this.query = questionerInput.getQuery() != null ? questionerInput.getQuery() : "";

        List<BaseMessage> chatHistory = getChatHistory(context);

        if (isSetQuestionContent()) {
            Map<String, Object> userFields = questionerInput.getExtraFields();
            output.setQuestion(QuestionerUtils.formatTemplate(config.getQuestionContent(), userFields));
            state.setQuestion(output.getQuestion());
            state = state.handleEvent(QuestionerEvent.USER_INTERACT_EVENT);
            return QuestionerUtils.formatQuestionerOutput(output);
        }

        if (needExtractFields()) {
            boolean isContinueAsk = extractFromChatHistory(chatHistory, output);
            QuestionerEvent event = isContinueAsk
                    ? QuestionerEvent.USER_INTERACT_EVENT : QuestionerEvent.END_EVENT;
            if (isContinueAsk) {
                state.setQuestion(output.getQuestion());
            }
            state = state.handleEvent(event);
        } else {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_QUESTIONER_INPUT_INVALID,
                    "error_msg", "question_content is empty and no extractable fields are configured");
        }
        return QuestionerUtils.formatQuestionerOutput(output);
    }

    private Map<String, Object> handleUserInteractState(Object inputs, NodeSessionApi session, ModelContext context) {
        // Get latest human feedback via session interaction
        int totalReads = state.getResponseNum() + 1;
        for (int i = 0; i < totalReads; i++) {
            this.query = (i == 0)
                    ? session.interact(state.getQuestion())
                    : session.userLatestInput(state.getQuestion());
        }
        state.incrementResponseNum();

        OutputCache output = new OutputCache();
        output.setQuestion(state.getQuestion());
        output.setUserResponse(query);

        List<BaseMessage> chatHistory = getChatHistory(context);
        Object userResponse = !chatHistory.isEmpty()
                ? chatHistory.get(chatHistory.size() - 1).getContent() : "";

        if (isSetQuestionContent() && !needExtractFields()) {
            output.setUserResponse(userResponse);
            state = state.handleEvent(QuestionerEvent.END_EVENT);
            return QuestionerUtils.formatQuestionerOutput(output);
        }

        if (needExtractFields()) {
            boolean isContinueAsk = extractFromChatHistory(chatHistory, output);
            QuestionerEvent event = isContinueAsk
                    ? QuestionerEvent.USER_INTERACT_EVENT : QuestionerEvent.END_EVENT;
            if (isContinueAsk) {
                state.setQuestion(output.getQuestion());
            }
            state = state.handleEvent(event);
        } else {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_QUESTIONER_INPUT_INVALID,
                    "error_msg", "question_content is empty and no extractable fields are configured");
        }
        return QuestionerUtils.formatQuestionerOutput(output);
    }

    private Map<String, Object> handleEndState() {
        QuestionerOutput output = QuestionerOutput.fromFields(state.getExtractedKeyFields());
        output.setUserResponse(state.getUserResponse());
        output.setQuestion(state.getQuestion());
        return output.toMap();
    }

    // ==================== LLM extraction ====================

    private boolean extractFromChatHistory(List<BaseMessage> chatHistory, OutputCache output) {
        List<BaseMessage> llmInputs = buildLlmInputs(chatHistory);
        Map<String, Object> extractedKeyFields = invokeLlmForExtraction(llmInputs);
        for (Map.Entry<String, Object> entry : extractedKeyFields.entrySet()) {
            if (entry.getValue() != null) {
                output.getKeyFields().put(entry.getKey(), entry.getValue());
            }
        }
        updateStateKeyFields(extractedKeyFields);

        updateParamDefaultValue(output);
        updateStateKeyFields(output.getKeyFields());

        return checkIfContinueAsk(output);
    }

    private List<BaseMessage> buildLlmInputs(List<BaseMessage> chatHistory) {
        Map<String, Object> templateInput = createPromptTemplateKeywords(chatHistory);
        PromptTemplate formatted = prompt.format(templateInput);
        return formatted.toMessages();
    }

    private Map<String, Object> createPromptTemplateKeywords(List<BaseMessage> chatHistory) {
        List<String> paramsList = new ArrayList<>();
        List<String> requiredNameList = new ArrayList<>();
        for (FieldInfo param : config.getFieldNames()) {
            paramsList.add(param.getFieldName() + ": " + param.getDescription());
            if (param.isRequired()) {
                String name = (param.getCnFieldName() != null && !param.getCnFieldName().isEmpty())
                        ? param.getCnFieldName() : param.getDescription();
                requiredNameList.add(name);
            }
        }

        String lang = config.getAcceptLanguage();
        String requiredNameStr;
        String dialogueHistoryStr;
        if ("en".equals(lang)) {
            requiredNameStr = String.join(", ", requiredNameList)
                    + " (" + requiredNameList.size() + " required field(s))";
            dialogueHistoryStr = chatHistory.stream()
                    .map(m -> m.getRole() + ": " + m.getContent())
                    .collect(Collectors.joining("\n"));
        } else {
            requiredNameStr = String.join("、", requiredNameList)
                    + requiredNameList.size() + "个必要信息";
            dialogueHistoryStr = chatHistory.stream()
                    .map(m -> m.getRole() + "：" + m.getContent())
                    .collect(Collectors.joining("\n"));
        }

        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("required_name", requiredNameStr);
        keywords.put("required_params_list", String.join("\n", paramsList));
        keywords.put("extra_info", config.getExtraPromptForFieldsExtraction());
        keywords.put("example", config.getExampleContent());
        keywords.put("dialogue_history", dialogueHistoryStr);
        return keywords;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeLlmForExtraction(List<BaseMessage> llmInputs) {
        String response;
        try {
            AssistantMessage msg = model.invoke(llmInputs, null, null, null,
                    null, null, null, null, null, null);
            response = msg.getContent() != null ? msg.getContent().toString() : "";
        } catch (Exception e) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("error_msg", "failed to invoke llm for extraction");
            throw ErrorHelper.buildError(StatusCode.COMPONENT_QUESTIONER_INVOKE_CALL_FAILED,
                    null, null, e, params);
        }

        Map<String, Object> result;
        try {
            String cleaned = JSON_BLOCK_PATTERN.matcher(response.strip()).replaceAll("");
            cleaned = JSON_BLOCK_SINGLE_PATTERN.matcher(cleaned).replaceAll("");
            Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(cleaned, Object.class);
            if (!(parsed instanceof Map)) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_QUESTIONER_EXECUTION_PROCESS_ERROR,
                        "error_msg", "failed to parse json from llm response");
            }
            result = (Map<String, Object>) parsed;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return new LinkedHashMap<>();
        }

        // Filter invalid values
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (QuestionerUtils.isValidValue(entry.getValue())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }

        // Validate and convert field types
        return validateAndConvertFields(filtered);
    }

    private Map<String, Object> validateAndConvertFields(Map<String, Object> extractedResult) {
        Map<String, String> fieldTypeMap = new LinkedHashMap<>();
        for (FieldInfo f : config.getFieldNames()) {
            fieldTypeMap.put(f.getFieldName(), f.getType());
        }

        Map<String, Object> validated = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : extractedResult.entrySet()) {
            if (!fieldTypeMap.containsKey(entry.getKey())) {
                continue;
            }
            String expectedType = fieldTypeMap.get(entry.getKey());
            Object[] result = QuestionerUtils.validateAndConvertType(entry.getValue(), expectedType);
            if ((boolean) result[1]) {
                validated.put(entry.getKey(), result[0]);
            }
        }
        return validated;
    }

    // ==================== State management ====================

    private void updateStateKeyFields(Map<String, Object> keyFields) {
        for (Map.Entry<String, Object> entry : keyFields.entrySet()) {
            if (entry.getValue() != null) {
                state.getExtractedKeyFields().put(entry.getKey(), entry.getValue());
            }
        }
    }

    private void updateParamDefaultValue(OutputCache output) {
        Map<String, Object> extractedKeyFields = state.getExtractedKeyFields();
        for (FieldInfo param : config.getFieldNames()) {
            String paramName = param.getFieldName();
            Object defaultValue = param.getDefaultValue();
            if (defaultValue != null && !"".equals(defaultValue)
                    && !extractedKeyFields.containsKey(paramName)) {
                output.getKeyFields().put(paramName, defaultValue);
            }
        }
    }

    private List<FieldInfo> filterNonExtractedKeyFields() {
        List<FieldInfo> result = new ArrayList<>();
        for (FieldInfo item : config.getFieldNames()) {
            if (item.isRequired() && !state.getExtractedKeyFields().containsKey(item.getFieldName())) {
                result.add(item);
            }
        }
        return result;
    }

    private boolean checkIfContinueAsk(OutputCache output) {
        boolean isContinueAsk = false;
        List<FieldInfo> nonExtractedKeyFields = filterNonExtractedKeyFields();
        if (!nonExtractedKeyFields.isEmpty()) {
            if (!exceedMaxResponse()) {
                output.setQuestion(QuestionerUtils.formatContinueAskQuestion(
                        nonExtractedKeyFields, config.getAcceptLanguage()));
                isContinueAsk = true;
            } else {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_QUESTIONER_RUNTIME_ERROR,
                        "error_msg", "max_response reached before all required fields were extracted");
            }
        }
        if (isContinueAsk) {
            output.getKeyFields().clear();
        } else {
            output.getKeyFields().putAll(state.getExtractedKeyFields());
        }
        return isContinueAsk;
    }

    private boolean exceedMaxResponse() {
        return state.getResponseNum() >= config.getMaxResponse();
    }

    // ==================== Helpers ====================

    private boolean isSetQuestionContent() {
        return config.getQuestionContent() != null && !config.getQuestionContent().isEmpty();
    }

    private boolean needExtractFields() {
        return config.isExtractFieldsFromResponse()
                && config.getFieldNames().size() > state.getExtractedKeyFields().size();
    }

    private List<BaseMessage> getChatHistory(ModelContext context) {
        List<BaseMessage> result = new ArrayList<>();
        // In Java version, context window access is synchronous
        if (config.isWithChatHistory() && context != null) {
            try {
                var contextWindow = context.getContextWindow(
                        null, null, null, config.getChatHistoryMaxRounds());
                if (contextWindow != null) {
                    result = new ArrayList<>(contextWindow.getMessages());
                }
            } catch (Exception ignored) {
                // context may not support getContextWindow in all implementations
            }
        }
        // Ensure last message is a user message
        if (result.isEmpty() || "assistant".equals(result.get(result.size() - 1).getRole())) {
            String content = query != null ? query.toString() : "";
            result.add(new UserMessage(content));
        }
        return result;
    }
}
