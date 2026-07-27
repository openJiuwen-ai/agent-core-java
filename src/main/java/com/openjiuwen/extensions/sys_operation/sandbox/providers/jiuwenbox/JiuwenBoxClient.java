/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OkHttp-based HTTP client that wraps the jiuwenbox REST API.
 *
 * @since 2026-01-01
 * @version 1.0
 */
public class JiuwenBoxClient {
    private static final Logger logger = LoggerFactory.getLogger(JiuwenBoxClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    // --- instance fields (separated from static fields by a blank line) ---
    private final String baseUrl;
    private final int timeoutSeconds;
    private final OkHttpClient client;

    /**
     * Constructs a JiuwenBoxClient with the given base URL and timeout.
     *
     * @param baseUrl the jiuwenBox server base URL
     * @param timeoutSeconds the request timeout in seconds
     */
    public JiuwenBoxClient(String baseUrl, int timeoutSeconds) {
        String stripped = baseUrl;
        while (stripped.endsWith("/") && stripped.length() > 1) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        this.baseUrl = stripped;
        this.timeoutSeconds = timeoutSeconds;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Creates a new sandbox and returns its ID.
     *
     * @param createOptions the sandbox creation options map (e.g., policy, policy_mode), may be null
     * @return the newly created sandbox ID string
     * @throws SandboxNotFoundException if the sandbox was not found during creation
     * @throws SandboxOperationException if the creation request fails
     */
    public String createSandbox(Map<String, Object> createOptions) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (createOptions != null) {
            for (Map.Entry<String, Object> entry : createOptions.entrySet()) {
                if (entry.getValue() != null) {
                    body.put(entry.getKey(), entry.getValue());
                }
            }
        }
        String jsonBody = toJson(body);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/sandboxes")
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = readBody(response);
            if (!response.isSuccessful()) {
                raiseForStatus(response, responseBody);
            }
            Map<String, Object> result = MAPPER.readValue(responseBody,
                    new TypeReference<Map<String, Object>>() {});
            Object idObj = result.get("id");
            if (idObj instanceof String id) {
                return id;
            }
            throw new SandboxOperationException("Invalid sandbox id in response: " + idObj);
        } catch (SandboxNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new SandboxOperationException("createSandbox request failed", e);
        }
    }

    /**
     * Sets idle timeout and check interval on the jiuwenbox server.
     *
     * @param idleTimeout the idle timeout in seconds, may be null
     * @param idleCheckInterval the idle check interval in seconds, may be null
     * @throws SandboxNotFoundException if the sandbox was not found
     * @throws SandboxOperationException if the request fails
     */
    public void setIdleTimeout(Integer idleTimeout, Integer idleCheckInterval) {
        if (idleTimeout == null && idleCheckInterval == null) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (idleTimeout != null) {
            body.put("idle_timeout", idleTimeout);
        }
        if (idleCheckInterval != null) {
            body.put("idle_check_interval", idleCheckInterval);
        }
        String jsonBody = toJson(body);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/timeout")
                .put(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = readBody(response);
            if (!response.isSuccessful()) {
                raiseForStatus(response, responseBody);
            }
        } catch (SandboxNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new SandboxOperationException("setIdleTimeout request failed", e);
        }
    }

