/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.classification.ModelCallClassification;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorCategory;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Builds the tiered timeout error surface ({@code MODEL_CALL_TIMEOUT}) with the
 * three-segment phase taxonomy in both the rendered message and the programmatic details map.
 *
 * @since 2026-10-09
 */
final class ModelTimeoutErrorFactory {
    private static final String LAYER_CONNECT = "connect";
    private static final String LAYER_HEADER_WAIT = "header_wait";
    private static final String LAYER_STREAM_IDLE = "stream_idle";
    private static final int MAX_CHAIN_DEPTH = 32;

    private ModelTimeoutErrorFactory() {
    }

    /**
     * Classifies a send-stage failure. Connect timeouts are matched before the parent
     * {@link HttpTimeoutException} because {@link HttpConnectTimeoutException} extends it;
     * reversing the order would mislabel connect failures as header-wait timeouts.
     *
     * @param error failure thrown by the send stage
     * @param isStreaming true for streaming calls
     * @param config model client config for effective timeout values
     * @return the tiered timeout error, or empty when the failure is not a timeout
     */
    static Optional<BaseError> fromSendException(Throwable error, boolean isStreaming,
            ModelClientConfig config) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof HttpConnectTimeoutException) {
                return Optional.of(timeoutError(TimeoutPhase.BEFORE_CONNECT, LAYER_CONNECT,
                        ModelTimeoutResolver.resolveConnectTimeoutSeconds(config), isStreaming, error));
            }
            if (cursor instanceof HttpTimeoutException) {
                return Optional.of(timeoutError(TimeoutPhase.BEFORE_FIRST_SEGMENT, LAYER_HEADER_WAIT,
                        ModelTimeoutResolver.resolveHeaderWaitSeconds(config), isStreaming, error));
            }
            cursor = cursor.getCause();
        }
        return Optional.empty();
    }

    /**
     * Builds the idle (peer silence) timeout error raised by the watchdog path.
     *
     * @param phase BEFORE_FIRST_SEGMENT or AFTER_FIRST_SEGMENT
     * @param timeoutSeconds effective idle window in seconds
     * @param isStreaming true for streaming calls
     * @param cause underlying failure, may be null
     * @return the tiered timeout error
     */
    static BaseError idleTimeoutError(TimeoutPhase phase, double timeoutSeconds, boolean isStreaming,
            Throwable cause) {
        return timeoutError(phase, LAYER_STREAM_IDLE, timeoutSeconds, isStreaming, cause);
    }

    /**
     * Classifies a timeout raised by a delegated transport (for example an IntelliRouter
     * driver). JDK send-stage failures reuse the send-stage mapping; idle markers keep their
     * measured window; other socket-level timeouts surface as BEFORE_FIRST_SEGMENT/header_wait
     * because a delegated transport does not expose finer phase information.
     *
     * @param error failure thrown by the delegated transport
     * @param isStreaming true for streaming calls
     * @param config model client config for effective timeout values
     * @return the tiered timeout error, or empty when the failure is not a timeout
     */
    static Optional<BaseError> fromDelegatedTransportException(Throwable error, boolean isStreaming,
            ModelClientConfig config) {
        Optional<BaseError> sendStage = fromSendException(error, isStreaming, config);
        if (sendStage.isPresent()) {
            return sendStage;
        }
        Throwable cursor = error;
        int depth = 0;
        while (cursor != null && depth < MAX_CHAIN_DEPTH) {
            if (cursor instanceof IdleTimeoutIOException idleTimeout) {
                return Optional.of(idleTimeoutError(TimeoutPhase.BEFORE_FIRST_SEGMENT,
                        idleTimeout.idleSeconds(), isStreaming, error));
            }
            if (cursor instanceof SocketTimeoutException || cursor instanceof TimeoutException) {
                return Optional.of(timeoutError(TimeoutPhase.BEFORE_FIRST_SEGMENT, LAYER_HEADER_WAIT,
                        ModelTimeoutResolver.resolveHeaderWaitSeconds(config), isStreaming, error));
            }
            cursor = cursor.getCause();
            depth++;
        }
        return Optional.empty();
    }

    private static BaseError timeoutError(TimeoutPhase phase, String layer, double timeoutSeconds,
            boolean isStreaming, Throwable cause) {
        String message = layer + " timeout after " + timeoutSeconds + "s";
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("phase", phase.name());
        details.put("timeout_seconds", timeoutSeconds);
        details.put("layer", layer);
        details.put("stream", isStreaming);
        details.put(ModelCallClassification.DETAIL_CATEGORY, ModelErrorCategory.TEMPORARY);
        details.put(ModelCallClassification.DETAIL_RETRYABLE, Boolean.TRUE);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("phase", phase.name());
        params.put("timeout", String.valueOf(timeoutSeconds));
        params.put("error_msg", message);
        return ErrorHelper.buildError(StatusCode.MODEL_CALL_TIMEOUT, null, details, cause, params);
    }
}
