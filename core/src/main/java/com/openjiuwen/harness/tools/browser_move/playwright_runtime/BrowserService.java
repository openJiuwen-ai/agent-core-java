/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.drivers.ManagedBrowserDriver;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Browser backend service with sticky sessions and guardrails.
 *
 * <p>Mirrors Python's {@code BrowserService} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/service.py}.</p>
 */
public class BrowserService {

    public static final String MAX_ITERATION_MESSAGE = "Max iterations reached without completion";

    private static final int DEFAULT_MANAGED_PORT = 9333;

    private final String provider;
    private final String apiKey;
    private final String apiBase;
    private final String modelName;
    private final McpServerConfig mcpConfig;
    private final BrowserRunGuardrails guardrails;
    private final Path mcpCwd;
    private final BrowserProfileStore profileStore;
    private final String profileName;
    private final String driverMode;
    private final Map<String, String> failureContextBySession = new ConcurrentHashMap<>();
    private final Map<String, BrowserTaskProgressState> progressBySession = new ConcurrentHashMap<>();
    private volatile boolean started;
    private volatile boolean connectionHealthy;
    private volatile Long lastHeartbeatOk;
    private volatile Object browserAgent;
    private volatile BrowserProfile activeProfile;
    private volatile ManagedBrowserDriver managedDriver;
    private volatile String registeredCdpEndpoint = "";
    private volatile Thread heartbeatThread;
    private volatile boolean heartbeatStopRequested;
    private volatile long heartbeatIntervalMillis = 1_000L;
    private final Map<String, Set<Object>> inflightTasks = new ConcurrentHashMap<>();

    public BrowserService(
            String provider,
            String apiKey,
            String apiBase,
            String modelName,
            McpServerConfig mcpConfig,
            BrowserRunGuardrails guardrails
    ) {
        this.provider = provider == null ? "" : provider;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiBase = apiBase == null ? "" : apiBase;
        this.modelName = modelName == null ? "" : modelName;
        this.mcpConfig = mcpConfig == null ? BrowserRuntimeConfig.buildPlaywrightMcpConfig() : mcpConfig;
        this.guardrails = guardrails == null ? BrowserRuntimeConfig.buildBrowserGuardrails() : guardrails;
        this.mcpCwd = resolveMcpCwd(this.mcpConfig);
        this.profileName = blankToDefault(configValue("BROWSER_PROFILE_NAME"), "jiuwenclaw");
        this.driverMode = resolveDriverMode();
        this.profileStore = new BrowserProfileStore(resolveProfileStorePath());
    }

    public void ensureRuntimeReady() {
        boolean browserRebound = ensureManagedDriverStarted();
        String configuredEndpoint = configuredCdpEndpoint();
        if (started && (browserRebound || !configuredEndpoint.equals(registeredCdpEndpoint))) {
            refreshMcpServerBinding();
            browserAgent = null;
        }
        if (!started) {
            registeredCdpEndpoint = configuredEndpoint;
            started = true;
        }
        connectionHealthy = true;
        lastHeartbeatOk = System.currentTimeMillis();
    }

    public void ensureStarted() {
        ensureRuntimeReady();
    }

