/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.openjiuwen.core.common.exception.GuardrailError;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.callback.DecoratorFramework;

import java.util.List;
import java.util.Objects;

/**
 * Package bridge for guardrail framework exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.security.guardrail} in
 * {@code openjiuwen/core/security/guardrail/__init__.py}.</p>
 */
public final class GuardrailPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/security/guardrail/__init__.py";

    public static final Class<RiskLevel> RISK_LEVEL = RiskLevel.class;
    public static final Class<GuardrailContentType> GUARDRAIL_CONTENT_TYPE = GuardrailContentType.class;
    public static final Class<GuardrailResult> GUARDRAIL_RESULT = GuardrailResult.class;
    public static final Class<RiskAssessment> RISK_ASSESSMENT = RiskAssessment.class;
    public static final Class<GuardrailContext> GUARDRAIL_CONTEXT = GuardrailContext.class;
    public static final Class<ModelOutputParser> MODEL_OUTPUT_PARSER = ModelOutputParser.class;
    public static final Class<BertBinaryParser> BERT_BINARY_PARSER = BertBinaryParser.class;
    public static final Class<QwenGuardParser> QWEN_GUARD_PARSER = QwenGuardParser.class;
    public static final Class<GuardrailBackend> GUARDRAIL_BACKEND = GuardrailBackend.class;
    public static final Class<RuleBasedPromptInjectionBackend> RULE_BASED_PROMPT_INJECTION_BACKEND =
            RuleBasedPromptInjectionBackend.class;
    public static final Class<RuleBasedBackendConfig> RULE_BASED_BACKEND_CONFIG = RuleBasedBackendConfig.class;
    public static final Class<LLMPromptInjectionBackend> LLM_PROMPT_INJECTION_BACKEND =
            LLMPromptInjectionBackend.class;
    public static final Class<LLMPromptInjectionBackendConfig> LLM_PROMPT_INJECTION_BACKEND_CONFIG =
            LLMPromptInjectionBackendConfig.class;
    public static final Class<APIModelBackend> API_MODEL_BACKEND = APIModelBackend.class;
    public static final Class<APIModelBackendConfig> API_MODEL_BACKEND_CONFIG = APIModelBackendConfig.class;
    public static final Class<LocalModelBackend> LOCAL_MODEL_BACKEND = LocalModelBackend.class;
    public static final Class<LocalModelBackendConfig> LOCAL_MODEL_BACKEND_CONFIG = LocalModelBackendConfig.class;
    public static final Class<BaseGuardrail> BASE_GUARDRAIL = BaseGuardrail.class;
    public static final Class<PromptInjectionGuardrail> PROMPT_INJECTION_GUARDRAIL = PromptInjectionGuardrail.class;
    public static final Class<PromptInjectionGuardrailConfig> PROMPT_INJECTION_GUARDRAIL_CONFIG =
            PromptInjectionGuardrailConfig.class;
    public static final Class<GuardrailError> GUARDRAIL_ERROR = GuardrailError.class;

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "RiskLevel",
            "GuardrailContentType",
            "GuardrailResult",
            "RiskAssessment",
            "GuardrailContext",
            "ModelOutputParser",
            "BertBinaryParser",
            "QwenGuardParser",
            "GuardrailBackend",
            "RuleBasedPromptInjectionBackend",
            "RuleBasedBackendConfig",
            "LLMPromptInjectionBackend",
            "LLMPromptInjectionBackendConfig",
            "APIModelBackend",
            "APIModelBackendConfig",
            "LocalModelBackend",
            "LocalModelBackendConfig",
            "BaseGuardrail",
            "PromptInjectionGuardrail",
            "PromptInjectionGuardrailConfig",
            "GuardrailError",
            "register_guardrail",
            "unregister_guardrail"
    );

    private GuardrailPackage() {
    }

    public static void registerGuardrail(BaseGuardrail guardrail) {
        registerGuardrail(guardrail, Runner.getCallbackFramework());
    }

    public static void register_guardrail(BaseGuardrail guardrail) {
        registerGuardrail(guardrail);
    }

    public static void registerGuardrail(BaseGuardrail guardrail, DecoratorFramework framework) {
        Objects.requireNonNull(guardrail, "guardrail").register(Objects.requireNonNull(framework, "framework"));
    }

    public static void unregisterGuardrail(BaseGuardrail guardrail) {
        Objects.requireNonNull(guardrail, "guardrail").unregister();
    }

    public static void unregister_guardrail(BaseGuardrail guardrail) {
        unregisterGuardrail(guardrail);
    }
}
