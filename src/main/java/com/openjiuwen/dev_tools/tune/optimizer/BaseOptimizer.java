/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base optimizer for prompt tuning.
 * <p>
 * Mirrors Python's {@code BaseOptimizer} in {@code openjiuwen.dev_tools.tune.optimizer.base}.
 * 
 * @since 0.1.7
 */
public abstract class BaseOptimizer implements AutoCloseable {
    /**
     * parameters.
     * 
     * @since 0.1.7
     */
    protected final Map<String, TextualParameter> parameters;

    /**
     * history.
     * 
     * @since 0.1.7
     */
    protected final OptimizeHistory history;

    /**
     * badCases.
     * 
     * @since 0.1.7
     */
    protected List<EvaluatedCase> badCases;

    /**
     * Creates a BaseOptimizer with optional parameters.
     * 
     * @param parameters parameters
     * @since 0.1.7
     */
    public BaseOptimizer(Map<String, LLMCall> parameters) {
        this.parameters = new HashMap<>();
        this.history = new OptimizeHistory();
        this.badCases = new ArrayList<>();
        bindParameter(parameters);
    }

    /**
     * Creates a BaseOptimizer without initial parameters.
     * 
     * @since 0.1.7
     */
    public BaseOptimizer() {
        this(null);
    }

    /**
     * Binds parameters to the optimizer.
     * 
     * @param params the LLM call parameters
     * @since 0.1.7
     */
    public void bindParameter(Map<String, LLMCall> params) {
        if (params == null) {
            return;
        }
        for (Map.Entry<String, LLMCall> entry : params.entrySet()) {
            String name = entry.getKey();
            LLMCall llmCall = entry.getValue();
            if (llmCall == null) {
                throw new IllegalArgumentException("Cannot bind a None parameter of " + name);
            }
            this.parameters.put(name, new TextualParameter(llmCall));
        }
        this.history.clearHistory();
        this.badCases = new ArrayList<>();
    }

    /**
     * Performs backward pass to analyze evaluated cases.
     * 
     * @param evaluatedCases the evaluated cases
     * @since 0.1.7
     */
    public void backward(List<EvaluatedCase> evaluatedCases) {
        validateParameters();
        getBadCases(evaluatedCases);
        try {
            doBackward(evaluatedCases);
        } catch (Exception e) {
            throw new RuntimeException("Backward execution error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the prompts based on gradients.
     * 
     * @since 0.1.7
     */
    public void update() {
        validateParameters();
        try {
            doUpdate();
            for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
                String name = entry.getKey();
                TextualParameter param = entry.getValue();
                Loggers.AGENT.info("LLM call '{}' prompt updated", name);
            }
            history.clearHistory();
        } catch (Exception e) {
            history.clearHistory();
            throw new RuntimeException("Update execution error: " + e.getMessage(), e);
        }
    }

    /**
     * Internal backward implementation.
     * 
     * @param evaluatedCases the evaluated cases
     * @since 0.1.7
     */
    protected abstract void doBackward(List<EvaluatedCase> evaluatedCases);

    /**
     * Internal update implementation.
     * 
     * @since 0.1.7
     */
    protected abstract void doUpdate();

    /**
     * Returns the parameters.
     * 
     * @return the parameter map
     * @since 0.1.7
     */
    public Map<String, TextualParameter> getParameters() {
        return parameters;
    }

    /**
     * Trace callback for LLM calls.
     * 
     * @param llmCallId the LLM call ID
     * @param nodeInput the node input
     * @param output the output message
     * @param session the session
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Void> traceCallback(String llmCallId, Map<String, Object> nodeInput, BaseMessage output,
            Session session) {
        return OpenJiuwenExecutors.runBackgroundAsync(() -> {
            TraceNode traceNode = new TraceNode();
            traceNode.setCaseId(session.getSessionId());
            traceNode.setLlmCallId(llmCallId);
            traceNode.setInputs(nodeInput);
            traceNode.setOutputs(TuneUtils.getOutputStringFromMessage(output));

            history.addHistory(session.getSessionId(), traceNode);
        });
    }

    /**
     * Gets bad cases from evaluated cases.
     * 
     * @param evaluatedCases the evaluated cases
     * @return the bad cases
     * @since 0.1.7
     */
    protected List<EvaluatedCase> getBadCases(List<EvaluatedCase> evaluatedCases) {
        badCases = evaluatedCases.stream().filter(c -> c.getScore() == 0.0f).toList();
        return badCases;
    }

    /**
     * Validates that parameters are set.
     * 
     * @since 0.1.7
     */
    protected void validateParameters() {
        if (parameters.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR, "error_msg",
                    "cannot optimize empty parameters");
        }
    }

    /**
     * batchSetOptimizerCallback.
     * 
     * @param callback callback
     * @since 0.1.7
     */
    private void batchSetOptimizerCallback(java.util.function.Function<TraceNode, CompletableFuture<Void>> callback) {
    }

    /**
     * close.
     * 
     * @since 0.1.7
     */
    @Override
    public void close() {
        batchSetOptimizerCallback(null);
    }
}
