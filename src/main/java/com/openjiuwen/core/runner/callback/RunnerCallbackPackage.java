/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Package bridge for callback framework exports.
 *
 * <p>Mirrors Python's callback package initializer in
 * {@code openjiuwen/core/runner/callback/__init__.py}.</p>
 */
public final class RunnerCallbackPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/runner/callback/__init__.py";

    public static final String DESCRIPTION = "Comprehensive Async Callback Framework";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
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
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_SYMBOL_NAMES = buildJavaSymbolNames();

    private RunnerCallbackPackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return callback package exports in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    public static String javaSymbolNameFor(String symbolName) {
        return JAVA_SYMBOL_NAMES.get(symbolName);
    }

    public static Optional<Class<?>> resolveType(String symbolName) {
        String javaSymbolName = JAVA_SYMBOL_NAMES.get(symbolName);
        if (javaSymbolName == null || javaSymbolName.contains("#")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(javaSymbolName));
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        }
    }

    private static Map<String, String> buildExportSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("FilterAction", "openjiuwen.core.runner.callback.enums.FilterAction");
        sources.put("ChainAction", "openjiuwen.core.runner.callback.enums.ChainAction");
        sources.put("HookType", "openjiuwen.core.runner.callback.enums.HookType");
        sources.put("AgentEvents", "openjiuwen.core.runner.callback.events.AgentEvents");
        sources.put("ContextEvents", "openjiuwen.core.runner.callback.events.ContextEvents");
        sources.put("LLMCallEvents", "openjiuwen.core.runner.callback.events.LLMCallEvents");
        sources.put("MemoryEvents", "openjiuwen.core.runner.callback.events.MemoryEvents");
        sources.put("RetrievalEvents", "openjiuwen.core.runner.callback.events.RetrievalEvents");
        sources.put("SessionEvents", "openjiuwen.core.runner.callback.events.SessionEvents");
        sources.put("ToolCallEvents", "openjiuwen.core.runner.callback.events.ToolCallEvents");
        sources.put("WorkflowEvents", "openjiuwen.core.runner.callback.events.WorkflowEvents");
        sources.put("CallbackMetrics", "openjiuwen.core.runner.callback.models.CallbackMetrics");
        sources.put("FilterResult", "openjiuwen.core.runner.callback.models.FilterResult");
        sources.put("ChainContext", "openjiuwen.core.runner.callback.models.ChainContext");
        sources.put("ChainResult", "openjiuwen.core.runner.callback.models.ChainResult");
        sources.put("CallbackInfo", "openjiuwen.core.runner.callback.models.CallbackInfo");
        sources.put("EventFilter", "openjiuwen.core.runner.callback.filters.EventFilter");
        sources.put("RateLimitFilter", "openjiuwen.core.runner.callback.filters.RateLimitFilter");
        sources.put("CircuitBreakerFilter", "openjiuwen.core.runner.callback.filters.CircuitBreakerFilter");
        sources.put("ValidationFilter", "openjiuwen.core.runner.callback.filters.ValidationFilter");
        sources.put("LoggingFilter", "openjiuwen.core.runner.callback.filters.LoggingFilter");
        sources.put("AuthFilter", "openjiuwen.core.runner.callback.filters.AuthFilter");
        sources.put("ParamModifyFilter", "openjiuwen.core.runner.callback.filters.ParamModifyFilter");
        sources.put("ConditionalFilter", "openjiuwen.core.runner.callback.filters.ConditionalFilter");
        sources.put("CallbackChain", "openjiuwen.core.runner.callback.chain.CallbackChain");
        sources.put("AbortError", "openjiuwen.core.runner.callback.errors.AbortError");
        sources.put("AsyncCallbackFramework", "openjiuwen.core.runner.callback.framework.AsyncCallbackFramework");
        sources.put("trigger", "openjiuwen.core.runner.callback.utils.trigger");
        sources.put("get_callback_framework", "openjiuwen.core.runner.callback.utils.get_callback_framework");
        sources.put("lazy_callback_framework", "openjiuwen.core.runner.callback.utils.lazy_callback_framework");
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaSymbolNames() {
        Map<String, String> symbols = new LinkedHashMap<>();
        symbols.put("FilterAction", "com.openjiuwen.core.runner.callback.FilterAction");
        symbols.put("ChainAction", "com.openjiuwen.core.runner.callback.ChainAction");
        symbols.put("HookType", "com.openjiuwen.core.runner.callback.HookType");
        symbols.put("AgentEvents", "com.openjiuwen.core.runner.callback.AgentEvents");
        symbols.put("ContextEvents", "com.openjiuwen.core.runner.callback.ContextEvents");
        symbols.put("LLMCallEvents", "com.openjiuwen.core.runner.callback.LLMCallEvents");
        symbols.put("MemoryEvents", "com.openjiuwen.core.runner.callback.MemoryEvents");
        symbols.put("RetrievalEvents", "com.openjiuwen.core.runner.callback.RetrievalEvents");
        symbols.put("SessionEvents", "com.openjiuwen.core.runner.callback.SessionEvents");
        symbols.put("ToolCallEvents", "com.openjiuwen.core.runner.callback.ToolCallEvents");
        symbols.put("WorkflowEvents", "com.openjiuwen.core.runner.callback.WorkflowEvents");
        symbols.put("CallbackMetrics", "com.openjiuwen.core.runner.callback.CallbackMetrics");
        symbols.put("FilterResult", "com.openjiuwen.core.runner.callback.FilterResult");
        symbols.put("ChainContext", "com.openjiuwen.core.runner.callback.ChainContext");
        symbols.put("ChainResult", "com.openjiuwen.core.runner.callback.ChainResult");
        symbols.put("CallbackInfo", "com.openjiuwen.core.runner.callback.CallbackInfo");
        symbols.put("EventFilter", "com.openjiuwen.core.runner.callback.EventFilter");
        symbols.put("RateLimitFilter", "com.openjiuwen.core.runner.callback.RateLimitFilter");
        symbols.put("CircuitBreakerFilter", "com.openjiuwen.core.runner.callback.CircuitBreakerFilter");
        symbols.put("ValidationFilter", "com.openjiuwen.core.runner.callback.ValidationFilter");
        symbols.put("LoggingFilter", "com.openjiuwen.core.runner.callback.LoggingFilter");
        symbols.put("AuthFilter", "com.openjiuwen.core.runner.callback.AuthFilter");
        symbols.put("ParamModifyFilter", "com.openjiuwen.core.runner.callback.ParamModifyFilter");
        symbols.put("ConditionalFilter", "com.openjiuwen.core.runner.callback.ConditionalFilter");
        symbols.put("CallbackChain", "com.openjiuwen.core.runner.callback.CallbackChain");
        symbols.put("AbortError", "com.openjiuwen.core.runner.callback.AbortError");
        symbols.put("AsyncCallbackFramework", "com.openjiuwen.core.runner.callback.AsyncCallbackFramework");
        symbols.put("trigger", "com.openjiuwen.core.runner.callback.CallbackUtils#trigger");
        symbols.put("get_callback_framework", "com.openjiuwen.core.runner.callback.CallbackUtils#getCallbackFramework");
        symbols.put("lazy_callback_framework", "com.openjiuwen.core.runner.callback.CallbackUtils#lazyCallbackFramework");
        return Collections.unmodifiableMap(symbols);
    }
}