    /**
     * Deletes a sandbox by ID. Treats 404 as success. No-op if sandboxId is null or empty.
     *
     * @param sandboxId the sandbox ID to delete
     * @throws SandboxNotFoundException if a sandbox-not-found error occurs with a different context
     * @throws SandboxOperationException if the delete request fails
     */
    public void deleteSandbox(String sandboxId) {
        if (sandboxId == null || sandboxId.isEmpty()) {
            return;
        }
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/sandboxes/" + sandboxId)
                .method("DELETE", null)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 404) {
                return;
            }
            String responseBody = readBody(response);
            if (!response.isSuccessful()) {
                raiseForStatus(response, responseBody);
            }
        } catch (SandboxNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new SandboxOperationException("deleteSandbox request failed", e);
        }
    }

    /**
     * Executes a command in a sandbox and returns the result.
     *
     * @param sandboxId the sandbox ID in which to execute the command
     * @param command the command and arguments to execute
     * @param cwd the working directory for the command, may be null
     * @param timeout the execution timeout in seconds, may be null (defaults to 30)
     * @param environment the environment variables map, may be null
     * @param stdin the stdin content to pass to the command, may be null
     * @return the execution response containing stdout, stderr, and exit code
     * @throws SandboxNotFoundException if the sandbox was not found
     * @throws SandboxOperationException if the exec request fails
     */
    public ExecResponse exec(String sandboxId, List<String> command, String cwd,
                              Integer timeout, Map<String, String> environment, String stdin) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("command", command);
        if (cwd != null) {
            body.put("workdir", cwd);
        }
        if (environment != null) {
            body.put("env", environment);
        }
        if (stdin != null) {
            body.put("stdin", stdin);
        }
        int normalizedTimeout = normalizeExecTimeout(timeout);
        body.put("timeout_seconds", normalizedTimeout);

        int httpTimeout = Math.max(normalizedTimeout, 30);
        OkHttpClient execClient = this.client.newBuilder()
                .callTimeout(httpTimeout, TimeUnit.SECONDS)
                .readTimeout(httpTimeout, TimeUnit.SECONDS)
                .build();

        String jsonBody = toJson(body);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/sandboxes/" + sandboxId + "/exec")
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build();
        try (Response response = execClient.newCall(request).execute()) {
            String responseBody = readBody(response);
            if (!response.isSuccessful()) {
                raiseForStatus(response, responseBody);
            }
            return MAPPER.readValue(responseBody, ExecResponse.class);
        } catch (SandboxNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new SandboxOperationException("exec request failed", e);
        }
    }

    /**
     * Uploads bytes to a sandbox path using multipart form-data.
     *
     * @param sandboxId the target sandbox ID
     * @param sandboxPath the destination path inside the sandbox
     * @param content the file content bytes to upload
     * @throws SandboxNotFoundException if the sandbox was not found
     * @throws SandboxOperationException if the upload request fails
     */
    public void uploadBytes(String sandboxId, String sandboxPath, byte[] content) {
        String fileName = sandboxPath.substring(sandboxPath.lastIndexOf('/') + 1);
        if (fileName.isEmpty()) {
            fileName = "upload.bin";
        }
        HttpUrl url = Objects.requireNonNull(
                HttpUrl.parse(baseUrl + "/api/v1/sandboxes/" + sandboxId + "/upload"),
                "Invalid upload URL"
        ).newBuilder()
                .addQueryParameter("sandbox_path", sandboxPath)
                .build();

        RequestBody fileBody = RequestBody.create(content, MediaType.parse("application/octet-stream"));
        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(multipartBody)
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = readBody(response);
            if (!response.isSuccessful()) {
                raiseForStatus(response, responseBody);
            }
        } catch (SandboxNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new SandboxOperationException("uploadBytes request failed", e);
        }
    }

    /**
     * Appends bytes to a sandbox file by encoding content as base64 and executing a decode command.
     *
     * @param sandboxId the target sandbox ID
     * @param sandboxPath the destination file path inside the sandbox
     * @param content the bytes to append
     * @throws SandboxOperationException if the append command fails
     */
    public void appendBytes(String sandboxId, String sandboxPath, byte[] content) {
        String encodedContent = Base64.getEncoder().encodeToString(content);
        ExecResponse result = exec(sandboxId,
                List.of("bash", "-lc",
                        "set -euo pipefail; target=\"$1\"; parent=$(dirname -- \"$target\"); "
                                + "mkdir -p -- \"$parent\"; base64 -d >> \"$target\"",
                        "jiuwenbox-append", sandboxPath),
                null, null, null, encodedContent);
        if (result.getExitCode() != 0) {
            String error = result.getStderr() != null && !result.getStderr().isEmpty()
                    ? result.getStderr() : result.getStdout();
            throw new SandboxOperationException(error != null && !error.isEmpty() ? error : "append file failed");
        }
    }

    /**
     * Downloads bytes from a sandbox path.
     *
     * @param sandboxId the source sandbox ID
     * @param sandboxPath the file path inside the sandbox to download
     * @return the downloaded file content as bytes; empty byte array if no body
     * @throws SandboxNotFoundException if the sandbox was not found
     * @throws SandboxOperationException if the download request fails
     */
    public byte[] downloadBytes(String sandboxId, String sandboxPath) {
        HttpUrl url = Objects.requireNonNull(
                HttpUrl.parse(baseUrl + "/api/v1/sandboxes/" + sandboxId + "/download"),
                "Invalid download URL"
        ).newBuilder()
                .addQueryParameter("sandbox_path", sandboxPath)
                .build();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = readBody(response);
                raiseForStatus(response, responseBody);
            }
            return response.body() != null ? response.body().bytes() : new byte[0];
        } catch (SandboxNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new SandboxOperationException("downloadBytes request failed", e);
        }
    }

    /**
     * Lists files in a sandbox directory.
     *
     * @param sandboxId the target sandbox ID
     * @param path the directory path inside the sandbox to list
     * @param isRecursive whether to list recursively
     * @param maxDepth the maximum recursion depth, may be null
     * @param isIncludeFiles whether to include files in the listing
     * @param isIncludeDirs whether to include directories in the listing
     * @return the list of file/directory metadata maps from the server response
     * @throws SandboxNotFoundException if the sandbox was not found
     * @throws SandboxOperationException if the list request fails
     */
    public List<Map<String, Object>> listFiles(String sandboxId, String path, boolean isRecursive,
                                                 Integer maxDepth, boolean isIncludeFiles,
                                                 boolean isIncludeDirs) {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(
                HttpUrl.parse(baseUrl + "/api/v1/sandboxes/" + sandboxId + "/files"),
                "Invalid files URL"
        ).newBuilder()
                .addQueryParameter("sandbox_path", path)
                .addQueryParameter("recursive", String.valueOf(isRecursive))
                .addQueryParameter("include_files", String.valueOf(isIncludeFiles))
                .addQueryParameter("include_dirs", String.valueOf(isIncludeDirs));
        if (maxDepth != null) {
            urlBuilder.addQueryParameter("max_depth", String.valueOf(maxDepth));
        }
        Request request = new Request.Builder().url(urlBuilder.build()).get().build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = readBody(response);
            if (!response.isSuccessful()) {
                raiseForStatus(response, responseBody);
            }
            return parseItems(responseBody);
        } catch (SandboxNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new SandboxOperationException("listFiles request failed", e);
        }
    }

    /**
     * Searches files in a sandbox directory by pattern.
     *
     * @param sandboxId the target sandbox ID
     * @param path the directory path inside the sandbox to search
     * @param pattern the search pattern string
     * @param excludePatterns the list of patterns to exclude, may be null
     * @return the list of matching file/directory metadata maps
     * @throws SandboxNotFoundException if the sandbox was not found
     * @throws SandboxOperationException if the search request fails
     */
    public List<Map<String, Object>> searchFiles(String sandboxId, String path, String pattern,
                                                   List<String> excludePatterns) {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(
                HttpUrl.parse(baseUrl + "/api/v1/sandboxes/" + sandboxId + "/search"),
                "Invalid search URL"
        ).newBuilder()
                .addQueryParameter("sandbox_path", path)
                .addQueryParameter("pattern", pattern);
        if (excludePatterns != null) {
            for (String ep : excludePatterns) {
                urlBuilder.addQueryParameter("exclude_patterns", ep);
            }
        }
        Request request = new Request.Builder().url(urlBuilder.build()).get().build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = readBody(response);
            if (!response.isSuccessful()) {
                raiseForStatus(response, responseBody);
            }
            return parseItems(responseBody);
        } catch (SandboxNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new SandboxOperationException("searchFiles request failed", e);
        }
    }

    /**
     * Checks if a path exists in a sandbox by listing the parent directory and matching paths.
     *
     * @param sandboxId the target sandbox ID
     * @param sandboxPath the path inside the sandbox to check
     * @return true if the path exists in the sandbox, false otherwise
     * @throws SandboxNotFoundException if the sandbox itself was not found
     * @throws SandboxOperationException if the check request fails
     */
    public boolean pathExists(String sandboxId, String sandboxPath) {
        String parent = parentPath(sandboxPath);
        HttpUrl url = Objects.requireNonNull(
                HttpUrl.parse(baseUrl + "/api/v1/sandboxes/" + sandboxId + "/files"),
                "Invalid files URL for pathExists"
        ).newBuilder()
                .addQueryParameter("sandbox_path", parent)
                .addQueryParameter("recursive", "false")
                .addQueryParameter("include_files", "true")
                .addQueryParameter("include_dirs", "true")
                .build();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = readBody(response);
            if (response.code() == 404) {
                if (isSandboxNotFoundError(responseBody)) {
                    throw new SandboxNotFoundException(sandboxId, response.code(), responseBody);
                }
                return false;
            }
            if (!response.isSuccessful()) {
                raiseForStatus(response, responseBody);
            }
            List<Map<String, Object>> items = parseItems(responseBody);
            for (Map<String, Object> item : items) {
                if (sandboxPath.equals(item.get("path"))) {
                    return true;
                }
            }
            return false;
        } catch (SandboxNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new SandboxOperationException("pathExists request failed", e);
        }
    }

    private int normalizeExecTimeout(Integer timeout) {
        if (timeout != null && timeout > 0) {
            return timeout;
        }
        return 30;
    }

    private void raiseForStatus(Response response, String responseBody) {
        if (response.isSuccessful()) {
            return;
        }
        if (response.code() == 404 && isSandboxNotFoundError(responseBody)) {
            String sandboxId = extractSandboxIdFromUrl(response.request().url()).orElse("");
            throw new SandboxNotFoundException(sandboxId, response.code(), responseBody);
        }
        String message = "HTTP " + response.code() + " for "
                + response.request().method() + " " + response.request().url();
        if (responseBody != null && !responseBody.isEmpty()) {
            message = message + ": " + responseBody;
        }
        throw new SandboxOperationException(message);
    }

    private boolean isSandboxNotFoundError(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.contains("sandbox") && lower.contains("not found")) {
            return true;
        }
        try {
            Map<String, Object> payload = MAPPER.readValue(body,
                    new TypeReference<Map<String, Object>>() {});
            for (String key : new String[]{"error", "detail", "message"}) {
                Object value = payload.get(key);
                if (value instanceof String) {
                    String v = ((String) value).toLowerCase(Locale.ROOT);
                    if (v.contains("sandbox") && v.contains("not found")) {
                        return true;
                    }
                }
            }
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse sandbox error response body as JSON", e);
        }
        return false;
    }

    private Optional<String> extractSandboxIdFromUrl(HttpUrl url) {
        List<String> segments = url.pathSegments();
        for (int i = 0; i < segments.size(); i++) {
            if ("sandboxes".equals(segments.get(i)) && i + 1 < segments.size()) {
                return Optional.of(segments.get(i + 1));
            }
        }
        return Optional.empty();
    }

    private String parentPath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String p = path;
        while (p.endsWith("/") && p.length() > 1) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.equals("/") || p.isEmpty()) {
            return "/";
        }
        int lastSlash = p.lastIndexOf('/');
        if (lastSlash < 0) {
            return ".";
        }
        if (lastSlash == 0) {
            return "/";
        }
        return p.substring(0, lastSlash);
    }

    private String readBody(Response response) throws IOException {
        return response.body() != null ? response.body().string() : "";
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new SandboxOperationException("Failed to serialize JSON", e);
        }
    }

    private List<Map<String, Object>> parseItems(String responseBody) throws IOException {
        Map<String, Object> result = MAPPER.readValue(responseBody,
                new TypeReference<Map<String, Object>>() {});
        Object itemsObj = result.get("items");
        if (itemsObj instanceof List) {
            List<?> rawItems = (List<?>) itemsObj;
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object item : rawItems) {
                if (item instanceof Map) {
                    items.add((Map<String, Object>) item);
                }
            }
            return items;
        }
        return Collections.emptyList();
    }

    /**
     * Response from exec API call.
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class ExecResponse {
        private String stdout;
        private String stderr;
        @JsonProperty("exit_code")
        private int exitCode;

        /**
         * Returns the stdout output from the executed command.
         *
         * @return the stdout content, may be null
         */
        public String getStdout() {
            return stdout;
        }

        /**
         * Returns the stderr output from the executed command.
         *
         * @return the stderr content, may be null
         */
        public String getStderr() {
            return stderr;
        }

        /**
         * Returns the exit code of the executed command.
         *
         * @return the exit code (0 for success)
         */
        public int getExitCode() {
            return exitCode;
        }

        /**
         * Sets the stdout output from the executed command.
         *
         * @param stdout the stdout content
         */
        public void setStdout(String stdout) {
            this.stdout = stdout;
        }

        /**
         * Sets the stderr output from the executed command.
         *
         * @param stderr the stderr content
         */
        public void setStderr(String stderr) {
            this.stderr = stderr;
        }

        /**
         * Sets the exit code of the executed command.
         *
         * @param exitCode the exit code value
         */
        public void setExitCode(int exitCode) {
            this.exitCode = exitCode;
        }
    }
}
