/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for guardrail package exports and helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.security.guardrail} in
 * {@code openjiuwen/core/security/guardrail/__init__.py}.</p>
 */
class GuardrailPackageTest {

    @Test
    void exposesPythonAllSymbolsInOrder() {
        assertThat(GuardrailPackage.EXPORTED_SYMBOLS).containsExactly(
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
    }

    @Test
    void exportedTypesPointToTranslatedImplementations() {
        assertThat(GuardrailPackage.PROMPT_INJECTION_GUARDRAIL).isSameAs(PromptInjectionGuardrail.class);
        assertThat(GuardrailPackage.BASE_GUARDRAIL).isSameAs(BaseGuardrail.class);
        assertThat(GuardrailPackage.RISK_LEVEL).isSameAs(RiskLevel.class);
        assertThat(GuardrailPackage.GUARDRAIL_ERROR.getSimpleName()).isEqualTo("GuardrailError");
    }

    @Test
    void registerAndUnregisterUseProvidedFrameworkLikeRunnerCallbackFramework() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        TestGuardrail guardrail = new TestGuardrail();

        GuardrailPackage.registerGuardrail(guardrail, framework);

        assertThat(guardrail.getRegisteredEvents()).containsExactly("guard.event");
        assertThat(framework.getCallbacks()).containsKey("guard.event");

        GuardrailPackage.unregisterGuardrail(guardrail);

        assertThat(guardrail.getRegisteredEvents()).isEmpty();
        assertThat(framework.getCallbacks()).doesNotContainKey("guard.event");
    }

    private static final class TestGuardrail extends BaseGuardrail {
        private TestGuardrail() {
            super(List.of("guard.event"), null, false);
        }

        @Override
        public GuardrailContext extractContext(Object event, Object[] args, Map<String, Object> kwargs) {
            return null;
        }
    }
}
