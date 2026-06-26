/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.utils;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's module helpers in
 * {@code openjiuwen/core/retrieval/utils/api_requests.py}.
 */
public final class ApiRequests {

    public static final String ERROR_TEMPLATE = "%s Service Error%s: %s";
    public static final List<String> SUPPORTED_TASKS = List.of("Reranker", "Embedding");
    public static final Map<Integer, CallbackFunction> HANDLE_ERR_CODE = Map.of(
            429, ApiRequests::defaultErrorHandling,
            500, ApiRequests::defaultErrorHandling,
            503, ApiRequests::defaultErrorHandling
    );

    private static final LoggerProtocol LOGGER = Loggers.RETRIEVAL;
    private static final Random RANDOM = new Random();

    private ApiRequests() {
    }

    public static HandleResult defaultErrorHandling(ResponseAdapter response, int attempt, boolean shouldRetry) {
        return new HandleResult(attempt, true, null);
    }

    public static Object syncRequestWithRetry(SyncPostClient client, Map<String, Object> kwargs) {
        return syncRequestWithRetry(client, 3, 0.1d, HANDLE_ERR_CODE, "Reranker", kwargs);
    }

    public static Object syncRequestWithRetry(SyncPostClient client,
                                              int maxRetries,
                                              double retryWait,
                                              Map<Integer, CallbackFunction> customCallback,
                                              String task,
                                              Map<String, Object> kwargs) {
        validateTask(task);
        int attempt = 0;
        boolean shouldRetry = false;
        String respStr = "No request sent";
        ResponseAdapter response = null;
        Exception lastError = null;

        for (int backoff = 1; backoff <= maxRetries; backoff++) {
            if (shouldRetry) {
                sleep(retryWait, backoff);
                shouldRetry = false;
            }
            try {
                response = client.post(kwargs);
                ResponsePayload payload = handleResponse(response);
                HandleResult result = handleResponseByStatus(
                        response,
                        attempt,
                        shouldRetry,
                        payload.respJson(),
                        payload.respStr(),
                        task,
                        customCallback
                );
                attempt = result.attempt();
                shouldRetry = result.shouldRetry();
                respStr = payload.respStr();
                if (result.result() != null) {
                    return result.result();
                }
            } catch (Exception exception) {
                respStr = exception.toString();
                lastError = exception;
                shouldRetry = true;
            }
        }

        throw raiseErrors(task, maxRetries, respStr, response, lastError);
    }

    public static CompletableFuture<Object> asyncRequestWithRetry(AsyncPostClient client, Map<String, Object> kwargs) {
        return asyncRequestWithRetry(client, 3, 0.1d, HANDLE_ERR_CODE, "Reranker", kwargs);
    }

