/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openjiuwen.core.sys_operation.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.config.SandboxLauncherConfig;
import com.openjiuwen.core.sys_operation.result.FileSystemItem;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Mirrors Python's helper classes and functions in
 * {@code openjiuwen/extensions/sys_operation/sandbox/providers/jiuwenbox.py}.
 */
final class JiuwenBoxProviderSupport {

    private static final ObjectMapper SORTED_JSON = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private static final Pattern SANDBOX_NOT_FOUND_RE =
            Pattern.compile("^\\s*Sandbox\\b.*\\bnot found\\b", Pattern.CASE_INSENSITIVE);

    private static final int DEFAULT_PROVIDER_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_RECREATE_RETRIES = 3;
    private static final long RECREATE_RETRY_SLEEP_MILLIS = 1_000L;
    private static final Object SHARED_LOCK = new Object();
    private static final Object RECREATE_LOCK = new Object();
    private static final Object IDLE_TIMEOUT_CACHE_LOCK = new Object();
    private static final Map<String, String> SHARED_SANDBOX_IDS = new ConcurrentHashMap<>();
    private static final Map<String, TimeoutConfig> IDLE_TIMEOUT_CACHE = new ConcurrentHashMap<>();

    private JiuwenBoxProviderSupport() {
    }

    static int resolveProviderTimeoutSeconds(SandboxGatewayConfig config) {
        if (config == null || config.getTimeoutSeconds() <= 0) {
            return DEFAULT_PROVIDER_TIMEOUT_SECONDS;
        }
        return config.getTimeoutSeconds();
    }

    static <R> R buildFsErrorResult(String execution, String errorMessage, Class<R> resultClass) {
        return AioProviderSupport.buildFsErrorResult(execution, errorMessage, resultClass);
    }

    static <T, R> R buildFsErrorResult(String execution, String errorMessage, Class<R> resultClass, T data) {
        return AioProviderSupport.buildFsErrorResult(execution, errorMessage, resultClass, data);
    }

    static <R> R buildShellErrorResult(String execution, String errorMessage, Class<R> resultClass) {
        return AioProviderSupport.buildShellErrorResult(execution, errorMessage, resultClass);
    }

    static <T, R> R buildShellErrorResult(String execution, String errorMessage, Class<R> resultClass, T data) {
        return AioProviderSupport.buildShellErrorResult(execution, errorMessage, resultClass, data);
    }

    static <R> R buildCodeErrorResult(String execution, String errorMessage, Class<R> resultClass) {
        return AioProviderSupport.buildCodeErrorResult(execution, errorMessage, resultClass);
    }

    static <T, R> R buildCodeErrorResult(String execution, String errorMessage, Class<R> resultClass, T data) {
        return AioProviderSupport.buildCodeErrorResult(execution, errorMessage, resultClass, data);
    }

