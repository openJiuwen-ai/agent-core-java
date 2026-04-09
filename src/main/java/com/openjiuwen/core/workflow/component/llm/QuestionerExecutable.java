/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.util.List;
import java.util.Map;

/**
 * Executable for the Questioner workflow component.
 * <p>
 * Manages state machine lifecycle, LLM initialization, and delegates to
 * {@link QuestionerDirectReplyHandler} for actual extraction.
 * <p>
 * Mirrors Python's {@code QuestionerExecutable}.
 */
public class QuestionerExecutable extends ComponentExecutable {

    private final QuestionerConfig config;
    private final QuestionerDefaultConfig defaultConfig;
    private final PromptTemplate prompt;
    private Model llm;
    private boolean initialized = false;
    private QuestionerState state;

    public QuestionerExecutable(QuestionerConfig config) {
        validateConfig(config);
        this.config = config;
        this.defaultConfig = QuestionerDefaultConfig.fromLanguage(config.getAcceptLanguage());
        this.prompt = initPrompt();
    }

    public QuestionerExecutable state(QuestionerState state) {
        this.state = state;
        return this;
    }

    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        // Load node-scoped state first so resumed questioner runs can recover extracted fields.
        Object sessionState = session.getState(null);
        if (sessionState == null) {
            sessionState = session.dumpState();
        }
        QuestionerState stateFromSession = QuestionerState.loadFromSession(sessionState);

        QuestionerState currentState;
        if (stateFromSession.isUndergoingInteraction()) {
            currentState = stateFromSession; // recover state from session
        } else {
            currentState = new QuestionerState(); // create new state
        }

        currentState = currentState.handleEvent(QuestionerEvent.START_EVENT);
        initializeIfNeeded();

        Map<String, Object> invokeResult;
        if (ResponseType.REPLY_DIRECTLY.getValue().equals(config.getResponseType())) {
            invokeResult = handleQuestionerDirectReply(inputs, session, context, currentState);
            // handler updates state via side-effect
            if (invokeResult.containsKey("_state")) {
                currentState = (QuestionerState) invokeResult.remove("_state");
            }
        } else {
            invokeResult = Map.of();
        }

        if (currentState.isUndergoingInteraction()) {
            QuestionerState.storeToSession(currentState, session);
            session.interact(invokeResult.getOrDefault("question", ""));
        } else {
            // Clear state when component completes normally to support reentrancy
            QuestionerState newState = new QuestionerState();
            this.state = newState;
            QuestionerState.storeToSession(newState, session);
        }

        return invokeResult;
    }

    // ==================== Internal ====================

    private Map<String, Object> handleQuestionerDirectReply(
            Object inputs, NodeSessionApi session, ModelContext context, QuestionerState currentState) {
        QuestionerDirectReplyHandler handler = new QuestionerDirectReplyHandler()
                .config(config)
                .model(llm)
                .state(currentState)
                .prompt(prompt);
        Map<String, Object> result = new java.util.LinkedHashMap<>(handler.handle(inputs, session, context));
        result.put("_state", handler.getState());
        return result;
    }

    private void initializeIfNeeded() {
        if (initialized) {
            return;
        }
        try {
            llm = createLlmInstance();
            initialized = true;
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_QUESTIONER_INVOKE_CALL_FAILED,
                    "error_msg", "failed to initialize llm if needed");
        }
    }

    private Model createLlmInstance() {
        if (config.getModelId() != null) {
            // In the full framework, this would use Runner.resourceMgr.getModel().
            // For now, throw if only modelId is provided without client/request configs.
            throw ErrorHelper.buildError(StatusCode.COMPONENT_QUESTIONER_INVOKE_CALL_FAILED,
                    "error_msg", "model_id based model lookup not yet supported in Java; "
                            + "provide modelClientConfig and modelConfig instead");
        }
        if (config.getModelClientConfig() == null || config.getModelConfig() == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_QUESTIONER_INVOKE_CALL_FAILED,
                    "error_msg", "failed to create llm instance");
        }
        return new Model(config.getModelClientConfig(), config.getModelConfig());
    }

    private PromptTemplate initPrompt() {
        List<BaseMessage> templateMessages = defaultConfig.getPromptTemplate();
        return PromptTemplate.builder().content(templateMessages).build();
    }

    // ==================== Config validation ====================

    private void validateConfig(QuestionerConfig config) {
        validateResponseTypeConfig(config.getResponseType());
        validateExtractKeyFieldsConfig(config.isExtractFieldsFromResponse(), config.getFieldNames());
        validateMaxResponseNumConfig(config.getMaxResponse());
    }

    private static void validateResponseTypeConfig(String responseType) {
        if (!ResponseType.isValid(responseType)) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_QUESTIONER_CONFIG_ERROR,
                    "error_msg", "response type " + responseType + " is invalid");
        }
    }

    private static void validateExtractKeyFieldsConfig(boolean ifExtract, List<FieldInfo> fields) {
        if (ifExtract && (fields == null || fields.isEmpty())) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_QUESTIONER_CONFIG_ERROR,
                    "error_msg", "extracted key fields cannot be empty");
        }
        if (fields != null) {
            for (FieldInfo item : fields) {
                if (item.getFieldName() == null || item.getFieldName().isEmpty()) {
                    throw ErrorHelper.buildError(StatusCode.COMPONENT_QUESTIONER_CONFIG_ERROR,
                            "error_msg", "extracted key field name cannot be empty");
                }
            }
        }
    }

    private static void validateMaxResponseNumConfig(int maxResponseNum) {
        if (maxResponseNum <= 0) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_QUESTIONER_CONFIG_ERROR,
                    "error_msg", "max response must be greater than 0");
        }
    }
}
