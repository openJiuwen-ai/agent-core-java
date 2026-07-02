/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import com.openjiuwen.core.common.VirtualThreadSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal;
import com.openjiuwen.core.sys_operation.result.BaseResult;
import com.openjiuwen.core.sys_operation.result.FileSystemItem;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Consumer;

/**
 * Mirrors Python's helper functions in
 * {@code openjiuwen/extensions/sys_operation/sandbox/providers/aio.py}.
 */
final class AioProviderSupport {

    static final ObjectMapper JSON = new ObjectMapper();
    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    static final int DEFAULT_PROVIDER_TIMEOUT_SECONDS = 30;
    static final String STDERR_PREFIX = "__OJW_STDERR__:";

    private AioProviderSupport() {
    }

    static int resolveProviderTimeoutSeconds(SandboxGatewayConfig config) {
        if (config == null || config.getTimeoutSeconds() <= 0) {
            return DEFAULT_PROVIDER_TIMEOUT_SECONDS;
        }
        return config.getTimeoutSeconds();
    }

    static <R> R buildFsErrorResult(String execution, String errorMessage, Class<R> resultClass) {
        return buildFsErrorResult(execution, errorMessage, resultClass, null);
    }

    static <T, R> R buildFsErrorResult(String execution, String errorMessage, Class<R> resultClass, T data) {
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR,
                messageArgs(execution, errorMessage),
                resultClass,
                data);
    }

    static <R> R buildShellErrorResult(String execution, String errorMessage, Class<R> resultClass) {
        return buildShellErrorResult(execution, errorMessage, resultClass, null);
    }

    static <T, R> R buildShellErrorResult(String execution, String errorMessage, Class<R> resultClass, T data) {
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
                messageArgs(execution, errorMessage),
                resultClass,
                data);
    }

    static <R> R buildCodeErrorResult(String execution, String errorMessage, Class<R> resultClass) {
        return buildCodeErrorResult(execution, errorMessage, resultClass, null);
    }

    static <T, R> R buildCodeErrorResult(String execution, String errorMessage, Class<R> resultClass, T data) {
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR,
                messageArgs(execution, errorMessage),
                resultClass,
                data);
    }

    private static Map<String, Object> messageArgs(String execution, String errorMessage) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("execution", execution == null ? "" : execution);
        args.put("error_msg", errorMessage == null ? "" : errorMessage);
        return args;
    }

    static NormalizedReadParams normalizeReadParams(
            Integer head,
            Integer tail,
            BaseFsProtocal.LineRange lineRange) {
        return new NormalizedReadParams(
                Objects.equals(head, 0) ? null : head,
                Objects.equals(tail, 0) ? null : tail,
                lineRange);
    }

    static Optional<String> validateReadParams(
            String mode,
            Integer head,
            Integer tail,
            BaseFsProtocal.LineRange lineRange) {
        if (BaseFsProtocal.MODE_BYTES.equals(mode)
                && (head != null || tail != null || lineRange != null)) {
            return Optional.of("Parameters 'head', 'tail', and 'line_range' are only supported in text mode");
        }
        List<String> specified = new ArrayList<>();
        if (head != null) {
            specified.add("head");
        }
        if (tail != null) {
            specified.add("tail");
        }
        if (lineRange != null) {
            specified.add("line_range");
        }
        if (specified.size() > 1) {
            return Optional.of(String.join(" and ", specified) + " cannot be specified simultaneously");
        }
        return Optional.empty();
    }

    static SelectedText selectTextLines(
            String content,
            Integer head,
            Integer tail,
            BaseFsProtocal.LineRange lineRange) {
        List<String> lines = splitLines(content);
        if (tail != null) {
            if (tail < 0) {
                return new SelectedText(List.of(), true);
            }
            return new SelectedText(tail == 0 ? lines : sliceTail(lines, tail), false);
        }
        if (head != null) {
            if (head < 0) {
                return new SelectedText(List.of(), true);
            }
            return new SelectedText(lines.subList(0, Math.min(lines.size(), head)), false);
        }
        if (lineRange != null) {
            int start = lineRange.startLine();
            int end = lineRange.endLine();
            if (start <= 0 || end <= 0 || start > end) {
                return new SelectedText(List.of(), true);
            }
            if (lines.isEmpty()) {
                return new SelectedText(List.of(), false);
            }
            int startIndex = start - 1;
            int endIndex = Math.min(lines.size(), end);
            if (startIndex >= lines.size() || endIndex <= startIndex) {
                return new SelectedText(List.of(), false);
            }
            return new SelectedText(lines.subList(startIndex, endIndex), false);
        }
        return new SelectedText(lines, false);
    }

    private static List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < content.length(); index++) {
            if (content.charAt(index) == '\n') {
                lines.add(content.substring(start, index + 1));
                start = index + 1;
            }
        }
        if (start < content.length()) {
            lines.add(content.substring(start));
        }
        return lines;
    }

    private static List<String> sliceTail(List<String> lines, int tail) {
        if (tail <= 0) {
            return lines;
        }
        int fromIndex = Math.max(0, lines.size() - tail);
        return lines.subList(fromIndex, lines.size());
    }

    static String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line);
        }
        return builder.toString();
    }

    static List<FileSystemItem> sortFsItems(List<FileSystemItem> items, String sortBy, boolean sortDescending) {
        Comparator<FileSystemItem> comparator;
        if (BaseFsProtocal.SORT_BY_MODIFIED_TIME.equals(sortBy)) {
            comparator = Comparator.comparing(item -> item.getModifiedTime() == null ? "" : item.getModifiedTime());
        } else if (BaseFsProtocal.SORT_BY_SIZE.equals(sortBy)) {
            comparator = Comparator.comparingInt(FileSystemItem::getSize);
        } else {
            comparator = Comparator.comparing(item -> item.getName() == null ? "" : item.getName());
        }
        if (sortDescending) {
            comparator = comparator.reversed();
        }
        return items.stream().sorted(comparator).toList();
    }

    static String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? null : fileName.substring(dot);
    }

    static String quoteShellValue(String value) {
        if (value == null || value.isEmpty()) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    static SplitOutput splitMarkedShellOutput(String output) {
        if (output == null || output.isEmpty()) {
            return new SplitOutput("", "");
        }
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        int start = 0;
        while (start < output.length()) {
            int lineEnd = output.indexOf('\n', start);
            String line;
            boolean hasNewline = lineEnd >= 0;
            if (hasNewline) {
                line = output.substring(start, lineEnd + 1);
                start = lineEnd + 1;
            } else {
                line = output.substring(start);
                start = output.length();
            }
            if (line.startsWith(STDERR_PREFIX)) {
                stderr.append(line.substring(STDERR_PREFIX.length()));
            } else {
                stdout.append(line);
            }
        }
        return new SplitOutput(stdout.toString(), stderr.toString());
    }

    static boolean isRetryableError(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof AioHttpException httpException) {
                return httpException.getStatusCode() == 502 || httpException.getStatusCode() == 503;
            }
            String message = cursor.getMessage();
            if (message != null && (message.contains("502") || message.contains("503"))) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    static <T> T withRetry(int timeoutSeconds, RetryableCall<T> call) throws Exception {
        long deadline = System.currentTimeMillis() + Math.max(1L, timeoutSeconds) * 1000L;
        long delayMillis = 500L;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                return call.call();
            } catch (Exception exception) {
                last = exception;
                if (!isRetryableError(exception)) {
                    throw exception;
                }
                if (System.currentTimeMillis() + delayMillis >= deadline) {
                    break;
                }
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw interruptedException;
                }
                delayMillis = Math.min(delayMillis * 2L, 2_000L);
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IOException("aio operation timeout");
    }

    static <T> Flow.Publisher<T> asyncPublisher(Consumer<SubmissionPublisher<T>> emitter) {
        SubmissionPublisher<T> publisher = new SubmissionPublisher<>();
        VirtualThreadSupport.startThread("aio-provider-publisher", () -> {
            try {
                emitter.accept(publisher);
                publisher.close();
            } catch (Throwable throwable) {
                publisher.closeExceptionally(throwable);
            }
        });
        return publisher;
    }

    @FunctionalInterface
    interface RetryableCall<T> {
        T call() throws Exception;
    }

    static final class AioHttpClient {

        private final SandboxEndpoint endpoint;
        private final SandboxGatewayConfig config;
        private final HttpClient httpClient;
        private final Duration requestTimeout;

        AioHttpClient(SandboxEndpoint endpoint, SandboxGatewayConfig config, int timeoutSeconds) {
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
            this.config = config;
            if (endpoint.baseUrl() == null || endpoint.baseUrl().isBlank()) {
                throw new IllegalArgumentException("AIO provider requires endpoint.base_url");
            }
            this.requestTimeout = Duration.ofSeconds(Math.max(1L, timeoutSeconds));
            this.httpClient = HttpClient.newBuilder().connectTimeout(DEFAULT_CONNECT_TIMEOUT).build();
        }

        JsonNode postJson(String path, Map<String, Object> payload) throws IOException, InterruptedException {
            String body = toJson(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder(resolveUri(path))
                    .timeout(requestTimeout)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            applyAuthHeaders(builder);
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), response.body(), path);
            return parseJson(response.body(), path);
        }

        byte[] getBytes(String path, Map<String, String> queryParams) throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(resolveUri(path, queryParams))
                    .timeout(requestTimeout)
                    .GET();
            applyAuthHeaders(builder);
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8), path);
            return response.body();
        }

        JsonNode uploadFile(String path, Path localFile) throws IOException, InterruptedException {
            String boundary = "----openjiuwen-aio-" + System.nanoTime();
            byte[] fileBytes = java.nio.file.Files.readAllBytes(localFile);
            String fileName = localFile.getFileName() == null ? "upload.bin" : localFile.getFileName().toString();
            List<byte[]> parts = new ArrayList<>();
            parts.add(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"path\"\r\n\r\n"
                    + path + "\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(fileBytes);
            parts.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest.Builder builder = HttpRequest.newBuilder(resolveUri("v1/file/upload"))
                    .timeout(requestTimeout)
                    .header("content-type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(parts));
            applyAuthHeaders(builder);
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), response.body(), "v1/file/upload");
            return parseJson(response.body(), "v1/file/upload");
        }

        private URI resolveUri(String path) {
            return resolveUri(path, Map.of());
        }

        private URI resolveUri(String path, Map<String, String> queryParams) {
            String normalizedBase = endpoint.baseUrl().replaceAll("/+$", "");
            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            Map<String, String> finalQuery = new LinkedHashMap<>();
            if (config != null && config.getAuthQueryParams() != null) {
                finalQuery.putAll(config.getAuthQueryParams());
            }
            if (queryParams != null) {
                finalQuery.putAll(queryParams);
            }
            if (finalQuery.isEmpty()) {
                return URI.create(normalizedBase + normalizedPath);
            }
            StringBuilder query = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : finalQuery.entrySet()) {
                if (!first) {
                    query.append('&');
                }
                first = false;
                query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
            }
            return URI.create(normalizedBase + normalizedPath + "?" + query);
        }

        private void applyAuthHeaders(HttpRequest.Builder builder) {
            if (config == null || config.getAuthHeaders() == null) {
                return;
            }
            config.getAuthHeaders().forEach(builder::header);
        }

        private static JsonNode parseJson(String body, String path) throws IOException {
            try {
                return JSON.readTree(body == null ? "{}" : body);
            } catch (JsonProcessingException exception) {
                throw new IOException("Invalid JSON response from " + path + ": " + body, exception);
            }
        }

        private static void ensureSuccess(int statusCode, String body, String path) throws AioHttpException {
            if (statusCode >= 200 && statusCode < 300) {
                return;
            }
            throw new AioHttpException(statusCode, "HTTP " + statusCode + " for " + path + ": " + body);
        }

        private static String toJson(Map<String, Object> payload) throws JsonProcessingException {
            return JSON.writeValueAsString(payload == null ? Map.of() : payload);
        }

        private static String urlEncode(String value) {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
        }
    }

    static final class AioHttpException extends IOException {
        private final int statusCode;

        AioHttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        int getStatusCode() {
            return statusCode;
        }
    }

    record NormalizedReadParams(Integer head, Integer tail, BaseFsProtocal.LineRange lineRange) {
    }

    record SelectedText(List<String> lines, boolean invalid) {
    }

    record SplitOutput(String stdout, String stderr) {
    }
}
