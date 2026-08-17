/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.io.Serial;
import java.util.Collection;
import java.util.Map;

/**
 * Applies a finite request timeout and turns empty provider responses into retryable failures.
 *
 * @since 0.1.13
 */
final class FeatureGuardedModel extends Model {
    FeatureGuardedModel(ModelClientConfig client, ModelRequestConfig request) {
        super(client, request);
    }

    @Override
    public AssistantMessage invoke(Object messages, Object tools, Float temperature,
                                   Float topP, String model, Integer maxTokens,
                                   String stop, BaseOutputParser outputParser,
                                   Float timeout, Map<String, Object> kwargs) throws Exception {
        Float effectiveTimeout = timeout;
        if (effectiveTimeout == null) {
            effectiveTimeout = (float) FeatureAgentHarness.MODEL_TIMEOUT_SECONDS;
        }
        AssistantMessage response = super.invoke(messages, tools, temperature, topP,
                model, maxTokens, stop, outputParser, effectiveTimeout, kwargs);
        if (isEmptyResponse(response)) {
            throw new EmptyModelResponseException();
        }
        return response;
    }

    static boolean isEmptyResponse(AssistantMessage response) {
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
    static final class EmptyModelResponseException extends Exception {
        @Serial
        private static final long serialVersionUID = 1L;

        EmptyModelResponseException() {
            super("Model returned no content or tool calls");
        }
    }
}