    static boolean isSandboxNotFoundError(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof JiuwenBoxHttpException httpException && httpException.getStatusCode() == 404) {
                String detail = httpException.getDetail();
                if (detail != null && SANDBOX_NOT_FOUND_RE.matcher(detail).find()) {
                    return true;
                }
            }
            String message = cursor.getMessage();
            if (message != null && SANDBOX_NOT_FOUND_RE.matcher(message).find() && message.contains("404")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    static List<String> readExcludedCommands(Map<String, Object> extra) {
        if (extra == null) {
            return null;
        }
        Object raw = extra.get("excluded_commands");
        if (!(raw instanceof List<?> rawList)) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof String text && !text.isBlank()) {
                values.add(text);
            }
        }
        return values.isEmpty() ? null : values;
    }

    static boolean commandMatchesExclude(String command, List<String> patterns) {
        if (command == null || command.isBlank() || patterns == null || patterns.isEmpty()) {
            return false;
        }
        String stripped = command.trim();
        String firstToken = firstToken(stripped);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (matchesGlob(stripped, pattern) || matchesGlob(firstToken, pattern)) {
                return true;
            }
        }
        return false;
    }

    static List<String> shellCommand(String command) {
        if (isExecutableOnPath("bash")) {
            return List.of("bash", "-lc", command);
        }
        if (isWindows()) {
            return List.of("powershell", "-NoProfile", "-Command", command);
        }
        return List.of("sh", "-lc", command);
    }

    static LocalProcessResult runLocalSubprocess(
            List<String> argv,
            String cwd,
            Map<String, String> env,
            Integer timeoutSeconds,
            String stdin) {
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.redirectErrorStream(false);
        if (cwd != null && !cwd.isBlank()) {
            builder.directory(new java.io.File(cwd));
        }
        if (env != null && !env.isEmpty()) {
            builder.environment().putAll(env);
        }
        Process process = null;
        try {
            process = builder.start();
            Process running = process;
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readStreamSafely(running.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readStreamSafely(running.getErrorStream()));
            if (stdin != null) {
                try (OutputStream outputStream = process.getOutputStream()) {
                    outputStream.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                process.getOutputStream().close();
            }

            boolean finished;
            if (timeoutSeconds != null && timeoutSeconds > 0) {
                finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            } else {
                process.waitFor();
                finished = true;
            }
            if (!finished) {
                process.destroyForcibly();
                String stdout = stdoutFuture.join();
                String stderr = stderrFuture.join() + "\n[local timeout after " + timeoutSeconds + "s]";
                return new LocalProcessResult(stdout, stderr, 124);
            }
            return new LocalProcessResult(stdoutFuture.join(), stderrFuture.join(), process.exitValue());
        } catch (Exception exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new LocalProcessResult("", "local subprocess error: " + exception.getMessage(), 1);
        }
    }

    static FileSystemItem itemFromPayload(Map<String, Object> item) {
        String name = String.valueOf(item.getOrDefault("name", ""));
        return FileSystemItem.builder()
                .name(name)
                .path(String.valueOf(item.getOrDefault("path", "")))
                .size(asInt(item.get("size"), 0))
                .isDirectory(asBoolean(item.get("is_directory"), false))
                .modifiedTime(String.valueOf(item.getOrDefault("modified_time", "0")))
                .type(item.get("type") == null ? extensionOf(name) : String.valueOf(item.get("type")))
                .build();
    }

    static String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? null : fileName.substring(dot);
    }

    static List<UploadPair> iterHostFilesForUpload(Object uploadEntries) {
        if (!(uploadEntries instanceof List<?> rawList)) {
            return List.of();
        }
        List<UploadPair> pairs = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String hostPath = stringValue(rawMap.get("host_path"));
            String sandboxPath = stringValue(rawMap.get("sandbox_path"));
            String kind = stringValue(rawMap.get("kind")).toLowerCase();
            if (hostPath.isBlank() || sandboxPath.isBlank()) {
                continue;
            }
            Path hostRoot = Paths.get(hostPath);
            if ("directory".equals(kind) || Files.isDirectory(hostRoot)) {
                if (!Files.isDirectory(hostRoot)) {
                    continue;
                }
                try (var stream = Files.walk(hostRoot)) {
                    stream.filter(Files::isRegularFile).forEach(subPath -> {
                        Path relative = hostRoot.relativize(subPath);
                        String sandboxChild = sandboxPath.replace('\\', '/');
                        if (!sandboxChild.endsWith("/")) {
                            sandboxChild = sandboxChild + "/";
                        }
                        sandboxChild = sandboxChild + relative.toString().replace('\\', '/');
                        pairs.add(new UploadPair(subPath, sandboxChild));
                    });
                } catch (IOException ignored) {
                    // Best-effort upload path mirrors Python's skip-on-error behavior.
                }
                continue;
            }
            if (Files.isRegularFile(hostRoot)) {
                pairs.add(new UploadPair(hostRoot, sandboxPath));
            }
        }
        return pairs;
    }

    static int uploadPreserveFilesSync(
            JiuwenBoxClient client,
            String sandboxId,
            Object uploadEntries) {
        List<UploadPair> pairs = iterHostFilesForUpload(uploadEntries);
        int uploaded = 0;
        for (UploadPair pair : pairs) {
            try {
                client.uploadBytes(sandboxId, pair.sandboxPath(), Files.readAllBytes(pair.hostPath()));
                uploaded++;
            } catch (Exception ignored) {
                // Python also treats preserve-file upload as best effort.
            }
        }
        return uploaded;
    }

    static void uploadPreserveFilesBestEffort(
            JiuwenBoxClient client,
            String sandboxId,
            Object uploadEntries) {
        uploadPreserveFilesSync(client, sandboxId, uploadEntries);
    }

    static void registerSharedSandboxId(String sharedKey, String sandboxId) {
        synchronized (SHARED_LOCK) {
            SHARED_SANDBOX_IDS.put(sharedKey, sandboxId);
        }
    }

    static List<String> clearSharedSandbox(String baseUrl) {
        String sharedKey = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        List<String> removed = new ArrayList<>();
        synchronized (SHARED_LOCK) {
            List<String> keysToDelete = SHARED_SANDBOX_IDS.keySet().stream()
                    .filter(key -> key.startsWith(sharedKey))
                    .toList();
            for (String key : keysToDelete) {
                String sandboxId = SHARED_SANDBOX_IDS.remove(key);
                if (sandboxId != null && !sandboxId.isBlank() && !removed.contains(sandboxId)) {
                    removed.add(sandboxId);
                }
            }
        }
        return removed;
    }

    static String forceRecreateJiuwenBoxSandbox(
            String baseUrl,
            Map<String, Object> createOptions,
            double timeoutSeconds,
            Object preserveFilesUpload,
            List<String> extraStaleSandboxIds) throws Exception {
        List<String> staleSandboxIds = new ArrayList<>(clearSharedSandbox(baseUrl));
        if (extraStaleSandboxIds != null) {
            for (String sandboxId : extraStaleSandboxIds) {
                if (sandboxId != null && !sandboxId.isBlank() && !staleSandboxIds.contains(sandboxId)) {
                    staleSandboxIds.add(sandboxId);
                }
            }
        }
        try (JiuwenBoxClient client = new JiuwenBoxClient(baseUrl, timeoutSeconds)) {
            String sandboxId = client.createSandbox(createOptions);
            if (preserveFilesUpload != null) {
                uploadPreserveFilesSync(client, sandboxId, preserveFilesUpload);
            }
            for (String oldId : staleSandboxIds) {
                if (oldId == null || oldId.isBlank() || Objects.equals(oldId, sandboxId)) {
                    continue;
                }
                try {
                    client.deleteSandbox(oldId);
                } catch (Exception ignored) {
                    // Keep the new sandbox even if stale cleanup partially fails.
                }
            }
            registerSharedSandboxId(buildSharedScopeKey(baseUrl, createOptions), sandboxId);
            return sandboxId;
        }
    }

    static String buildSharedScopeKey(String baseUrl, Map<String, Object> createOptions) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (createOptions == null || createOptions.isEmpty()) {
            return normalizedBase;
        }
        try {
            return normalizedBase + "|" + SORTED_JSON.writeValueAsString(new TreeMap<>(createOptions));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize sandbox create options", exception);
        }
    }

    static int resolveRecreateRetries() {
        String raw = System.getenv("JIUWENBOX_SANDBOX_RECREATE_RETRIES");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_RECREATE_RETRIES;
        }
        try {
            return Math.max(Integer.parseInt(raw), 0);
        } catch (NumberFormatException exception) {
            return DEFAULT_RECREATE_RETRIES;
        }
    }

    private static String readStreamSafely(InputStream inputStream) {
        try (InputStream in = inputStream; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            in.transferTo(outputStream);
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    private static String firstToken(String command) {
        int whitespace = command.indexOf(' ');
        return whitespace < 0 ? command : command.substring(0, whitespace);
    }

    private static boolean matchesGlob(String text, String pattern) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < pattern.length(); index++) {
            char ch = pattern.charAt(index);
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '^', '$', '+', '|', '\\' -> regex.append('\\').append(ch);
                default -> regex.append(ch);
            }
        }
        regex.append('$');
        return text.matches(regex.toString());
    }

    private static boolean isExecutableOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return false;
        }
        String[] pathEntries = path.split(Pattern.quote(java.io.File.pathSeparator));
        List<String> suffixes = isWindows()
                ? List.of(".exe", ".cmd", ".bat", "")
                : List.of("");
        for (String entry : pathEntries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            for (String suffix : suffixes) {
                Path candidate = Paths.get(entry, executable + suffix);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    static int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record TimeoutConfig(Integer idleTimeout, Integer idleCheckInterval) {
    }

    record LocalProcessResult(String stdout, String stderr, int exitCode) {
    }

    record UploadPair(Path hostPath, String sandboxPath) {
    }

    @FunctionalInterface
    interface CheckedFunction<T, R> {
        R apply(T value) throws Exception;
    }

    /**
     * Mirrors Python's {@code _JiuwenBoxProviderMixin} in
     * {@code openjiuwen/extensions/sys_operation/sandbox/providers/jiuwenbox.py}.
     */
    static final class ProviderState {

        private final SandboxEndpoint endpoint;
        private final SandboxGatewayConfig config;
        private final int timeoutSeconds;
        private JiuwenBoxClient client;
        private String sandboxId;

        ProviderState(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
            this.config = config;
            this.timeoutSeconds = resolveProviderTimeoutSeconds(config);
            this.sandboxId = endpoint.sandboxId();
        }

        JiuwenBoxClient getClient() {
            if (client == null) {
                String baseUrl = baseUrl();
                if (baseUrl == null || baseUrl.isBlank()) {
                    throw new IllegalArgumentException("jiuwenbox provider requires endpoint.base_url");
                }
                client = new JiuwenBoxClient(baseUrl, timeoutSeconds);
            }
            return client;
        }

        Map<String, Object> launcherExtraParams(boolean create) {
            SandboxLauncherConfig launcherConfig = config == null ? null : config.getLauncherConfig();
            if (launcherConfig == null) {
                return Map.of();
            }
            Map<String, Object> extraParams = launcherConfig.getExtraParams();
            if (extraParams != null) {
                return extraParams;
            }
            if (!create) {
                return Map.of();
            }
            Map<String, Object> created = new LinkedHashMap<>();
            launcherConfig.setExtraParams(created);
            return created;
        }

        Map<String, Object> sandboxCreateOptionsFromLauncherExtraParams() {
            Map<String, Object> extraParams = launcherExtraParams(false);
            Map<String, Object> options = new LinkedHashMap<>();
            Object policy = extraParams.get("policy");
            if (policy instanceof Map<?, ?> rawMap) {
                options.put("policy", new LinkedHashMap<>((Map<?, ?>) rawMap));
            }
            Object policyMode = extraParams.get("policy_mode");
            if (policyMode instanceof String text && !text.isBlank()) {
                options.put("policy_mode", text);
            } else if (policyMode instanceof Enum<?> enumValue) {
                options.put("policy_mode", enumValue.name().toLowerCase());
            }
            return options;
        }

        String sharedScopeKey() {
            return buildSharedScopeKey(baseUrl(), sandboxCreateOptionsFromLauncherExtraParams());
        }

        String sandboxIdFromLauncherExtraParams() {
            Object value = launcherExtraParams(false).get("sandbox_id");
            return value instanceof String text && !text.isBlank() ? text : null;
        }

        TimeoutConfig idleTimeoutFromLauncher() {
            SandboxLauncherConfig launcherConfig = config == null ? null : config.getLauncherConfig();
            Integer idleTimeout = launcherConfig == null ? null : launcherConfig.getIdleTtlSeconds();
            Object rawCheck = launcherExtraParams(false).get("idle_check_interval");
            Integer idleCheckInterval = rawCheck instanceof Boolean
                    ? null
                    : rawCheck instanceof Number number ? number.intValue() : null;
            return new TimeoutConfig(idleTimeout, idleCheckInterval);
        }

        void configureServerIdleTimeout() {
            TimeoutConfig timeoutConfig = idleTimeoutFromLauncher();
            if (timeoutConfig.idleTimeout() == null && timeoutConfig.idleCheckInterval() == null) {
                return;
            }
            String cacheKey = baseUrl().replaceAll("/+$", "");
            synchronized (IDLE_TIMEOUT_CACHE_LOCK) {
                if (Objects.equals(IDLE_TIMEOUT_CACHE.get(cacheKey), timeoutConfig)) {
                    return;
                }
            }
            try {
                getClient().setIdleTimeout(timeoutConfig.idleTimeout(), timeoutConfig.idleCheckInterval());
                synchronized (IDLE_TIMEOUT_CACHE_LOCK) {
                    IDLE_TIMEOUT_CACHE.put(cacheKey, timeoutConfig);
                }
            } catch (Exception ignored) {
                // Idle-timeout propagation is hygiene, not a hard blocker.
            }
        }

        String getSandboxId() throws Exception {
            String envSandboxId = System.getenv("JIUWENBOX_SANDBOX_ID");
            if (envSandboxId != null && !envSandboxId.isBlank() && !Objects.equals(sandboxId, envSandboxId)) {
                sandboxId = envSandboxId;
            }
            String extraSandboxId = sandboxIdFromLauncherExtraParams();
            if (extraSandboxId != null && !Objects.equals(extraSandboxId, sandboxId)) {
                sandboxId = extraSandboxId;
            }
            if (sandboxId == null || sandboxId.isBlank()) {
                sandboxId = endpoint.sandboxId();
            }
            if (sandboxId == null || sandboxId.isBlank()) {
                String sharedKey = sharedScopeKey();
                boolean newlyCreated = false;
                synchronized (SHARED_LOCK) {
                    sandboxId = SHARED_SANDBOX_IDS.get(sharedKey);
                    if (sandboxId == null || sandboxId.isBlank()) {
                        configureServerIdleTimeout();
                        sandboxId = getClient().createSandbox(sandboxCreateOptionsFromLauncherExtraParams());
                        newlyCreated = true;
                    }
                    SHARED_SANDBOX_IDS.put(sharedKey, sandboxId);
                    launcherExtraParams(true).put("sandbox_id", sandboxId);
                }
                if (newlyCreated) {
                    uploadPreserveFilesBestEffort(
                            getClient(),
                            sandboxId,
                            launcherExtraParams(false).get("preserve_files_upload"));
                }
            } else {
                synchronized (SHARED_LOCK) {
                    SHARED_SANDBOX_IDS.put(sharedScopeKey(), sandboxId);
                    launcherExtraParams(true).put("sandbox_id", sandboxId);
                }
            }
            return sandboxId;
        }

        <T> T executeWithSandboxRetry(CheckedFunction<String, T> operation) throws Exception {
            int maxRetries = resolveRecreateRetries();
            Exception last = null;
            String staleSandboxId = getSandboxId();
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                String currentSandboxId = attempt == 0 ? staleSandboxId : recreateSandboxAfterLoss(staleSandboxId);
                if (attempt > 0) {
                    Thread.sleep(RECREATE_RETRY_SLEEP_MILLIS);
                }
                try {
                    return operation.apply(currentSandboxId);
                } catch (Exception exception) {
                    if (!isSandboxNotFoundError(exception)) {
                        throw exception;
                    }
                    last = exception;
                    staleSandboxId = currentSandboxId;
                }
            }
            throw last == null ? new IllegalStateException("sandbox retry failed") : last;
        }

        String recreateSandboxAfterLoss(String staleSandboxId) throws Exception {
            String baseUrl = baseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("jiuwenbox provider requires endpoint.base_url");
            }
            Map<String, Object> createOptions = sandboxCreateOptionsFromLauncherExtraParams();
            Object preserveFilesUpload = launcherExtraParams(false).get("preserve_files_upload");
            synchronized (RECREATE_LOCK) {
                String current = sandboxIdFromLauncherExtraParams();
                String cached;
                synchronized (SHARED_LOCK) {
                    cached = SHARED_SANDBOX_IDS.get(sharedScopeKey());
                }
                for (String candidate : List.of(current, cached)) {
                    if (candidate != null && !candidate.isBlank() && !Objects.equals(candidate, staleSandboxId)) {
                        sandboxId = candidate;
                        return candidate;
                    }
                }
                String newId = forceRecreateJiuwenBoxSandbox(
                        baseUrl,
                        createOptions,
                        timeoutSeconds,
                        preserveFilesUpload,
                        List.of(staleSandboxId));
                sandboxId = newId;
                launcherExtraParams(true).put("sandbox_id", newId);
                return newId;
            }
        }

        private String baseUrl() {
            if (endpoint.baseUrl() != null && !endpoint.baseUrl().isBlank()) {
                return endpoint.baseUrl();
            }
            SandboxLauncherConfig launcherConfig = config == null ? null : config.getLauncherConfig();
            if (launcherConfig instanceof PreDeployLauncherConfig preDeployLauncherConfig) {
                return preDeployLauncherConfig.getBaseUrl();
            }
            return null;
        }
    }

    /**
     * Mirrors Python's {@code _JiuwenBoxClient} in
     * {@code openjiuwen/extensions/sys_operation/sandbox/providers/jiuwenbox.py}.
     */
    static final class JiuwenBoxClient implements AutoCloseable {

        private final String baseUrl;
        private final double timeoutSeconds;
        private final HttpClient httpClient;

        JiuwenBoxClient(String baseUrl, double timeoutSeconds) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl").replaceAll("/+$", "");
            this.timeoutSeconds = timeoutSeconds <= 0 ? 30.0d : timeoutSeconds;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
        }

        String createSandbox(Map<String, Object> createOptions) throws IOException, InterruptedException {
            Map<String, Object> body = createOptions == null ? Map.of() : createOptions;
            HttpRequest request = HttpRequest.newBuilder(resolveUri("/api/v1/sandboxes", Map.of()))
                    .timeout(Duration.ofSeconds((long) Math.max(timeoutSeconds, 30.0d)))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(AioProviderSupport.JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), response.body(), "POST", request.uri());
            JsonNode payload = AioProviderSupport.JSON.readTree(response.body());
            return payload.path("id").asText();
        }

        void setIdleTimeout(Integer idleTimeout, Integer idleCheckInterval) throws IOException, InterruptedException {
            Map<String, Object> body = new LinkedHashMap<>();
            if (idleTimeout != null) {
                body.put("idle_timeout", idleTimeout);
            }
            if (idleCheckInterval != null) {
                body.put("idle_check_interval", idleCheckInterval);
            }
            if (body.isEmpty()) {
                return;
            }
            HttpRequest request = HttpRequest.newBuilder(resolveUri("/api/v1/timeout", Map.of()))
                    .timeout(Duration.ofSeconds((long) Math.max(timeoutSeconds, 30.0d)))
                    .header("content-type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(AioProviderSupport.JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), response.body(), "PUT", request.uri());
        }

        void deleteSandbox(String sandboxId) throws IOException, InterruptedException {
            if (sandboxId == null || sandboxId.isBlank()) {
                return;
            }
            HttpRequest request = HttpRequest.newBuilder(resolveUri("/api/v1/sandboxes/" + urlPath(sandboxId), Map.of()))
                    .timeout(Duration.ofSeconds((long) Math.max(timeoutSeconds, 30.0d)))
                    .DELETE()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) {
                return;
            }
            ensureSuccess(response.statusCode(), response.body(), "DELETE", request.uri());
        }

        Map<String, Object> exec(
                String sandboxId,
                List<String> command,
                String cwd,
                Integer timeoutSeconds,
                Map<String, String> environment,
                String stdin) throws IOException, InterruptedException {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("command", command);
            if (cwd != null && !cwd.isBlank()) {
                body.put("workdir", cwd);
            }
            if (environment != null && !environment.isEmpty()) {
                body.put("env", environment);
            }
            if (stdin != null) {
                body.put("stdin", stdin);
            }
            if (timeoutSeconds != null && timeoutSeconds > 0) {
                body.put("timeout_seconds", timeoutSeconds);
            }
            int requestTimeout = Math.max(timeoutSeconds == null ? 30 : timeoutSeconds, 30);
            HttpRequest request = HttpRequest.newBuilder(
                            resolveUri("/api/v1/sandboxes/" + urlPath(sandboxId) + "/exec", Map.of()))
                    .timeout(Duration.ofSeconds(requestTimeout))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(AioProviderSupport.JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), response.body(), "POST", request.uri());
            return parseJsonMap(response.body(), request.uri().toString());
        }

        void uploadBytes(String sandboxId, String sandboxPath, byte[] content) throws IOException, InterruptedException {
            String boundary = "----openjiuwen-jiuwenbox-" + System.nanoTime();
            String fileName = Path.of(sandboxPath).getFileName() == null
                    ? "upload.bin"
                    : Path.of(sandboxPath).getFileName().toString();
            List<byte[]> parts = new ArrayList<>();
            parts.add(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(content == null ? new byte[0] : content);
            parts.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(resolveUri(
                            "/api/v1/sandboxes/" + urlPath(sandboxId) + "/upload",
                            Map.of("sandbox_path", sandboxPath)))
                    .timeout(Duration.ofSeconds((long) Math.max(timeoutSeconds, 30.0d)))
                    .header("content-type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(parts))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), response.body(), "POST", request.uri());
        }

        void appendBytes(String sandboxId, String sandboxPath, byte[] content) throws IOException, InterruptedException {
            String encodedContent = Base64.getEncoder().encodeToString(content == null ? new byte[0] : content);
            Map<String, Object> result = exec(
                    sandboxId,
                    List.of(
                            "bash",
                            "-lc",
                            "set -euo pipefail; target=\"$1\"; parent=$(dirname -- \"$target\"); mkdir -p -- \"$parent\"; base64 -d >> \"$target\"",
                            "jiuwenbox-append",
                            sandboxPath),
                    null,
                    null,
                    null,
                    encodedContent);
            if (asInt(result.get("exit_code"), 0) != 0) {
                throw new IOException(String.valueOf(result.getOrDefault("stderr",
                        result.getOrDefault("stdout", "append file failed"))));
            }
        }

        byte[] downloadBytes(String sandboxId, String sandboxPath) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(resolveUri(
                            "/api/v1/sandboxes/" + urlPath(sandboxId) + "/download",
                            Map.of("sandbox_path", sandboxPath)))
                    .timeout(Duration.ofSeconds((long) Math.max(timeoutSeconds, 30.0d)))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8), "GET", request.uri());
            return response.body();
        }

        List<Map<String, Object>> listFiles(
                String sandboxId,
                String path,
                boolean recursive,
                Integer maxDepth,
                boolean includeFiles,
                boolean includeDirs) throws IOException, InterruptedException {
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("sandbox_path", path);
            queryParams.put("recursive", Boolean.toString(recursive));
            queryParams.put("include_files", Boolean.toString(includeFiles));
            queryParams.put("include_dirs", Boolean.toString(includeDirs));
            if (maxDepth != null) {
                queryParams.put("max_depth", Integer.toString(maxDepth));
            }
            HttpRequest request = HttpRequest.newBuilder(resolveUri(
                            "/api/v1/sandboxes/" + urlPath(sandboxId) + "/files",
                            queryParams))
                    .timeout(Duration.ofSeconds((long) Math.max(timeoutSeconds, 30.0d)))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), response.body(), "GET", request.uri());
            return parseJsonItems(response.body(), "items", request.uri().toString());
        }

        List<Map<String, Object>> searchFiles(
                String sandboxId,
                String path,
                String pattern,
                List<String> excludePatterns) throws IOException, InterruptedException {
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("sandbox_path", path);
            queryParams.put("pattern", pattern);
            if (excludePatterns != null && !excludePatterns.isEmpty()) {
                queryParams.put("exclude_patterns", String.join("\u0000", excludePatterns));
            }
            HttpRequest request = HttpRequest.newBuilder(resolveUri(
                            "/api/v1/sandboxes/" + urlPath(sandboxId) + "/search",
                            queryParams))
                    .timeout(Duration.ofSeconds((long) Math.max(timeoutSeconds, 30.0d)))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), response.body(), "GET", request.uri());
            return parseJsonItems(response.body(), "items", request.uri().toString());
        }

        boolean pathExists(String sandboxId, String sandboxPath) throws IOException, InterruptedException {
            Path path = Paths.get(sandboxPath);
            String parent = path.getParent() == null ? "/" : path.getParent().toString().replace('\\', '/');
            try {
                return listFiles(sandboxId, parent, false, null, true, true).stream()
                        .anyMatch(item -> Objects.equals(String.valueOf(item.get("path")), sandboxPath));
            } catch (JiuwenBoxHttpException exception) {
                if (exception.getStatusCode() == 404 && !isSandboxNotFoundError(exception)) {
                    return false;
                }
                throw exception;
            }
        }

        @Override
        public void close() {
            // java.net.http.HttpClient does not require explicit cleanup.
        }

        private URI resolveUri(String path, Map<String, String> queryParams) {
            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            if (queryParams == null || queryParams.isEmpty()) {
                return URI.create(baseUrl + normalizedPath);
            }
            StringBuilder query = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                if (entry.getKey().equals("exclude_patterns") && entry.getValue().contains("\u0000")) {
                    for (String item : entry.getValue().split("\u0000")) {
                        if (!first) {
                            query.append('&');
                        }
                        first = false;
                        query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(item));
                    }
                    continue;
                }
                if (!first) {
                    query.append('&');
                }
                first = false;
                query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
            }
            return URI.create(baseUrl + normalizedPath + "?" + query);
        }

        private Map<String, Object> parseJsonMap(String body, String path) throws IOException {
            try {
                return AioProviderSupport.JSON.readValue(body == null ? "{}" : body, Map.class);
            } catch (JsonProcessingException exception) {
                throw new IOException("Invalid JSON response from " + path + ": " + body, exception);
            }
        }

        private List<Map<String, Object>> parseJsonItems(String body, String field, String path) throws IOException {
            try {
                JsonNode payload = AioProviderSupport.JSON.readTree(body == null ? "{}" : body);
                List<Map<String, Object>> items = new ArrayList<>();
                for (JsonNode itemNode : payload.path(field)) {
                    items.add(AioProviderSupport.JSON.convertValue(itemNode, Map.class));
                }
                return items;
            } catch (JsonProcessingException exception) {
                throw new IOException("Invalid JSON response from " + path + ": " + body, exception);
            }
        }

        private void ensureSuccess(int statusCode, String body, String method, URI uri) throws JiuwenBoxHttpException {
            if (statusCode >= 200 && statusCode < 300) {
                return;
            }
            String detail = responseErrorDetail(body);
            String message = "HTTP " + statusCode + " for " + method + " " + uri;
            if (!detail.isBlank()) {
                message = message + ": " + detail;
            }
            throw new JiuwenBoxHttpException(statusCode, detail, message);
        }

        private String responseErrorDetail(String body) {
            if (body == null || body.isBlank()) {
                return "";
            }
            try {
                JsonNode payload = AioProviderSupport.JSON.readTree(body);
                if (payload.isObject()) {
                    for (String key : List.of("error", "detail", "message")) {
                        JsonNode value = payload.get(key);
                        if (value != null && !value.isNull()) {
                            return value.isTextual() ? value.asText() : value.toString();
                        }
                    }
                    return payload.toString();
                }
                return payload.isTextual() ? payload.asText() : payload.toString();
            } catch (JsonProcessingException exception) {
                return body.strip();
            }
        }

        private String urlEncode(String value) {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
        }

        private String urlPath(String value) {
            return urlEncode(value).replace("+", "%20");
        }
    }

    /**
     * Mirrors Python's HTTP status failures in
     * {@code openjiuwen/extensions/sys_operation/sandbox/providers/jiuwenbox.py}.
     */
    static final class JiuwenBoxHttpException extends IOException {

        private final int statusCode;
        private final String detail;

        JiuwenBoxHttpException(int statusCode, String detail, String message) {
            super(message);
            this.statusCode = statusCode;
            this.detail = detail;
        }

        int getStatusCode() {
            return statusCode;
        }

        String getDetail() {
            return detail;
        }
    }
}