    public static CompletableFuture<Object> asyncRequestWithRetry(AsyncPostClient client,
                                                                  int maxRetries,
                                                                  double retryWait,
                                                                  Map<Integer, CallbackFunction> customCallback,
                                                                  String task,
                                                                  Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            validateTask(task);
            int attempt = 0;
            boolean shouldRetry = false;
            String respStr = "No request sent";
            ResponseAdapter response = null;
            Exception lastError = null;

            for (int backoff = 1; backoff <= maxRetries; backoff++) {
                if (shouldRetry) {
                    sleep(retryWait, backoff);
                    shouldRetry = false;
                }
                try {
                    response = client.post(kwargs).join();
                    ResponsePayload payload = handleResponse(response);
                    HandleResult result = handleResponseByStatus(
                            response,
                            attempt,
                            shouldRetry,
                            payload.respJson(),
                            payload.respStr(),
                            task,
                            customCallback
                    );
                    attempt = result.attempt();
                    shouldRetry = result.shouldRetry();
                    respStr = payload.respStr();
                    if (result.result() != null) {
                        return result.result();
                    }
                } catch (Exception exception) {
                    respStr = exception.toString();
                    lastError = exception;
                    shouldRetry = true;
                }
            }

            throw raiseErrors(task, maxRetries, respStr, response, lastError);
        });
    }

    static ResponsePayload handleResponse(ResponseAdapter response) {
        if (response == null || response.text() == null || response.text().trim().isEmpty()) {
            throw new IllegalArgumentException("Empty response");
        }
        return new ResponsePayload(response.json(), "response.text=" + response.text());
    }

    static HandleResult handleResponseByStatus(ResponseAdapter response,
                                               int attempt,
                                               boolean shouldRetry,
                                               Object respJson,
                                               String respStr,
                                               String task,
                                               Map<Integer, CallbackFunction> customCallback) {
        int statusCode = response.statusCode();
        if (statusCode == 200) {
            return new HandleResult(attempt, shouldRetry, respJson);
        }
        if (statusCode == 400) {
            int nextAttempt = attempt + 1;
            String attemptStr = " (attempt=" + nextAttempt + ")";
            LOGGER.error(ERROR_TEMPLATE, task, attemptStr, respStr);
            Object errorPayload = respJson;
            if (respJson instanceof Map<?, ?> map && map.get("error") != null) {
                errorPayload = map.get("error");
            }
            String errorCode = stringifyErrorCode(errorPayload).toLowerCase();
            if (errorCode.contains("safety")
                    || errorCode.contains("violation")
                    || errorCode.contains("policy")
                    || errorCode.contains("inspection")
                    || errorCode.contains("appropriate")) {
                LOGGER.warning("Reranker request may contain censored content");
            }
            return new HandleResult(nextAttempt, shouldRetry, null);
        }
        CallbackFunction callback = customCallback.getOrDefault(statusCode, ApiRequests::defaultErrorHandling);
        return callback.apply(response, attempt, shouldRetry);
    }

    static BaseError raiseErrors(String task,
                                 int maxRetries,
                                 String respStr,
                                 ResponseAdapter response,
                                 Exception lastError) {
        LOGGER.error(ERROR_TEMPLATE, task, "", respStr);
        Exception finalCause = lastError;
        if (response != null) {
            try {
                response.raiseForStatus();
            } catch (Exception exception) {
                finalCause = exception;
            }
        } else {
            finalCause = buildTaskError(
                    unreachableStatus(task),
                    "Failed to get " + task + " after " + maxRetries + " attempts",
                    null
            );
        }

        return buildTaskError(
                requestFailedStatus(task),
                "Failed to get " + task + " after " + maxRetries + " attempts",
                finalCause
        );
    }

    static void validateTask(String task) {
        if (!SUPPORTED_TASKS.contains(task)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_UTILS_CONFIG_NOT_FOUND,
                    null,
                    null,
                    null,
                    Map.of("error_msg", "Unsupported task in retrieval_api_requests: " + task + ", SUPPORTED_TASKS=" + SUPPORTED_TASKS)
            );
        }
    }

    private static void sleep(double retryWait, int backoff) {
        long millis = Math.max(0L, Math.round(RANDOM.nextDouble() * retryWait * backoff * 1000.0d));
        if (millis == 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static BaseError buildTaskError(StatusCode status, String errorMessage, Throwable cause) {
        return ErrorHelper.buildError(
                status,
                null,
                null,
                cause,
                Map.of("error_msg", errorMessage)
        );
    }

    private static StatusCode requestFailedStatus(String task) {
        return switch (task) {
            case "Embedding" -> StatusCode.RETRIEVAL_EMBEDDING_REQUEST_CALL_FAILED;
            case "Reranker" -> StatusCode.RETRIEVAL_RERANKER_REQUEST_CALL_FAILED;
            default -> throw new IllegalArgumentException("Unsupported task: " + task);
        };
    }

    private static StatusCode unreachableStatus(String task) {
        return switch (task) {
            case "Embedding" -> StatusCode.RETRIEVAL_EMBEDDING_UNREACHABLE_CALL_FAILED;
            case "Reranker" -> StatusCode.RETRIEVAL_RERANKER_UNREACHABLE_CALL_FAILED;
            default -> throw new IllegalArgumentException("Unsupported task: " + task);
        };
    }

    private static String stringifyErrorCode(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return String.valueOf(payload);
        }
        Object code = map.containsKey("code") ? map.get("code") : "";
        Object message = map.containsKey("message") ? map.get("message") : "";
        Object content = map.containsKey("content") ? map.get("content") : "";
        return String.valueOf(code) + message + content;
    }

    public interface SyncPostClient {
        ResponseAdapter post(Map<String, Object> kwargs) throws Exception;
    }

    public interface AsyncPostClient {
        CompletableFuture<ResponseAdapter> post(Map<String, Object> kwargs);
    }

    public interface ResponseAdapter {
        int statusCode();

        String text();

        Object json();

        void raiseForStatus() throws Exception;
    }

    @FunctionalInterface
    public interface CallbackFunction {
        HandleResult apply(ResponseAdapter response, int attempt, boolean shouldRetry);
    }

    record ResponsePayload(Object respJson, String respStr) {
    }

    public record HandleResult(int attempt, boolean shouldRetry, Object result) {
    }
}
