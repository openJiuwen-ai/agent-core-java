/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.openjiuwen.core.runner.callback.LLMCallEvents;
import com.openjiuwen.core.runner.callback.ToolCallEvents;

import java.util.List;
import java.util.Map;

/**
 * Prompt injection detection guardrail.
 *
 * <p>Mirrors Python's {@code PromptInjectionGuardrail} in
 * {@code openjiuwen.core.security.guardrail.builtin}.</p>
 */
public class PromptInjectionGuardrail extends BaseGuardrail {

    public PromptInjectionGuardrail() {
        this((PromptInjectionGuardrailConfig) null, (GuardrailBackend) null, true);
    }

    public PromptInjectionGuardrail(boolean enableLogging) {
        this((PromptInjectionGuardrailConfig) null, (GuardrailBackend) null, enableLogging);
    }

    public PromptInjectionGuardrail(PromptInjectionGuardrailConfig config) {
        this(config, null, true);
    }

    public PromptInjectionGuardrail(PromptInjectionGuardrailConfig config, boolean enableLogging) {
        this(config, null, enableLogging);
    }

    public PromptInjectionGuardrail(GuardrailBackend backend, boolean enableLogging) {
        this((PromptInjectionGuardrailConfig) null, backend, enableLogging);
    }

    public PromptInjectionGuardrail(GuardrailBackend backend, List<String> events, boolean enableLogging) {
        super(backend, events, enableLogging);
    }

    public PromptInjectionGuardrail(PromptInjectionGuardrailConfig config, GuardrailBackend backend,
                                    boolean enableLogging) {
        super(backend != null ? backend : buildBackendFromConfig(config != null ? config : new PromptInjectionGuardrailConfig()),
                null, enableLogging);
    }

    @Override
    protected List<String> defaultEvents() {
        return List.of(LLMCallEvents.LLM_INVOKE_INPUT, ToolCallEvents.TOOL_INVOKE_OUTPUT);
    }

    @Override
    public GuardrailResult detect(String eventName, Object[] args, Map<String, Object> kwargs) throws Exception {
        GuardrailContext context = extractContext(eventName, args, kwargs);
        RiskAssessment assessment;
        if (backend instanceof RuleBasedPromptInjectionBackend ruleBackend) {
            assessment = ruleBackend.analyze(context);
        } else if (backend instanceof APIModelBackend apiBackend) {
            assessment = apiBackend.analyze(context);
        } else if (backend instanceof LocalModelBackend localBackend) {
            assessment = localBackend.analyze(context);
        } else {
            assessment = backend.analyze(Map.of(
                    "event", eventName,
                    "content", context.getContent(),
                    "metadata", context.getMetadata()
            ));
        }
        if (assessment == null || !assessment.isHasRisk()) {
            return GuardrailResult.pass(assessment != null ? assessment.getDetails() : null);
        }
        return GuardrailResult.block(
                assessment.getRiskLevel(),
                assessment.getRiskType(),
                assessment.getDetails(),
                null
        );
    }

    public GuardrailContext extractContext(String event, Object[] args, Map<String, Object> kwargs) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("event_source", event);
        if (LLMCallEvents.LLM_INVOKE_INPUT.equals(event)) {
            Object messages = kwargs != null ? kwargs.get("messages") : null;
            if (messages instanceof List<?> messageList && !messageList.isEmpty()) {
                Object lastMessage = messageList.get(messageList.size() - 1);
                metadata.put("message_count", messageList.size());
                return new GuardrailContext(
                        GuardrailContentType.TEXT,
                        extractTextFromMessage(lastMessage),
                        event,
                        metadata
                );
            }
            return new GuardrailContext(GuardrailContentType.MESSAGES, messages != null ? messages : List.of(), event,
                    metadata);
        }
        if (ToolCallEvents.TOOL_INVOKE_OUTPUT.equals(event)) {
            Object result = kwargs != null ? kwargs.get("result") : null;
            return new GuardrailContext(GuardrailContentType.TEXT, result != null ? String.valueOf(result) : "", event,
                    metadata);
        }
        return new GuardrailContext(GuardrailContentType.RAW, Map.of(
                "args", args != null ? List.of(args) : List.of(),
                "kwargs", kwargs != null ? kwargs : Map.of()
        ), event, metadata);
    }

    public static GuardrailBackend buildBackendFromConfig(PromptInjectionGuardrailConfig config) {
        String mode = config.getMode() != null ? config.getMode() : "rules";
        if (!List.of("rules", "api", "local").contains(mode)) {
            throw new IllegalArgumentException("invalid mode: " + mode + ", must be 'rules', 'api' or 'local'");
        }
        if ("rules".equals(mode)) {
            return new RuleBasedPromptInjectionBackend(
                    new RuleBasedBackendConfig(config.getCustomPatterns(), config.getRiskLevel())
            );
        }
        if ("api".equals(mode) && (config.getApiUrl() == null || config.getApiUrl().isBlank())) {
            throw new IllegalArgumentException("api_url is required for api mode");
        }
        if ("local".equals(mode) && (config.getModelPath() == null || config.getModelPath().isBlank())) {
            throw new IllegalArgumentException("model_path is required for local mode");
        }
        if (config.getModelType() == null && config.getParser() == null) {
            throw new IllegalArgumentException("either model_type or parser must be specified for api/local mode");
        }
        if (config.getModelType() != null && !"bert".equals(config.getModelType()) && !"qwen".equals(config.getModelType())) {
            throw new IllegalArgumentException("unknown model_type: " + config.getModelType());
        }

        ModelOutputParser parser = config.getParser();
        if (parser == null) {
            if ("bert".equals(config.getModelType())) {
                parser = new BertBinaryParser(
                        "prompt_injection",
                        config.getBertThresholds(),
                        config.getAttackClassId()
                );
            } else {
                parser = new QwenGuardParser(config.getQwenRiskType());
            }
        }

        if ("api".equals(mode)) {
            return new APIModelBackend(new APIModelBackendConfig(
                    config.getApiUrl(),
                    parser,
                    config.getApiKey(),
                    config.getTimeout(),
                    "model_detection"
            ));
        }
        return new LocalModelBackend(new LocalModelBackendConfig(
                config.getModelPath(),
                parser,
                config.getDevice(),
                "model_detection"
        ));
    }

    private String extractTextFromMessage(Object message) {
        if (message instanceof Map<?, ?> map) {
            Object content = map.get("content");
            return content != null ? String.valueOf(content) : String.valueOf(message);
        }
        return message != null ? String.valueOf(message) : "";
    }
}
