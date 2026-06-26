/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RunnerCallbackPackage}.
 *
 * <p>Mirrors Python's callback package initializer in
 * {@code openjiuwen/core/runner/callback/__init__.py}.</p>
 */
class RunnerCallbackPackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        assertThat(RunnerCallbackPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/runner/callback/__init__.py");
        assertThat(RunnerCallbackPackage.DESCRIPTION)
                .isEqualTo("Comprehensive Async Callback Framework");
        assertThat(RunnerCallbackPackage.all()).containsExactlyElementsOf(List.of(
                "FilterAction",
                "ChainAction",
                "HookType",
                "AgentEvents",
                "ContextEvents",
                "LLMCallEvents",
                "MemoryEvents",
                "RetrievalEvents",
                "SessionEvents",
                "ToolCallEvents",
                "WorkflowEvents",
                "CallbackMetrics",
                "FilterResult",
                "ChainContext",
                "ChainResult",
                "CallbackInfo",
                "EventFilter",
                "RateLimitFilter",
                "CircuitBreakerFilter",
                "ValidationFilter",
                "LoggingFilter",
                "AuthFilter",
                "ParamModifyFilter",
                "ConditionalFilter",
                "CallbackChain",
                "AbortError",
                "AsyncCallbackFramework",
                "trigger",
                "get_callback_framework",
                "lazy_callback_framework"
        ));
    }

    @Test
    void exportMetadataPreservesPythonSourcesAndJavaTargets() {
        assertThat(RunnerCallbackPackage.exports("AsyncCallbackFramework")).isTrue();
        assertThat(RunnerCallbackPackage.exports("missing")).isFalse();
        assertThat(RunnerCallbackPackage.sourceFor("CallbackChain"))
                .isEqualTo("openjiuwen.core.runner.callback.chain.CallbackChain");
        assertThat(RunnerCallbackPackage.javaSymbolNameFor("trigger"))
                .isEqualTo("com.openjiuwen.core.runner.callback.CallbackUtils#trigger");
        assertThat(RunnerCallbackPackage.resolveType("CallbackChain")).contains(CallbackChain.class);
        assertThat(RunnerCallbackPackage.resolveType("trigger")).isEmpty();
    }
}
