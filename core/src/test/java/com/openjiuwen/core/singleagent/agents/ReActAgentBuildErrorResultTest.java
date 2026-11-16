/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ModelError;
import com.openjiuwen.core.common.exception.StatusCode;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

/**
 * Covers the structured failure entry attached to streaming error results so remote
 * adapters can rebuild a failure descriptor without parsing the flattened output text.
 */
class ReActAgentBuildErrorResultTest {
    @Test
    void baseErrorCarriesStructuredFailureEntry() {
        ModelError error = new ModelError(StatusCode.MODEL_CALL_TIMEOUT);
        Map<String, Object> result = ReActAgent.buildErrorResult(error);
        assertThat(result.get("result_type")).isEqualTo("error");
        assertThat(result.get("output")).isInstanceOf(String.class);
        assertThat(result.get("failure")).isInstanceOf(Map.class);
        Object failure = result.get("failure");
        assertThat(readFailureField(failure, "code")).isEqualTo("MODEL_CALL_TIMEOUT");
        assertThat(readFailureField(failure, "numericCode")).isEqualTo(181008);
        assertThat(readFailureField(failure, "retryable")).isEqualTo(true);
    }

    @Test
    void wrappedBaseErrorIsFoundThroughCauseChain() {
        ModelError error = new ModelError(StatusCode.MODEL_CALL_FAILED);
        RuntimeException wrapped = new IllegalStateException("stream failed", error);
        Map<String, Object> result = ReActAgent.buildErrorResult(wrapped);
        assertThat(readFailureField(result.get("failure"), "code")).isEqualTo("MODEL_CALL_FAILED");
        assertThat(readFailureField(result.get("failure"), "numericCode")).isEqualTo(181001);
    }

    @Test
    void plainRuntimeExceptionHasNoFailureEntry() {
        Map<String, Object> result = ReActAgent.buildErrorResult(new IllegalStateException("boom"));
        assertThat(result.get("output")).isEqualTo("IllegalStateException: boom");
        assertThat(result.containsKey("failure")).isFalse();
    }

    @Test
    void answerPayloadPassesFailureEntryThrough() {
        Map<String, Object> result = ReActAgent.buildErrorResult(new ModelError(StatusCode.MODEL_CALL_TIMEOUT));
        Map<String, Object> payload = ReActAgent.toAnswerPayload(result);
        assertThat(payload.get("result_type")).isEqualTo("error");
        assertThat(readFailureField(payload.get("failure"), "numericCode")).isEqualTo(181008);
    }

    @Test
    void answerPayloadWithoutFailureKeepsOriginalShape() {
        Map<String, Object> payload = ReActAgent.toAnswerPayload(Map.of(
                "output", "hello",
                "result_type", "answer"
        ));
        assertThat(payload).containsOnlyKeys("output", "result_type");
    }

    @Test
    void answerPayloadDropsNonMapFailureValue() {
        Map<String, Object> payload = ReActAgent.toAnswerPayload(Map.of(
                "output", "hello",
                "result_type", "error",
                "failure", "not-a-map"
        ));
        assertThat(payload).containsOnlyKeys("output", "result_type");
    }

    @Test
    void findBaseErrorReturnsEmptyForPlainChain() {
        Optional<BaseError> found = ReActAgent.findBaseError(new IllegalStateException("boom"));
        assertThat(found).isEmpty();
    }

    private static Object readFailureField(Object failure, String key) {
        assertThat(failure).isInstanceOf(Map.class);
        Map<?, ?> failureMap = (Map<?, ?>) failure;
        return failureMap.get(key);
    }
}