    public synchronized void startHeartbeat() {
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            return;
        }
        heartbeatStopRequested = false;
        heartbeatThread = new Thread(this::heartbeatLoop, "browser-service-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    protected void heartbeatLoop() {
        while (!heartbeatStopRequested && !Thread.currentThread().isInterrupted()) {
            try {
                checkConnection();
                connectionHealthy = true;
                lastHeartbeatOk = System.currentTimeMillis();
            } catch (RuntimeException ex) {
                connectionHealthy = false;
            }
            if (heartbeatStopRequested || Thread.currentThread().isInterrupted()) {
                break;
            }
            try {
                Thread.sleep(Math.max(1L, heartbeatIntervalMillis));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    protected void checkConnection() {
        Object client = getRegisteredBrowserRuntimeClient();
        if (client == null) {
            throw new IllegalStateException("browser runtime client not found");
        }
        if (!pingClient(client)) {
            throw new IllegalStateException("browser runtime client is not responding");
        }
        ManagedBrowserDriver driver = managedDriver;
        if (driver != null && !driver.isEndpointReady()) {
            throw new IllegalStateException("browser runtime CDP endpoint is not ready");
        }
    }

    public Map<String, Object> requestCancel(String sessionId, String requestId) {
        return Map.of(
                "ok", true,
                "session_id", normalizeSessionId(sessionId),
                "request_id", requestId == null ? "" : requestId,
                "error", ""
        );
    }

    public Map<String, Object> clearCancel(String sessionId, String requestId) {
        return requestCancel(sessionId, requestId);
    }

    public Map<String, Object> runTask(String task, String sessionId, String requestId, Integer timeoutSeconds) {
        ensureStarted();
        String sid = newSession(sessionId);
        String rid = requestId == null || requestId.isBlank() ? randomHex() : requestId.trim();
        int effectiveTimeout = timeoutSeconds != null && timeoutSeconds > 0
                ? timeoutSeconds
                : guardrails.getTimeoutSeconds();
        int attempts = guardrails.isRetryOnce() ? 2 : 1;
        int maxAttempts = attempts + (guardrails.isResumeOnMaxIterations() ? 1 : 0);
        String baseTask = task == null ? "" : task.trim();
        String previousFailureSummary = failureContextBySession.getOrDefault(sid, "");
        String nextTask = buildTaskWithFailureContext(baseTask, previousFailureSummary);
        boolean usedMaxIterationResume = false;
        int attemptIndex = 0;
        String lastError = null;
        String lastFailureFinal = "";
        Map<String, Object> lastFailurePage = Map.of();
        Object lastFailureScreenshot = null;

        while (attemptIndex < maxAttempts) {
            try {
                Map<String, Object> parsed = runTaskOnce(nextTask, sid, rid);
                attemptIndex += 1;
                updateProgressFromWorkerResult(sid, rid, parsed);

                boolean parsedOk = Boolean.TRUE.equals(parsed.get("ok"));
                if (!parsedOk && shouldTreatAsCompleted(parsed)) {
                    parsed = new LinkedHashMap<>(parsed);
                    parsed.put("ok", true);
                    parsed.put("error", null);
                    parsedOk = true;
                }

                boolean shouldResumeMaxIteration = !parsedOk
                        && isMaxIterationResult(parsed)
                        && guardrails.isResumeOnMaxIterations()
                        && !usedMaxIterationResume;
                if (!parsedOk) {
                    lastError = stringValue(parsed.get("error"));
                    lastFailureFinal = stringValue(parsed.get("final"));
                    lastFailurePage = mapValue(parsed.get("page"));
                    lastFailureScreenshot = parsed.get("screenshot");
                }

                if (shouldResumeMaxIteration) {
                    usedMaxIterationResume = true;
                    nextTask = buildResumeTask(
                            nextTask,
                            stringValue(parsed.get("final")),
                            buildProgressContext(progressBySession.get(sid))
                    );
                    lastError = stringValue(parsed.getOrDefault("error", MAX_ITERATION_MESSAGE));
                    continue;
                }

                if (!parsedOk && attemptIndex < attempts && isRetryableRuntimeResult(parsed)) {
                    Map<String, Object> page = mapValue(parsed.get("page"));
                    String failureSummary = buildFailureSummary(
                            baseTask,
                            stringValue(parsed.get("error")),
                            stringValue(page.get("url")),
                            stringValue(page.get("title")),
                            stringValue(parsed.get("final")),
                            parsed.get("screenshot"),
                            attemptIndex,
                            progressBySession.get(sid)
                    );
                    if (isRetryableTransportMessage(failureSummary) || shouldRestartAfterRuntimeResult(parsed)) {
                        restart();
                    }
                    nextTask = buildTaskWithFailureContext(baseTask, failureSummary);
                    continue;
                }

                Map<String, Object> page = mapValue(parsed.get("page"));
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("ok", parsedOk);
                response.put("session_id", sid);
                response.put("request_id", rid);
                response.put("final", stringValue(parsed.get("final")));
                response.put("page", Map.of(
                        "url", stringValue(page.get("url")),
                        "title", stringValue(page.get("title"))
                ));
                response.put("screenshot", parsed.get("screenshot"));
                response.put("error", parsed.get("error"));
                response.put("attempt", attemptIndex);
                response.put("progress_state", null);
                response.put("timeout_s", effectiveTimeout);
                if (parsedOk) {
                    failureContextBySession.remove(sid);
                    progressBySession.remove(sid);
                    response.put("failure_summary", null);
                    return response;
                }

                String failureSummary = buildFailureSummary(
                        baseTask,
                        stringValue(parsed.get("error")),
                        stringValue(page.get("url")),
                        stringValue(page.get("title")),
                        stringValue(parsed.get("final")),
                        parsed.get("screenshot"),
                        attemptIndex,
                        progressBySession.get(sid)
                );
                failureContextBySession.put(sid, failureSummary);
                response.put("failure_summary", failureSummary);
                response.put("progress_state", exportProgressState(sid));
                return response;
            } catch (TaskTimeoutException ex) {
                attemptIndex += 1;
                lastError = "task_timeout: exceeded " + effectiveTimeout + "s";
                if (attemptIndex >= attempts) {
                    break;
                }
            } catch (RuntimeException ex) {
                attemptIndex += 1;
                lastError = ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
                if (attemptIndex >= attempts) {
                    break;
                }
                if (isRetryableTransportError(ex)) {
                    restart();
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        String failureSummary = buildFailureSummary(
                baseTask,
                lastError == null || lastError.isBlank() ? "unknown browser execution error" : lastError,
                stringValue(lastFailurePage.get("url")),
                stringValue(lastFailurePage.get("title")),
                lastFailureFinal,
                lastFailureScreenshot,
                Math.min(attemptIndex, maxAttempts),
                progressBySession.get(sid)
        );
        failureContextBySession.put(sid, failureSummary);
        result.put("ok", false);
        result.put("session_id", sid);
        result.put("request_id", rid);
        result.put("final", "");
        result.put("page", Map.of("url", "", "title", ""));
        result.put("screenshot", null);
        result.put("error", lastError == null || lastError.isBlank() ? "unknown browser execution error" : lastError);
        result.put("attempt", Math.min(attemptIndex, maxAttempts));
        result.put("failure_summary", failureSummary);
        result.put("progress_state", exportProgressState(sid));
        result.put("timeout_s", effectiveTimeout);
        return result;
    }

    public Map<String, Object> runTaskOnce(String task, String sessionId, String requestId) {
        String workerConversationId = buildWorkerConversationId(sessionId, requestId);
        String taskPrompt = "Session id: " + sessionId + "\n"
                + "Request id: " + requestId + "\n"
                + "Max steps: " + guardrails.getMaxSteps() + "\n"
                + "Max failures: " + guardrails.getMaxFailures() + "\n\n"
                + "Task:\n" + (task == null ? "" : task) + "\n\n"
                + "Perform the task in the current logical browser session/tab for this session id.";
        return executeWorkerTask(taskPrompt, workerConversationId, requestId);
    }

    public void shutdown() {
        stopHeartbeat();
        started = false;
        connectionHealthy = false;
        stopManagedDriver();
    }

    public BrowserTaskProgressState getProgressState(String sessionId) {
        return progressBySession.get(normalizeSessionId(sessionId));
    }

    public void setProgressState(String sessionId, BrowserTaskProgressState progressState) {
        String sid = normalizeSessionId(sessionId);
        if (progressState == null || progressState.isEmpty()) {
            progressBySession.remove(sid);
            return;
        }
        progressBySession.put(sid, progressState);
    }

    public void clearProgressState(String sessionId) {
        progressBySession.remove(normalizeSessionId(sessionId));
    }

    public Map<String, Object> exportProgressState(String sessionId) {
        BrowserTaskProgressState state = getProgressState(sessionId);
        return state == null || state.isEmpty() ? null : state.toMap();
    }

    public void recordWorkerProgress(String sessionId, String requestId, Map<String, Object> parsed) {
        updateProgressFromWorkerResult(normalizeSessionId(sessionId), requestId, parsed);
    }

    public void recordToolProgress(String sessionId, String requestId, String toolName, Object toolResult) {
        String sid = normalizeSessionId(sessionId);
        BrowserTaskProgressState state = getOrCreateProgressState(sid);
        if (requestId != null && !requestId.isBlank()) {
            state.setRequestId(requestId);
        }
        state.setRecentToolSteps(pushRecentToolStep(state.getRecentToolSteps(), summarizeToolResult(toolName, toolResult), 8));
        String[] page = extractPageSnapshot(toolResult);
        if (!page[0].isBlank()) {
            state.setLastPageUrl(page[0]);
        }
        if (!page[1].isBlank()) {
            state.setLastPageTitle(page[1]);
        }
        Object screenshot = extractScreenshotSnapshot(toolResult);
        if (screenshot != null) {
            state.setLastScreenshot(screenshot);
        }
        if ("unknown".equals(state.getStatus())) {
            state.setStatus("partial");
        }
        setProgressState(sid, state);
    }

    public boolean shouldTreatAsCompleted(Map<String, Object> parsed) {
        if (parsed == null) {
            return false;
        }
        if (Boolean.TRUE.equals(parsed.get("ok"))) {
            return true;
        }
        String status = normalizeProgressStatus(firstNonBlank(parsed.get("status"), parsed.get("task_status")));
        if (!"completed".equals(status)) {
            return false;
        }
        Map<String, Object> progress = mapValue(parsed.get("progress"));
        List<String> missing = cleanProgressItems(firstNonNull(progress.get("missing_requirements"), parsed.get("missing_requirements")), 4);
        List<String> evidence = cleanProgressItems(firstNonNull(progress.get("completion_evidence"), parsed.get("completion_evidence")), 4);
        return missing.isEmpty() && (!evidence.isEmpty() || !stringValue(parsed.get("final")).isBlank());
    }

    public static String buildProgressContext(BrowserTaskProgressState state) {
        if (state == null || state.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        if (!state.getStatus().isBlank() && !"unknown".equals(state.getStatus())) {
            lines.add("- Known progress status: " + state.getStatus());
        }
        if (!state.getCompletedSteps().isEmpty()) {
            lines.add("- Completed steps: " + String.join(" | ", state.getCompletedSteps()));
        }
        if (!state.getRemainingSteps().isEmpty()) {
            lines.add("- Remaining steps: " + String.join(" | ", state.getRemainingSteps()));
        }
        if (!state.getNextStep().isBlank()) {
            lines.add("- Next step to try: " + state.getNextStep());
        }
        if (!state.getCompletionEvidence().isEmpty()) {
            lines.add("- Completion evidence observed: " + String.join(" | ", state.getCompletionEvidence()));
        }
        if (!state.getMissingRequirements().isEmpty()) {
            lines.add("- Missing requirements / blockers: " + String.join(" | ", state.getMissingRequirements()));
        }
        if (!state.getRecentToolSteps().isEmpty()) {
            lines.add("- Recent browser tool activity:");
            List<String> recent = state.getRecentToolSteps();
            recent.subList(Math.max(0, recent.size() - 6), recent.size()).forEach(step -> lines.add("  - " + step));
        }
        return lines.isEmpty() ? "" : "Known progress for continuation:\n" + String.join("\n", lines);
    }

    public String buildFailureSummary(
            String task,
            String error,
            String pageUrl,
            String pageTitle,
            String finalText,
            Object screenshot,
            int attempt,
            BrowserTaskProgressState progressState
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Failure summary for continuation:\n");
        builder.append("- Original task: ").append(trim(task, 400, "(empty)")).append('\n');
        builder.append("- Failed attempt: ").append(attempt).append('\n');
        builder.append("- Error: ").append(trim(error, 300, "(unknown)")).append('\n');
        if ((pageUrl != null && !pageUrl.isBlank()) || (pageTitle != null && !pageTitle.isBlank())) {
            builder.append("- Last page: url=")
                    .append(trim(pageUrl, 240, "(unknown)"))
                    .append(", title=")
                    .append(trim(pageTitle, 120, "(unknown)"))
                    .append('\n');
        }
        if (screenshot != null && !String.valueOf(screenshot).isBlank()) {
            builder.append("- Last screenshot: ").append(trim(String.valueOf(screenshot), 200, "")).append('\n');
        }
        String progressContext = buildProgressContext(progressState);
        if (!progressContext.isBlank()) {
            builder.append(progressContext).append('\n');
        }
        if (finalText != null && !finalText.isBlank()) {
            builder.append("- Partial output excerpt:\n").append(trim(finalText, 1200, ""));
        }
        return builder.toString().trim();
    }

    public String getProvider() {
        return provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiBase() {
        return apiBase;
    }

    public String getModelName() {
        return modelName;
    }

    public McpServerConfig getMcpConfig() {
        return mcpConfig;
    }

    public BrowserRunGuardrails getGuardrails() {
        return guardrails;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isConnectionHealthy() {
        return connectionHealthy;
    }

    public Long getLastHeartbeatOk() {
        return lastHeartbeatOk;
    }

    protected Thread getHeartbeatThreadForTest() {
        return heartbeatThread;
    }

    protected void setHeartbeatIntervalMillisForTest(long heartbeatIntervalMillis) {
        this.heartbeatIntervalMillis = Math.max(1L, heartbeatIntervalMillis);
    }

    protected void markInflightTaskForTest(String sessionId, Object task) {
        String sid = normalizeSessionId(sessionId);
        inflightTasks.computeIfAbsent(sid, ignored -> ConcurrentHashMap.newKeySet()).add(task == null ? new Object() : task);
    }

    protected boolean hasInflightTasksForTest() {
        return inflightTasks.values().stream().anyMatch(tasks -> tasks != null && !tasks.isEmpty());
    }

    protected void requestHeartbeatStopForTest() {
        heartbeatStopRequested = true;
    }

    public Path getProfileStorePath() {
        return profileStore.getPath();
    }

    public Object getBrowserAgent() {
        return browserAgent;
    }

    public void setBrowserAgent(Object browserAgent) {
        this.browserAgent = browserAgent;
    }

    protected Map<String, Object> executeWorkerTask(String taskPrompt, String workerConversationId, String requestId) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("url", "");
        page.put("title", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("final", "");
        result.put("page", page);
        result.put("screenshot", null);
        result.put("error", "browser_runtime_worker_not_bound");
        result.put("status", "failed");
        return result;
    }

    protected void restart() {
        resetBrowserRuntime();
        ensureStarted();
    }

    protected void refreshMcpServerBinding() {
        registeredCdpEndpoint = configuredCdpEndpoint();
    }

    protected ManagedBrowserDriver createManagedDriver(BrowserProfile profile) {
        return new ManagedBrowserDriver(profile);
    }

    protected Object getRegisteredBrowserRuntimeClient() {
        return null;
    }

    protected boolean ensureManagedDriverStarted() {
        if (!"managed".equals(driverMode)) {
            return false;
        }
        String previousEndpoint = configuredCdpEndpoint();
        ManagedBrowserDriver current = managedDriver;
        if (current != null && current.isEndpointReady()) {
            return false;
        }
        if (current != null) {
            stopManagedDriver();
        }
        BrowserProfile profile = profileStore.getProfile(profileName);
        if (profile == null
                || !"managed".equals(profile.getDriverType())
                || profile.getDebugPort() <= 0
                || profile.getUserDataDir().isBlank()) {
            profile = buildManagedProfile();
        }
        String configuredBinary = configValue("BROWSER_MANAGED_BINARY");
        if (!configuredBinary.isBlank()) {
            profile.setBrowserBinary(configuredBinary);
        }
        profileStore.upsertProfile(profile, true);
        activeProfile = profile;
        ManagedBrowserDriver driver = createManagedDriver(profile);
        boolean killExisting = truthy(configValue("BROWSER_MANAGED_KILL_EXISTING"));
        String endpoint = driver.start(20.0, killExisting);
        injectCdpEndpoint(endpoint);
        profile.setCdpUrl(endpoint);
        profileStore.upsertProfile(profile, true);
        managedDriver = driver;
        return !endpoint.equals(previousEndpoint) || current != null;
    }

    protected BrowserProfile buildManagedProfile() {
        String host = blankToDefault(configValue("BROWSER_MANAGED_HOST"), "127.0.0.1");
        int port = parsePositiveInt(configValue("BROWSER_MANAGED_PORT"), DEFAULT_MANAGED_PORT);
        boolean killExisting = truthy(configValue("BROWSER_MANAGED_KILL_EXISTING"));
        String explicitUserDataDir = configValue("BROWSER_MANAGED_USER_DATA_DIR");
        String userDataDir;
        if (!explicitUserDataDir.isBlank()) {
            userDataDir = explicitUserDataDir;
        } else if (killExisting) {
            userDataDir = defaultChromeUserDataDir();
        } else {
            userDataDir = mcpCwd.resolve(".browser-profiles").resolve(profileName).toString();
        }
        String endpoint = "http://" + host + ":" + port;
        return new BrowserProfile(
                profileName,
                "managed",
                endpoint,
                configValue("BROWSER_MANAGED_BINARY"),
                userDataDir,
                port,
                host,
                BrowserRuntimeConfig.parseCommandArgs(configValue("BROWSER_MANAGED_ARGS"))
        );
    }

    protected void setStartedForTest(boolean started) {
        this.started = started;
    }

    protected void setManagedDriverForTest(ManagedBrowserDriver managedDriver) {
        this.managedDriver = managedDriver;
    }

    protected ManagedBrowserDriver getManagedDriverForTest() {
        return managedDriver;
    }

    protected void setRegisteredCdpEndpointForTest(String registeredCdpEndpoint) {
        this.registeredCdpEndpoint = registeredCdpEndpoint == null ? "" : registeredCdpEndpoint;
    }

    protected BrowserProfile getActiveProfileForTest() {
        return activeProfile;
    }

    protected static String buildWorkerConversationId(String sessionId, String requestId) {
        String sid = sessionId == null || sessionId.isBlank() ? "browser-session" : sessionId.trim();
        String rid = requestId == null || requestId.isBlank() ? "request" : requestId.trim();
        return sid + ":worker:" + rid + ":" + randomHex();
    }

    private void updateProgressFromWorkerResult(String sessionId, String requestId, Map<String, Object> parsed) {
        if (parsed == null) {
            return;
        }
        BrowserTaskProgressState state = getOrCreateProgressState(sessionId);
        if (requestId != null && !requestId.isBlank()) {
            state.setRequestId(requestId);
        }
        Map<String, Object> progressPayload = mapValue(parsed.get("progress"));
        String status = normalizeProgressStatus(firstNonBlank(parsed.get("status"), parsed.get("task_status")));
        if (status.isBlank()) {
            if (Boolean.TRUE.equals(parsed.get("ok"))) {
                status = "completed";
            } else if (isMaxIterationResult(parsed)) {
                status = "partial";
            } else if (!stringValue(parsed.get("error")).isBlank()) {
                status = "failed";
            } else {
                status = "partial";
            }
        }
        state.setStatus(status);
        List<String> completedSteps = cleanProgressItems(firstNonNull(
                progressPayload.get("completed_steps"), parsed.get("completed_steps")), 8);
        if (!completedSteps.isEmpty()) {
            state.setCompletedSteps(completedSteps);
        }
        List<String> remainingSteps = cleanProgressItems(firstNonNull(
                progressPayload.get("remaining_steps"), parsed.get("remaining_steps")), 8);
        if (!remainingSteps.isEmpty()) {
            state.setRemainingSteps(remainingSteps);
        }
        String nextStep = stringValue(firstNonNull(progressPayload.get("next_step"), parsed.get("next_step")));
        if (!nextStep.isBlank()) {
            state.setNextStep(trim(nextStep, 220, ""));
        }
        List<String> completionEvidence = cleanProgressItems(firstNonNull(
                progressPayload.get("completion_evidence"), parsed.get("completion_evidence")), 6);
        if (!completionEvidence.isEmpty()) {
            state.setCompletionEvidence(completionEvidence);
        }
        List<String> missingRequirements = cleanProgressItems(firstNonNull(
                progressPayload.get("missing_requirements"), parsed.get("missing_requirements")), 6);
        if (!missingRequirements.isEmpty()) {
            state.setMissingRequirements(missingRequirements);
        }
        String[] page = extractPageSnapshot(parsed);
        if (!page[0].isBlank()) {
            state.setLastPageUrl(page[0]);
        }
        if (!page[1].isBlank()) {
            state.setLastPageTitle(page[1]);
        }
        Object screenshot = extractScreenshotSnapshot(parsed);
        if (screenshot != null) {
            state.setLastScreenshot(screenshot);
        }
        String finalText = stringValue(parsed.get("final"));
        if (!finalText.isBlank()) {
            state.setLastWorkerFinal(trim(finalText, 1200, ""));
            if (Boolean.TRUE.equals(parsed.get("ok")) && state.getCompletionEvidence().isEmpty()) {
                state.setCompletionEvidence(List.of(trim(finalText, 220, "")));
            }
        }
        setProgressState(sessionId, state);
    }

    private BrowserTaskProgressState getOrCreateProgressState(String sessionId) {
        return progressBySession.computeIfAbsent(sessionId, ignored -> new BrowserTaskProgressState());
    }

    private void resetBrowserRuntime() {
        started = false;
        registeredCdpEndpoint = "";
        browserAgent = null;
        stopManagedDriver();
    }

    private void stopManagedDriver() {
        ManagedBrowserDriver driver = managedDriver;
        managedDriver = null;
        if (driver != null) {
            driver.stop();
        }
    }

    private void stopHeartbeat() {
        heartbeatStopRequested = true;
        Thread thread = heartbeatThread;
        if (thread != null) {
            thread.interrupt();
            if (thread != Thread.currentThread()) {
                try {
                    thread.join(1_000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static boolean pingClient(Object client) {
        if (client == null) {
            return false;
        }
        try {
            Method method = client.getClass().getDeclaredMethod("ping");
            method.setAccessible(true);
            Object result = method.invoke(client);
            if (result instanceof Boolean value) {
                return value;
            }
            return result != null;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("browser runtime client does not support ping", ex);
        }
    }

    private Path resolveProfileStorePath() {
        String configured = configValue("BROWSER_PROFILE_STORE_PATH");
        if (!configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return mcpCwd.resolve(".browser").resolve("profiles.json").toAbsolutePath().normalize();
    }

    private void injectCdpEndpoint(String endpoint) {
        Map<String, Object> params = new LinkedHashMap<>(mcpConfig.getParams());
        Map<String, String> env = new LinkedHashMap<>();
        Object existingEnv = params.get("env");
        if (existingEnv instanceof Map<?, ?> existingMap) {
            for (Map.Entry<?, ?> entry : existingMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    env.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }
        env.put("PLAYWRIGHT_MCP_CDP_ENDPOINT", endpoint == null ? "" : endpoint.trim());
        env.putIfAbsent("PLAYWRIGHT_MCP_BROWSER", "chrome");
        env.remove("PLAYWRIGHT_MCP_DEVICE");
        params.put("env", env);
        mcpConfig.setParams(params);
    }

    private String configuredCdpEndpoint() {
        Object rawEnv = mcpConfig.getParams().get("env");
        if (!(rawEnv instanceof Map<?, ?> env)) {
            return "";
        }
        Object value = env.get("PLAYWRIGHT_MCP_CDP_ENDPOINT");
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String resolveDriverMode() {
        String value = configValue("BROWSER_DRIVER").toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return "remote";
        }
        if (!List.of("remote", "managed", "extension").contains(value)) {
            throw new IllegalArgumentException("BROWSER_DRIVER must be one of: remote, managed, extension");
        }
        return value;
    }

    private static Path resolveMcpCwd(McpServerConfig config) {
        Object raw = config.getParams().get("cwd");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            return Path.of(String.valueOf(raw)).toAbsolutePath().normalize();
        }
        return Path.of(BrowserRuntimeConfig.resolvePlaywrightMcpCwd()).toAbsolutePath().normalize();
    }

    private static boolean isMaxIterationResult(Map<String, Object> parsed) {
        if (parsed == null) {
            return false;
        }
        if ("max_iterations_reached".equals(stringValue(parsed.get("error")).toLowerCase(Locale.ROOT))) {
            return true;
        }
        String marker = MAX_ITERATION_MESSAGE.toLowerCase(Locale.ROOT);
        return stringValue(parsed.get("final")).toLowerCase(Locale.ROOT).contains(marker)
                || stringValue(parsed.get("error")).toLowerCase(Locale.ROOT).contains(marker);
    }

    private static String buildTaskWithFailureContext(String task, String failureSummary) {
        String base = task == null ? "" : task.trim();
        String summary = failureSummary == null ? "" : failureSummary.trim();
        if (summary.isBlank()) {
            return base;
        }
        return base + "\n\n"
                + "Previous failed attempt context:\n"
                + summary + "\n\n"
                + "Continuation instructions:\n"
                + "- Continue from the current browser state in this same session.\n"
                + "- Do not repeat completed steps unless required for recovery.\n"
                + "- Prioritize resolving the listed failure.";
    }

    private static String buildResumeTask(String task, String previousFinal, String progressContext) {
        String previous = trim(previousFinal, 1200, "");
        List<String> context = new ArrayList<>();
        context.add("Continuation context:");
        context.add("- The previous run reached max iterations before completion.");
        context.add("- Continue from the current browser state in this same session.");
        context.add("- Avoid repeating already completed steps unless needed for recovery.");
        if (progressContext != null && !progressContext.isBlank()) {
            context.add(progressContext);
        }
        if (!previous.isBlank()) {
            context.add("- Previous partial status (may be incomplete):");
            context.add(previous);
        }
        return (task == null ? "" : task.trim()) + "\n\n" + String.join("\n", context);
    }

    private static boolean isRetryableRuntimeResult(Map<String, Object> parsed) {
        if (parsed == null || Boolean.TRUE.equals(parsed.get("ok"))) {
            return false;
        }
        String text = (stringValue(parsed.get("error")) + "\n" + stringValue(parsed.get("final"))).toLowerCase(Locale.ROOT);
        return List.of(
                "frame has been detached",
                "execution context was destroyed",
                "target page, context or browser has been closed",
                "target closed",
                "navigation failed because browser has disconnected",
                "context closed",
                "page crashed",
                "net::err_network_changed",
                "net::err_internet_disconnected"
        ).stream().anyMatch(text::contains);
    }

    private static boolean shouldRestartAfterRuntimeResult(Map<String, Object> parsed) {
        String text = (stringValue(parsed.get("error")) + "\n" + stringValue(parsed.get("final"))).toLowerCase(Locale.ROOT);
        return List.of(
                "frame has been detached",
                "target page, context or browser has been closed",
                "target closed",
                "context closed",
                "page crashed"
        ).stream().anyMatch(text::contains);
    }

    private static boolean isRetryableTransportError(RuntimeException ex) {
        return isRetryableTransportMessage(ex.getClass().getSimpleName()) || isRetryableTransportMessage(ex.getMessage());
    }

    private static boolean isRetryableTransportMessage(String text) {
        String lowered = stringValue(text).toLowerCase(Locale.ROOT);
        return List.of(
                "session terminated",
                "not connected",
                "endofstream",
                "closedresourceerror",
                "brokenresourceerror",
                "stream closed",
                "connection closed",
                "broken pipe",
                "remoteprotocolerror",
                "readerror",
                "writeerror"
        ).stream().anyMatch(lowered::contains);
    }

    private static String normalizeSessionId(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "default" : sessionId.trim();
    }

    private static String newSession(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "browser-" + randomHex() : sessionId.trim();
    }

    private static String normalizeProgressStatus(Object value) {
        String normalized = stringValue(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "complete", "completed", "done" -> "completed";
            case "partial", "in_progress", "in-progress" -> "partial";
            case "blocked" -> "blocked";
            case "failed" -> "failed";
            default -> "";
        };
    }

    private static List<String> cleanProgressItems(Object value, int limit) {
        List<?> candidates;
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            iterable.forEach(items::add);
            candidates = items;
        } else if (value == null) {
            candidates = List.of();
        } else {
            candidates = List.of(value);
        }
        List<String> cleaned = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Object candidate : candidates) {
            String text = stringValue(candidate).replaceAll("\\s+", " ").trim();
            String lowered = text.toLowerCase(Locale.ROOT);
            if (text.isBlank() || seen.contains(lowered)) {
                continue;
            }
            seen.add(lowered);
            cleaned.add(trim(text, 220, ""));
            if (cleaned.size() >= limit) {
                break;
            }
        }
        return cleaned;
    }

    private static List<String> pushRecentToolStep(List<String> existing, String step, int limit) {
        String normalized = stringValue(step).replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return existing == null ? List.of() : new ArrayList<>(existing);
        }
        List<String> updated = new ArrayList<>();
        if (existing != null) {
            existing.stream().filter(item -> !normalized.equals(item)).forEach(updated::add);
        }
        updated.add(normalized);
        return updated.subList(Math.max(0, updated.size() - limit), updated.size());
    }

    private static String summarizeToolResult(String toolName, Object toolResult) {
        String payloadSummary = summarizeObservationPayload(toolResult);
        if (payloadSummary.isBlank()) {
            payloadSummary = "completed";
        }
        return toolName == null || toolName.isBlank() ? payloadSummary : toolName + ": " + payloadSummary;
    }

    private static String summarizeObservationPayload(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = mapValue(raw);
            List<String> parts = new ArrayList<>();
            String error = stringValue(map.get("error"));
            if (!error.isBlank()) {
                parts.add("error=" + trim(error, 140, ""));
            }
            for (String key : List.of("message", "text", "result", "output", "value", "selector")) {
                Object candidate = map.get(key);
                if (candidate == null || candidate instanceof Map<?, ?> || candidate instanceof Iterable<?>) {
                    continue;
                }
                String text = stringValue(candidate);
                if (!text.isBlank()) {
                    parts.add(trim(text, 160, ""));
                    break;
                }
            }
            String[] page = extractPageSnapshot(map);
            if (!page[0].isBlank()) {
                parts.add("url=" + page[0]);
            }
            if (!page[1].isBlank()) {
                parts.add("title=" + page[1]);
            }
            if (Boolean.TRUE.equals(map.get("ok")) && parts.isEmpty()) {
                parts.add("ok");
            }
            if (parts.isEmpty() && !map.isEmpty()) {
                parts.add(String.join(", ", map.keySet().stream().limit(4).toList()));
            }
            return String.join("; ", parts);
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> nested = new ArrayList<>();
            for (Object item : iterable) {
                String text = summarizeObservationPayload(item);
                if (!text.isBlank()) {
                    nested.add(text);
                }
                if (nested.size() >= 2) {
                    break;
                }
            }
            return String.join(" | ", nested);
        }
        return trim(stringValue(value).replaceAll("\\s+", " "), 160, "");
    }

    private static String[] extractPageSnapshot(Object value) {
        Map<String, Object> map = mapValue(value);
        Map<String, Object> page = mapValue(map.get("page"));
        return new String[]{
                trim(firstNonBlank(map.get("url"), page.get("url")), 240, ""),
                trim(firstNonBlank(map.get("title"), page.get("title")), 120, "")
        };
    }

    private static Object extractScreenshotSnapshot(Object value) {
        Map<String, Object> map = mapValue(value);
        Object screenshot = map.get("screenshot");
        return screenshot == null || stringValue(screenshot).isBlank() ? null : screenshot;
    }

    private static Map<String, Object> mapValue(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static Object firstNonNull(Object first, Object second) {
        return first == null ? second : first;
    }

    private static String firstNonBlank(Object first, Object second) {
        String firstValue = stringValue(first);
        return firstValue.isBlank() ? stringValue(second) : firstValue;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String trim(String value, int limit, String fallback) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            return fallback;
        }
        return text.length() <= limit ? text : text.substring(0, limit) + "...[truncated]";
    }

    private static String randomHex() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean truthy(String value) {
        return List.of("1", "true", "yes", "on").contains(stringValue(value).toLowerCase(Locale.ROOT));
    }

    private static int parsePositiveInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(stringValue(raw));
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String configValue(String key) {
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        String env = System.getenv(key);
        return env == null ? "" : env.trim();
    }

    private static String defaultChromeUserDataDir() {
        Path home = Path.of(System.getProperty("user.home", "."));
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String localAppData = blankToDefault(System.getenv("LOCALAPPDATA"),
                    home.resolve("AppData").resolve("Local").toString());
            return Path.of(localAppData).resolve("Google").resolve("Chrome").resolve("User Data").toString();
        }
        if (os.contains("mac")) {
            return home.resolve("Library").resolve("Application Support").resolve("Google").resolve("Chrome").toString();
        }
        return home.resolve(".config").resolve("google-chrome").toString();
    }

    /**
     * Test hook mirroring Python's {@code asyncio.TimeoutError} branch.
     */
    protected static class TaskTimeoutException extends RuntimeException {
        protected TaskTimeoutException(String message) {
            super(message);
        }
    }
}
