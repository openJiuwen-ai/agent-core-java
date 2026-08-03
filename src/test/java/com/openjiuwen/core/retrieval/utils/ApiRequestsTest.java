/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.utils;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's tests for
 * {@code openjiuwen/core/retrieval/utils/api_requests.py}.
 */
class ApiRequestsTest {

    @Test
    void syncRequestWithRetryReturnsJsonOnSuccess() {
        RecordingSyncClient client = new RecordingSyncClient(new StubResponse(200, "{\"ok\":true}", Map.of("ok", true), null));

        Object result = ApiRequests.syncRequestWithRetry(client, Map.of("url", "https://example.com"));

        assertEquals(Map.of("ok", true), result);
    }

    @Test
    void syncRequestWithRetryUsesCallbackForRetryableStatuses() {
        RecordingSyncClient client = new RecordingSyncClient(
                new StubResponse(503, "{\"error\":\"busy\"}", Map.of("error", "busy"), null),
                new StubResponse(200, "{\"ok\":true}", Map.of("ok", true), null)
        );

        Object result = ApiRequests.syncRequestWithRetry(
                client,
                2,
                0.0d,
                ApiRequests.HANDLE_ERR_CODE,
                "Reranker",
                Map.of("url", "https://example.com")
        );

        assertEquals(Map.of("ok", true), result);
        assertEquals(2, client.callCount);
    }

    @Test
    void asyncRequestWithRetryRecoversAfterTransportFailure() {
        RecordingAsyncClient client = new RecordingAsyncClient(
                CompletableFuture.failedFuture(new IllegalStateException("network down")),
                CompletableFuture.completedFuture(new StubResponse(200, "{\"value\":1}", Map.of("value", 1), null))
        );

        Object result = ApiRequests.asyncRequestWithRetry(
                client,
                2,
                0.0d,
                ApiRequests.HANDLE_ERR_CODE,
                "Embedding",
                Map.of("url", "https://example.com")
        ).join();

        assertEquals(Map.of("value", 1), result);
    }

    @Test
    void syncRequestWithRetryRaisesConfiguredRequestErrorAfterExhaustion() {
        RecordingSyncClient client = new RecordingSyncClient(
                new StubResponse(500, "{\"error\":\"boom\"}", Map.of("error", "boom"), new IllegalStateException("HTTP 500"))
        );

        BaseError error = assertThrows(
                BaseError.class,
                () -> ApiRequests.syncRequestWithRetry(client, 1, 0.0d, ApiRequests.HANDLE_ERR_CODE, "Reranker", Map.of())
        );

        assertEquals(StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED, error.getStatus());
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test
    void validateTaskRejectsUnsupportedValues() {
        BaseError error = assertThrows(BaseError.class, () -> ApiRequests.validateTask("Search"));

        assertEquals(StatusCode.RETRIEVAL_UTILS_CONFIG_NOT_FOUND, error.getStatus());
    }

    private static final class RecordingSyncClient implements ApiRequests.SyncPostClient {

        private final Deque<StubResponse> responses = new ArrayDeque<>();
        private int callCount;

        private RecordingSyncClient(StubResponse... responses) {
            this.responses.addAll(java.util.List.of(responses));
        }

        @Override
        public ApiRequests.ResponseAdapter post(Map<String, Object> kwargs) {
            callCount++;
            return responses.removeFirst();
        }
    }

    private static final class RecordingAsyncClient implements ApiRequests.AsyncPostClient {

        private final Deque<CompletableFuture<ApiRequests.ResponseAdapter>> responses = new ArrayDeque<>();

        @SafeVarargs
        private RecordingAsyncClient(CompletableFuture<ApiRequests.ResponseAdapter>... responses) {
            this.responses.addAll(java.util.List.of(responses));
        }

        @Override
        public CompletableFuture<ApiRequests.ResponseAdapter> post(Map<String, Object> kwargs) {
            return responses.removeFirst();
        }
    }

    private record StubResponse(int statusCode, String text, Object json, Exception raiseError)
            implements ApiRequests.ResponseAdapter {

        @Override
        public void raiseForStatus() throws Exception {
            if (raiseError != null) {
                throw raiseError;
            }
        }
    }
}
