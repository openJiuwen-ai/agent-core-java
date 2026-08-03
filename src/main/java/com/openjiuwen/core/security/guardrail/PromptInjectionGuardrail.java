/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.openjiuwen.core.runner.callback.LLMCallEvents;
import com.openjiuwen.core.runner.callback.ToolCallEvents;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt injection detection guardrail.
 *
 * <p>Mirrors Python's {@code PromptInjectionGuardrail} in
 * {@code openjiuwen/core/security/guardrail/builtin.py}.</p>
 */
public class PromptInjectionGuardrail extends BaseGuardrail {

    public static final List<Object> DEFAULT_EVENTS = List.of(
            LLMCallEvents.LLM_INVOKE_INPUT,
            ToolCallEvents.TOOL_INVOKE_OUTPUT
    );

    public PromptInjectionGuardrail() {
        this(null, null, null, true);
    }

    public PromptInjectionGuardrail(PromptInjectionGuardrailConfig config) {
        this(null, null, config, true);
    }

    public PromptInjectionGuardrail(PromptInjectionGuardrailConfig config, boolean enableLogging) {
        this(null, null, config, enableLogging);
    }

    public PromptInjectionGuardrail(GuardrailBackend backend, boolean enableLogging) {
        this(null, backend, null, enableLogging);
    }

    public PromptInjectionGuardrail(List<?> events, GuardrailBackend backend, boolean enableLogging) {
        this(events, backend, null, enableLogging);
    }

    public PromptInjectionGuardrail(
            List<?> events,
            GuardrailBackend backend,
            PromptInjectionGuardrailConfig config,
            boolean enableLogging) {
        super(
                events,
                backend != null ? backend : buildBackendFromConfig(config != null ? config : new PromptInjectionGuardrailConfig()),
                null,
                enableLogging
        );
    }

    public static GuardrailBackend buildBackendFromConfig(PromptInjectionGuardrailConfig config) {
        PromptInjectionGuardrailConfig effectiveConfig =
                config != null ? config : new PromptInjectionGuardrailConfig();
        String mode = effectiveConfig.getMode() != null ? effectiveConfig.getMode() : "rules";

        if (!List.of("rules", "api", "local").contains(mode)) {
            throw new IllegalArgumentException("invalid mode: " + mode + ", must be 'rules', 'api' or 'local'");
        }

        if ("rules".equals(mode)) {
            return new RuleBasedPromptInjectionBackend(
                    new RuleBasedBackendConfig(
                            effectiveConfig.getCustomPatterns(),
                            effectiveConfig.getRiskLevel()
                    )
            );
        }

        if ("api".equals(mode) && isBlank(effectiveConfig.getApiUrl())) {
            throw new IllegalArgumentException("api_url is required for api mode");
        }

        if ("local".equals(mode) && isBlank(effectiveConfig.getModelPath())) {
            throw new IllegalArgumentException("model_path is required for local mode");
        }

        if (effectiveConfig.getModelType() == null && effectiveConfig.getParser() == null) {
            throw new IllegalArgumentException("either model_type or parser must be specified for api/local mode");
        }

        if (effectiveConfig.getModelType() != null && !List.of("bert", "qwen").contains(effectiveConfig.getModelType())) {
            throw new IllegalArgumentException("unknown model_type: " + effectiveConfig.getModelType());
        }

        ModelOutputParser parser = effectiveConfig.getParser();
        if (parser == null) {
            if ("bert".equals(effectiveConfig.getModelType())) {
                parser = new BertBinaryParser(
                        "prompt_injection",
                        effectiveConfig.getBertThresholds(),
                        effectiveConfig.getAttackClassId()
                );
            } else {
                parser = new QwenGuardParser(effectiveConfig.getQwenRiskType());
            }
        }

        if ("api".equals(mode)) {
            return new APIModelBackend(
                    new APIModelBackendConfig(
                            effectiveConfig.getApiUrl(),
                            parser,
                            effectiveConfig.getApiKey(),
                            effectiveConfig.getTimeout(),
                            "model_detection"
                    )
            );
        }

        return new LocalModelBackend(
                new LocalModelBackendConfig(
                        effectiveConfig.getModelPath(),
                        parser,
                        effectiveConfig.getDevice(),
                        "model_detection"
                )
        );
    }

    @Override
    public GuardrailContext extractContext(Object event, Object[] args, Map<String, Object> kwargs) {
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("event_source", String.valueOf(event));

        String eventName = normalizeEvent(event);
        String llmEvent = normalizeEvent(LLMCallEvents.LLM_INVOKE_INPUT);
        String toolEvent = normalizeEvent(ToolCallEvents.TOOL_INVOKE_OUTPUT);

        if (eventName.equals(llmEvent)) {
            Object messages = safeKwargs.get("messages");
            if (messages instanceof List<?> messageList && !messageList.isEmpty()) {
                Object lastMessage = messageList.get(messageList.size() - 1);
                metadata.put("message_count", messageList.size());
                return new GuardrailContext(
                        GuardrailContentType.TEXT,
                        extractTextFromMessage(lastMessage),
                        String.valueOf(event),
                        metadata
                );
            }
            return new GuardrailContext(
                    GuardrailContentType.MESSAGES,
                    messages instanceof List<?> list ? list : List.of(),
                    String.valueOf(event),
                    metadata
            );
        }

        if (eventName.equals(toolEvent)) {
            Object result = safeKwargs.get("result");
            return new GuardrailContext(
                    GuardrailContentType.TEXT,
                    result != null ? String.valueOf(result) : "",
                    String.valueOf(event),
                    metadata
            );
        }

        return new GuardrailContext(
                GuardrailContentType.RAW,
                Map.of(
                        "args", args != null ? List.of(args) : List.of(),
                        "kwargs", new LinkedHashMap<>(safeKwargs)
                ),
                String.valueOf(event),
                metadata
        );
    }

    private static String normalizeEvent(Object event) {
        return event == null ? "" : String.valueOf(event);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String extractTextFromMessage(Object message) {
        if (message instanceof Map<?, ?> map) {
            Object content = map.get("content");
            return content != null ? String.valueOf(content) : String.valueOf(message);
        }

        Object getterValue = invokeContentGetter(message);
        if (getterValue != null) {
            return String.valueOf(getterValue);
        }

        Object fieldValue = readContentField(message);
        if (fieldValue != null) {
            return String.valueOf(fieldValue);
        }

        return message != null ? String.valueOf(message) : "";
    }

    private static Object invokeContentGetter(Object message) {
        if (message == null) {
            return null;
        }
        try {
            Method method = message.getClass().getMethod("getContent");
            return method.invoke(message);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object readContentField(Object message) {
        if (message == null) {
            return null;
        }
        try {
            Field field = message.getClass().getDeclaredField("content");
            field.setAccessible(true);
            return field.get(message);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
