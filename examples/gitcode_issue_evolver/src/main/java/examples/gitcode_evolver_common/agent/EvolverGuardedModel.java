/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_evolver_common.agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.io.Serial;
import java.util.Collection;
import java.util.Map;

/**
 * Applies a finite request timeout and rejects empty provider responses.
 *
 * @since 0.1.13
 */
public class EvolverGuardedModel extends Model {
    /**
     * Create a guarded model.
     *
     * @param client model client configuration
     * @param request model request configuration
     */
    public EvolverGuardedModel(ModelClientConfig client, ModelRequestConfig request) {
        super(client, request);
    }

    @Override
    public AssistantMessage invoke(Object messages, Object tools, Float temperature,
                                   Float topP, String model, Integer maxTokens,
                                   String stop, BaseOutputParser outputParser,
                                   Float timeout, Map<String, Object> kwargs) throws Exception {
        Float effectiveTimeout = timeout == null
                ? (float) EvolverAgentHarness.MODEL_TIMEOUT_SECONDS : timeout;
        AssistantMessage response = super.invoke(messages, tools, temperature, topP,
                model, maxTokens, stop, outputParser, effectiveTimeout, kwargs);
        if (isEmptyResponse(response)) {
            throw new EmptyModelResponseException();
        }
        return response;
    }

    /**
     * Determine whether a provider response contains neither text nor tool calls.
     *
     * @param response provider response
     * @return true when the response is empty
     */
    public static boolean isEmptyResponse(AssistantMessage response) {
        if (response == null) {
            return true;
        }
        if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
            return false;
        }
        Object content = response.getContent();
        if (content == null) {
            return true;
        }
        if (content instanceof String text) {
            return text.isBlank();
        }
        return content instanceof Collection<?> collection && collection.isEmpty();
    }

    /** Provider returned neither text nor tool calls. */
    public static class EmptyModelResponseException extends Exception {
        @Serial
        private static final long serialVersionUID = 1L;

        /** Create an empty response failure. */
        public EmptyModelResponseException() {
            super("Model returned no content or tool calls");
        }
    }
}
