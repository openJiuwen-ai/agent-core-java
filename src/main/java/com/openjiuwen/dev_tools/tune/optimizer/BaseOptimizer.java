/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.core.operator.legacy.llm_call.LegacyOptimizerCallback;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Base class for legacy prompt tuning optimizers.
 *
 * <p>Mirrors Python's {@code BaseOptimizer} in
 * {@code openjiuwen/dev_tools/tune/optimizer/base.py}.</p>
 */
public abstract class BaseOptimizer implements AutoCloseable {

    protected Map<String, TextualParameter> parameters = new LinkedHashMap<>();
    protected OptimizeHistory history = new OptimizeHistory();
    protected List<EvaluatedCase> badCases = new ArrayList<>();

    protected BaseOptimizer() {
        this(null);
    }

    protected BaseOptimizer(Map<String, LLMCall> parameters) {
        bindParameter(parameters);
    }

    public BaseOptimizer enter() {
        batchSetOptimizerCallback(this::traceCallback);
        return this;
    }

    public CompletionStage<BaseOptimizer> aenter() {
        batchSetOptimizerCallback(this::traceCallback);
        return CompletableFuture.completedFuture(this);
    }

    public CompletionStage<Void> aexit(Throwable excType, Throwable excVal, Throwable excTb) {
        batchSetOptimizerCallback(null);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        batchSetOptimizerCallback(null);
    }

    public void bindParameter(Map<String, LLMCall> llmCalls) {
        if (llmCalls == null) {
            return;
        }
        for (Map.Entry<String, LLMCall> entry : llmCalls.entrySet()) {
            String name = entry.getKey();
            LLMCall llmCall = entry.getValue();
            if (llmCall == null) {
                throw ErrorHelper.buildError(
                        StatusCode.TOOLCHAIN_OPTIMIZER_PARAM_ERROR,
                        "error_msg",
                        "cannot bind a None parameter of " + name
                );
            }
            parameters.put(name, new TextualParameter(llmCall));
        }
        history = new OptimizeHistory();
        badCases = new ArrayList<>();
    }

    public void bind_parameter(Map<String, LLMCall> llmCalls) {
        bindParameter(llmCalls);
    }

    public void backward(List<EvaluatedCase> evaluatedCases) {
        validateParameters();
        getBadCases(evaluatedCases);
        try {
            doBackward(evaluatedCases == null ? List.of() : evaluatedCases);
        } catch (Exception exception) {
            throw wrapFailure(StatusCode.TOOLCHAIN_OPTIMIZER_BACKWARD_EXECUTION_ERROR, exception);
        }
    }

    public void update() {
        validateParameters();
        try {
            doUpdate();
            for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
                String name = entry.getKey();
                LLMCall llmCall = entry.getValue().getLlmCall();
                Loggers.AGENT.info("LLM call basic system prompt info llm_call_name={} frozen_system_prompt={} "
                                + "system_prompt_content={}",
                        name,
                        llmCall.getFreezeSystemPrompt(),
                        String.valueOf(llmCall.getSystemPrompt().getContent()));
                Loggers.AGENT.info("LLM call basic user prompt info llm_call_name={} frozen_system_prompt={} "
                                + "system_prompt_content={}",
                        name,
                        llmCall.getFreezeUserPrompt(),
                        String.valueOf(llmCall.getUserPrompt().getContent()));
            }
            history.clearHistory();
        } catch (Exception exception) {
            history.clearHistory();
            throw wrapFailure(StatusCode.TOOLCHAIN_OPTIMIZER_UPDATE_EXECUTION_ERROR, exception);
        }
    }

    public Map<String, TextualParameter> parameters() {
        return parameters;
    }

    public OptimizeHistory getHistory() {
        return history;
    }

    public List<EvaluatedCase> getBadCases() {
        return badCases;
    }

    public void traceCallback(String llmCallId, Map<String, Object> nodeInput, Object output, Object session) {
        String sessionId = sessionId(session);
        TraceNode traceNode = new TraceNode(
                sessionId,
                llmCallId,
                nodeInput,
                outputString(output)
        );
        history.addHistory(sessionId, traceNode);
    }

    protected void batchSetOptimizerCallback(LegacyOptimizerCallback callback) {
        for (TextualParameter parameter : parameters.values()) {
            parameter.getLlmCall().setOptimizerCallback(callback);
        }
    }

    protected List<EvaluatedCase> getBadCases(List<EvaluatedCase> evaluatedCases) {
        badCases = (evaluatedCases == null ? List.<EvaluatedCase>of() : evaluatedCases).stream()
                .filter(caseValue -> Double.compare(caseValue.getScore(), 0.0d) == 0)
                .collect(Collectors.toCollection(ArrayList::new));
        return badCases;
    }

    protected void validateParameters() {
        if (parameters.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR,
                    "error_msg",
                    "cannot optimize empty parameters"
            );
        }
    }

    protected BaseError wrapFailure(StatusCode statusCode, Throwable throwable) {
        String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
        return ErrorHelper.buildError(statusCode, message, null, throwable, Map.of("error_msg", message));
    }

    private static String outputString(Object output) {
        if (output instanceof BaseMessage message) {
            return TuneUtils.getOutputStringFromMessage(message);
        }
        return output == null ? "" : String.valueOf(output);
    }

    private static String sessionId(Object session) {
        if (session == null) {
            return "";
        }
        if (session instanceof CharSequence text) {
            return text.toString();
        }
        Object value = invokeNoArg(session, "getSessionId");
        if (value == null) {
            value = invokeNoArg(session, "get_session_id");
        }
        return value == null ? String.valueOf(session) : String.valueOf(value);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        Objects.requireNonNull(target, "target");
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    protected abstract void doUpdate();

    protected abstract void doBackward(List<EvaluatedCase> evaluatedCases);
}
