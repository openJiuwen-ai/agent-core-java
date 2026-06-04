package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser_move.drivers.ManagedBrowserDriver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Browser backend service with sticky sessions and guardrails.
 *
 * <p>Mirrors Python's {@code BrowserService} in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.service}.</p>
 */
public class BrowserService {

    public static final String MAX_ITERATION_MESSAGE = "Max iterations reached without completion";

    private final String provider;
    private final String apiKey;
    private final String apiBase;
    private final String modelName;
    private final McpServerConfig mcpCfg;
    private final BrowserRunGuardrails guardrails;
    boolean started;
    boolean connectionHealthy = true;
    boolean lastHeartbeatOk = true;
    private CompletableFuture<Void> heartbeatTask;
    private volatile boolean shutdownRequested;
    private final Path mcpCwd;
    private final BrowserProfileStore profileStore;
    private String profileName;
    private String driverMode;
    private BrowserProfile activeProfile;
    private ManagedBrowserDriver managedDriver;
    private String registeredCdpEndpoint = "";
    private Object browserAgent;

    private final Map<String, String> cancelStore = new ConcurrentHashMap<>();
    private final Map<String, String> failureContexts = new ConcurrentHashMap<>();
    private final Map<String, BrowserTaskProgressState> progressStates = new ConcurrentHashMap<>();
    private final Map<String, String> sessionTasks = new ConcurrentHashMap<>();

    public BrowserService(String provider, String apiKey, String apiBase, String modelName, McpServerConfig mcpCfg, BrowserRunGuardrails guardrails) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
        this.mcpCfg = mcpCfg;
        this.guardrails = guardrails;
        this.mcpCwd = resolveMcpCwd(mcpCfg);
        this.profileStore = new BrowserProfileStore(resolveProfileStorePath());
        this.profileName = firstNonBlank(System.getenv("BROWSER_PROFILE_NAME"), "jiuwenclaw");
        this.driverMode = resolveDriverMode();
    }

    public void ensureStarted() {
        ensureRuntimeReady();
        if (this.browserAgent == null) {
            this.browserAgent = new Object();
        }
    }

    public void ensureRuntimeReady() {
        if (started) {
            boolean browserRebound = ensureManagedDriverStarted();
            String configuredEndpoint = configuredCdpEndpoint();
            if (browserRebound || !configuredEndpoint.equals(registeredCdpEndpoint)) {
                refreshMcpServerBinding();
                browserAgent = null;
            }
            return;
        }
        ensureManagedDriverStarted();
        if (!started) {
            started = true;
        }
        registeredCdpEndpoint = configuredCdpEndpoint();
        connectionHealthy = true;
    }

    public synchronized void startHeartbeat() {
        if (heartbeatTask != null && !heartbeatTask.isDone()) {
            return;
        }
        shutdownRequested = false;
        heartbeatTask = CompletableFuture.runAsync(this::heartbeatLoop);
    }

    protected void heartbeatLoop() {
        synchronized (this) {
            if (shutdownRequested) {
                return;
            }
            connectionHealthy = true;
            lastHeartbeatOk = true;
        }
        try {
            Thread.sleep(25L);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    protected boolean ensureManagedDriverStarted() {
        if (!"managed".equals(driverMode)) {
            return false;
        }
        if (managedDriver != null && managedDriver.isEndpointReady()) {
            return false;
        }
        if (managedDriver != null) {
            stopManagedDriver();
        }

        BrowserProfile profile = profileStore.getProfile(profileName);
        if (profile == null
                || !"managed".equals(profile.getDriverType())
                || profile.getDebugPort() <= 0
                || profile.getUserDataDir().isBlank()) {
            profile = buildManagedProfile();
        }
        profileStore.upsertProfile(profile, true);
        activeProfile = profile;

        ManagedBrowserDriver driver = createManagedDriver(profile);
        String endpoint = driver.start();
        injectCdpEndpoint(endpoint);
        profile.setCdpUrl(endpoint);
        profileStore.upsertProfile(profile, true);
        managedDriver = driver;
        return true;
    }

    protected BrowserProfile buildManagedProfile() {
        String host = firstNonBlank(System.getenv("BROWSER_MANAGED_HOST"), "127.0.0.1");
        int port = parsePositiveInt(firstNonBlank(System.getenv("BROWSER_MANAGED_PORT"), "9333"), "BROWSER_MANAGED_PORT");
        String explicitUserDataDir = System.getenv("BROWSER_MANAGED_USER_DATA_DIR");
        String userDataDir = explicitUserDataDir != null && !explicitUserDataDir.trim().isEmpty()
                ? explicitUserDataDir.trim()
                : mcpCwd.resolve(".browser-profiles").resolve(profileName).toString();
        String browserBinary = firstNonBlank(System.getenv("BROWSER_MANAGED_BINARY"), "");
        String cdpUrl = "http://" + host + ":" + port;
        return new BrowserProfile(
                profileName,
                "managed",
                cdpUrl,
                browserBinary,
                userDataDir,
                port,
                host,
                List.of()
        );
    }

    protected ManagedBrowserDriver createManagedDriver(BrowserProfile profile) {
        return new ManagedBrowserDriver(toDriverProfile(profile));
    }

    private static com.openjiuwen.harness.tools.browser_move.drivers.BrowserProfile toDriverProfile(BrowserProfile profile) {
        return new com.openjiuwen.harness.tools.browser_move.drivers.BrowserProfile(
                profile.getName(),
                profile.getDriverType(),
                profile.getCdpUrl(),
                profile.getUserDataDir(),
                profile.getDebugPort(),
                profile.getHost()
        );
    }

    protected void stopManagedDriver() {
        ManagedBrowserDriver driver = managedDriver;
        managedDriver = null;
        if (driver != null) {
            driver.stop();
        }
    }

    protected void restart() {
        restartBrowserRuntime();
    }

    protected void resetBrowserRuntime() {
        started = false;
        registeredCdpEndpoint = "";
        browserAgent = null;
        stopManagedDriver();
    }

    protected void restartBrowserRuntime() {
        resetBrowserRuntime();
        ensureStarted();
    }

    protected void refreshMcpServerBinding() {
        registeredCdpEndpoint = configuredCdpEndpoint();
    }

    protected void injectCdpEndpoint(String endpoint) {
        Map<String, Object> params = new HashMap<>(mcpCfg.getParams() != null ? mcpCfg.getParams() : Map.of());
        Map<String, Object> envMap = new HashMap<>();
        Object env = params.get("env");
        if (env instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                envMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        envMap.put("PLAYWRIGHT_MCP_CDP_ENDPOINT", endpoint);
        envMap.putIfAbsent("PLAYWRIGHT_MCP_BROWSER", "chrome");
        envMap.remove("PLAYWRIGHT_MCP_DEVICE");
        params.put("env", envMap);
        mcpCfg.setParams(params);
    }

    protected String configuredCdpEndpoint() {
        Map<String, Object> params = mcpCfg.getParams() != null ? mcpCfg.getParams() : Map.of();
        Object env = params.get("env");
        if (!(env instanceof Map<?, ?> envMap)) {
            return "";
        }
        Object endpoint = envMap.get("PLAYWRIGHT_MCP_CDP_ENDPOINT");
        return endpoint != null ? String.valueOf(endpoint).trim() : "";
    }

    public synchronized Map<String, Object> runTask(String task, String sessionId, String requestId, Integer timeoutS) {
        ensureStarted();
        String sid = sessionNew(sessionId);
        String rid = requestId != null && !requestId.trim().isEmpty()
                ? requestId.trim()
                : UUID.randomUUID().toString().replace("-", "");
        int effectiveTimeout = timeoutS != null && timeoutS > 0 ? timeoutS : guardrails.getTimeoutS();
        String baseTask = task != null ? task.trim() : "";
        String previousFailureSummary = failureContexts.getOrDefault(sid, "");

        if (isCancelled(sid, rid)) {
            clearCancel(sid, rid);
            clearCancel(sid, null);
            return cancelledResult(sid, rid, 0);
        }

        int attempts = guardrails.isRetryOnce() ? 2 : 1;
        int maxAttempts = attempts + (guardrails.isResumeOnMaxIterations() ? 1 : 0);
        int attemptIdx = 0;
        boolean usedMaxIterationResume = false;
        String nextTask = buildTaskWithFailureContext(baseTask, previousFailureSummary);
        String lastError = null;
        String lastFailureFinal = "";
        Map<String, Object> lastFailurePage = Map.of();
        Object lastFailureScreenshot = null;

        while (attemptIdx < maxAttempts) {
            try {
                Map<String, Object> parsed = runTaskOnce(nextTask, sid, rid, effectiveTimeout);
                attemptIdx++;
                recordWorkerProgress(sid, rid, parsed);

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
                    lastFailurePage = parsed.get("page") instanceof Map<?, ?> page
                            ? mapCopy(page)
                            : Map.of();
                    lastFailureScreenshot = parsed.get("screenshot");
                }

                if (shouldResumeMaxIteration) {
                    usedMaxIterationResume = true;
                    nextTask = buildResumeTask(
                            nextTask,
                            stringValue(parsed.get("final")),
                            buildProgressContext(progressStates.get(sid))
                    );
                    lastError = !lastError.isEmpty() ? lastError : MAX_ITERATION_MESSAGE;
                    continue;
                }

                if (!parsedOk && attemptIdx < attempts && isRetryableRuntimeResult(parsed)) {
                    Map<String, Object> page = parsed.get("page") instanceof Map<?, ?> pageMap
                            ? mapCopy(pageMap)
                            : Map.of();
                    String failureSummary = buildFailureSummary(
                            baseTask,
                            stringValue(parsed.get("error")),
                            stringValue(page.get("url")),
                            stringValue(page.get("title")),
                            stringValue(parsed.get("final")),
                            parsed.get("screenshot"),
                            attemptIdx,
                            progressStates.get(sid)
                    );
                    if (isRetryableTransportMessage(failureSummary) || shouldRestartAfterRuntimeResult(parsed)) {
                        try {
                            restart();
                        } catch (RuntimeException restartException) {
                            lastError = "restart_failed: " + restartException;
                            break;
                        }
                    }
                    nextTask = buildTaskWithFailureContext(baseTask, failureSummary);
                    continue;
                }

                Map<String, Object> response = buildResponse(parsed, sid, rid, attemptIdx);
                if (parsedOk) {
                    failureContexts.remove(sid);
                    progressStates.remove(sid);
                    response.put("failure_summary", null);
                    response.put("progress_state", null);
                    return response;
                }

                Map<String, Object> page = parsed.get("page") instanceof Map<?, ?> pageMap
                        ? mapCopy(pageMap)
                        : Map.of();
                String failureSummary = buildFailureSummary(
                        baseTask,
                        stringValue(parsed.get("error")),
                        stringValue(page.get("url")),
                        stringValue(page.get("title")),
                        stringValue(parsed.get("final")),
                        parsed.get("screenshot"),
                        attemptIdx,
                        progressStates.get(sid)
                );
                failureContexts.put(sid, failureSummary);
                response.put("failure_summary", failureSummary);
                response.put("progress_state", exportProgressState(sid));
                return response;
            } catch (RuntimeException exception) {
                attemptIdx++;
                if (isTimeoutFailure(exception)) {
                    lastError = "task_timeout: exceeded " + effectiveTimeout + "s";
                } else {
                    lastError = !stringValue(exception.getMessage()).isEmpty()
                            ? exception.getMessage()
                            : exception.toString();
                }
                if (attemptIdx >= attempts) {
                    break;
                }
                if (!isTimeoutFailure(exception) && isRetryableTransportError(exception)) {
                    try {
                        restart();
                    } catch (RuntimeException restartException) {
                        lastError = "restart_failed: " + restartException;
                        break;
                    }
                }
            }
        }

        clearCancel(sid, rid);
        clearCancel(sid, null);
        String pageUrl = stringValue(lastFailurePage.get("url"));
        String pageTitle = stringValue(lastFailurePage.get("title"));
        String failureSummary = buildFailureSummary(
                baseTask,
                !stringValue(lastError).isEmpty() ? lastError : "unknown browser execution error",
                pageUrl,
                pageTitle,
                lastFailureFinal,
                lastFailureScreenshot,
                Math.min(attemptIdx, maxAttempts),
                progressStates.get(sid)
        );
        failureContexts.put(sid, failureSummary);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("session_id", sid);
        result.put("request_id", rid);
        result.put("final", "");
        result.put("page", Map.of("url", "", "title", ""));
        result.put("screenshot", null);
        result.put("error", !stringValue(lastError).isEmpty() ? lastError : "unknown browser execution error");
        result.put("attempt", Math.min(attemptIdx, maxAttempts));
        result.put("failure_summary", failureSummary);
        result.put("progress_state", exportProgressState(sid));
        return result;
    }

    protected Map<String, Object> runTaskOnce(String task, String sessionId, String requestId, Integer timeoutS) {
        String workerConversationId = buildWorkerConversationId(sessionId, requestId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("session_id", sessionId);
        result.put("request_id", requestId);
        result.put("worker_conversation_id", workerConversationId);
        result.put("final", task);
        result.put("page", Map.of());
        result.put("screenshot", null);
        result.put("error", null);
        if (timeoutS != null) {
            result.put("timeout_s", timeoutS);
        }
        if (sessionId != null && !sessionId.isEmpty()) {
            sessionTasks.put(sessionId, task);
        }
        return result;
    }

    public String sessionNew(String sessionId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        return sid.isEmpty() ? "browser-" + UUID.randomUUID().toString().replace("-", "") : sid;
    }

    protected String buildWorkerConversationId(String sessionId, String requestId) {
        String sid = sessionId != null && !sessionId.trim().isEmpty() ? sessionId.trim() : "browser-session";
        String rid = requestId != null && !requestId.trim().isEmpty() ? requestId.trim() : "request";
        return sid + ":worker:" + rid + ":" + UUID.randomUUID().toString().replace("-", "");
    }

    protected static String buildTaskWithFailureContext(String task, String failureSummary) {
        String base = task != null ? task.trim() : "";
        String summary = failureSummary != null ? failureSummary.trim() : "";
        if (summary.isEmpty()) {
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

    protected static String buildResumeTask(String task, String previousFinal, String progressContext) {
        String base = task != null ? task.trim() : "";
        String previous = previousFinal != null ? previousFinal.trim() : "";
        if (previous.length() > 1200) {
            previous = previous.substring(0, 1200) + "...[truncated]";
        }
        List<String> contextParts = new ArrayList<>();
        contextParts.add("Continuation context:");
        contextParts.add("- The previous run reached max iterations before completion.");
        contextParts.add("- Continue from the current browser state in this same session.");
        contextParts.add("- Avoid repeating already completed steps unless needed for recovery.");
        if (progressContext != null && !progressContext.trim().isEmpty()) {
            contextParts.add(progressContext.trim());
        }
        if (!previous.isEmpty()) {
            contextParts.add("- Previous partial status (may be incomplete):");
            contextParts.add(previous);
        }
        return base + "\n\n" + String.join("\n", contextParts);
    }

    private static Map<String, Object> cancelledResult(String sessionId, String requestId, int attempt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("session_id", sessionId);
        result.put("request_id", requestId);
        result.put("final", "");
        result.put("page", Map.of("url", "", "title", ""));
        result.put("screenshot", null);
        result.put("error", "cancelled_by_frontend");
        result.put("attempt", attempt);
        result.put("failure_summary", null);
        result.put("progress_state", null);
        return result;
    }

    private static Map<String, Object> buildResponse(Map<String, Object> parsed, String sessionId, String requestId, int attempt) {
        Map<String, Object> page = parsed.get("page") instanceof Map<?, ?> pageMap ? mapCopy(pageMap) : Map.of();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", Boolean.TRUE.equals(parsed.get("ok")));
        response.put("session_id", sessionId);
        response.put("request_id", requestId);
        response.put("final", stringValue(parsed.get("final")));
        response.put("page", Map.of(
                "url", stringValue(page.get("url")),
                "title", stringValue(page.get("title"))
        ));
        response.put("screenshot", parsed.get("screenshot"));
        response.put("error", parsed.get("error"));
        response.put("attempt", attempt);
        response.put("progress_state", null);
        return response;
    }

    private static Map<String, Object> mapCopy(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public void requestCancel(String sessionId, String requestId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            throw new IllegalArgumentException("session_id is required for cancellation");
        }
        String cancelKey = cancelKey(sid, requestId);
        cancelStore.put(cancelKey, "1");
    }

    public void clearCancel(String sessionId, String requestId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return;
        }
        if (requestId != null && !requestId.trim().isEmpty()) {
            cancelStore.remove(cancelKey(sid, requestId));
        } else {
            cancelStore.remove(cancelKey(sid, "*"));
        }
    }

    public boolean isCancelled(String sessionId, String requestId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return false;
        }
        if (requestId != null && !requestId.trim().isEmpty()) {
            if (cancelStore.containsKey(cancelKey(sid, requestId))) {
                return true;
            }
        }
        return cancelStore.containsKey(cancelKey(sid, "*"));
    }

    protected String cancelKey(String sessionId, String requestId) {
        String rid = requestId != null ? requestId.trim() : "*";
        return "cancel:" + sessionId + ":" + rid;
    }

    public void recordToolProgress(String sessionId, String requestId, String toolName, Object toolResult) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return;
        }
        BrowserTaskProgressState state = getOrCreateProgressState(sid);
        String rid = requestId != null ? requestId.trim() : "";
        if (!rid.isEmpty()) {
            state.setRequestId(rid);
        }
        String toolSummary = summarizeToolResult(toolName, toolResult);
        state.setRecentToolSteps(pushRecentToolStep(
                state.getRecentToolSteps(),
                toolSummary,
                8
        ));
        state.setCompletedSteps(pushRecentToolStep(
                state.getCompletedSteps(),
                toolSummary,
                8
        ));
        PageSnapshot page = extractPageSnapshot(toolResult);
        if (!page.url().isEmpty()) {
            state.setLastPageUrl(page.url());
        }
        if (!page.title().isEmpty()) {
            state.setLastPageTitle(page.title());
        }
        Object screenshot = extractScreenshotSnapshot(toolResult);
        if (screenshot != null) {
            state.setLastScreenshot(screenshot);
        }
        if ("unknown".equals(state.getStatus())) {
            state.setStatus("partial");
        }
    }

    public void recordWorkerProgress(String sessionId, String requestId, Map<String, Object> parsed) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty() || parsed == null) {
            return;
        }
        BrowserTaskProgressState state = getOrCreateProgressState(sid);
        String rid = requestId != null ? requestId.trim() : "";
        if (!rid.isEmpty()) {
            state.setRequestId(rid);
        }

        Map<?, ?> progress = parsed.get("progress") instanceof Map<?, ?> map ? map : Map.of();
        String status = normalizeProgressStatus(firstNonBlank(parsed.get("status"), parsed.get("task_status")));
        if (status.isEmpty()) {
            if (Boolean.TRUE.equals(parsed.get("ok"))) {
                status = "completed";
            } else if (isMaxIterationParsed(parsed)) {
                status = "partial";
            } else if (!stringValue(parsed.get("error")).isEmpty()) {
                status = "failed";
            } else {
                status = "partial";
            }
        }
        state.setStatus(status);

        List<String> completedSteps = cleanProgressItems(firstNonNull(progress.get("completed_steps"), parsed.get("completed_steps")), 8);
        if (!completedSteps.isEmpty()) {
            state.setCompletedSteps(completedSteps);
        }
        List<String> remainingSteps = cleanProgressItems(firstNonNull(progress.get("remaining_steps"), parsed.get("remaining_steps")), 8);
        if (!remainingSteps.isEmpty()) {
            state.setRemainingSteps(remainingSteps);
        }
        String nextStep = stringValue(firstNonBlank(progress.get("next_step"), parsed.get("next_step")));
        if (!nextStep.isEmpty()) {
            state.setNextStep(trimText(nextStep, 220));
        }
        List<String> completionEvidence = cleanProgressItems(
                firstNonNull(progress.get("completion_evidence"), parsed.get("completion_evidence")),
                6
        );
        if (!completionEvidence.isEmpty()) {
            state.setCompletionEvidence(completionEvidence);
        }
        List<String> missingRequirements = cleanProgressItems(
                firstNonNull(progress.get("missing_requirements"), parsed.get("missing_requirements")),
                6
        );
        if (!missingRequirements.isEmpty()) {
            state.setMissingRequirements(missingRequirements);
        }

        PageSnapshot page = extractPageSnapshot(parsed);
        if (!page.url().isEmpty()) {
            state.setLastPageUrl(page.url());
        }
        if (!page.title().isEmpty()) {
            state.setLastPageTitle(page.title());
        }
        Object screenshot = extractScreenshotSnapshot(parsed);
        if (screenshot != null) {
            state.setLastScreenshot(screenshot);
        }
        String finalText = stringValue(parsed.get("final"));
        if (!finalText.isEmpty()) {
            state.setLastWorkerFinal(trimText(finalText, 1200));
            if (Boolean.TRUE.equals(parsed.get("ok")) && state.getCompletionEvidence().isEmpty()) {
                state.setCompletionEvidence(List.of(trimText(finalText, 220)));
            }
        }
    }

    public BrowserTaskProgressState getProgressState(String sessionId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return null;
        }
        return progressStates.get(sid);
    }

    protected BrowserTaskProgressState getOrCreateProgressState(String sessionId) {
        return progressStates.computeIfAbsent(sessionId, k -> new BrowserTaskProgressState());
    }

    public Map<String, Object> exportProgressState(String sessionId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return null;
        }
        BrowserTaskProgressState state = progressStates.get(sid);
        if (state == null || state.isEmpty()) {
            return null;
        }
        return state.toDict();
    }

    public void setProgressState(String sessionId, BrowserTaskProgressState progressState) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty() || progressState == null) {
            return;
        }
        if (progressState.isEmpty()) {
            progressStates.remove(sid);
            return;
        }
        progressStates.put(sid, progressState);
    }

    public void clearProgressState(String sessionId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return;
        }
        progressStates.remove(sid);
        sessionTasks.remove(sid);
    }

    public boolean shouldTreatAsCompleted(Map<String, Object> parsed) {
        if (parsed == null) {
            return false;
        }
        String status = normalizeProgressStatus(firstNonBlank(parsed.get("status"), parsed.get("task_status")));
        if (!"completed".equals(status)) {
            return false;
        }
        Map<?, ?> progress = parsed.get("progress") instanceof Map<?, ?> map ? map : Map.of();
        List<String> missing = cleanProgressItems(
                firstNonNull(progress.get("missing_requirements"), parsed.get("missing_requirements")),
                4
        );
        List<String> evidence = cleanProgressItems(
                firstNonNull(progress.get("completion_evidence"), parsed.get("completion_evidence")),
                4
        );
        String finalText = stringValue(parsed.get("final"));
        return missing.isEmpty() && (!evidence.isEmpty() || !finalText.isEmpty());
    }

    public String buildFailureSummary(String task, String error, String pageUrl, String pageTitle, String finalOutput, Object screenshot, int attempt, BrowserTaskProgressState progressState) {
        List<String> lines = new ArrayList<>();
        lines.add("Failure summary for continuation:");
        lines.add("- Original task: " + fallback(trimText(task, 400), "(empty)"));
        lines.add("- Failed attempt: " + attempt);
        lines.add("- Error: " + fallback(trimText(error, 300), "(unknown)"));
        if ((pageUrl != null && !pageUrl.isBlank()) || (pageTitle != null && !pageTitle.isBlank())) {
            lines.add("- Last page: url=" + fallback(trimText(pageUrl, 240), "(unknown)")
                    + ", title=" + fallback(trimText(pageTitle, 120), "(unknown)"));
        }
        String screenshotText = trimText(screenshot, 200);
        if (!screenshotText.isEmpty()) {
            lines.add("- Last screenshot: " + screenshotText);
        }
        String progressContext = buildProgressContext(progressState);
        if (!progressContext.isEmpty()) {
            lines.add(progressContext);
        }
        String finalExcerpt = trimText(finalOutput, 1200);
        if (!finalExcerpt.isEmpty()) {
            lines.add("- Partial output excerpt:");
            lines.add(finalExcerpt);
        }
        return String.join("\n", lines);
    }

    public static String buildProgressContext(BrowserTaskProgressState progressState) {
        if (progressState == null || progressState.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        if (progressState.getStatus() != null && !progressState.getStatus().isEmpty()
                && !"unknown".equals(progressState.getStatus())) {
            lines.add("- Known progress status: " + progressState.getStatus());
        }
        if (progressState.getCompletedSteps() != null && !progressState.getCompletedSteps().isEmpty()) {
            lines.add("- Completed steps: " + String.join(" | ", progressState.getCompletedSteps()));
        }
        if (progressState.getRemainingSteps() != null && !progressState.getRemainingSteps().isEmpty()) {
            lines.add("- Remaining steps: " + String.join(" | ", progressState.getRemainingSteps()));
        }
        if (progressState.getNextStep() != null && !progressState.getNextStep().isEmpty()) {
            lines.add("- Next step to try: " + progressState.getNextStep());
        }
        if (progressState.getCompletionEvidence() != null && !progressState.getCompletionEvidence().isEmpty()) {
            lines.add("- Completion evidence observed: " + String.join(" | ", progressState.getCompletionEvidence()));
        }
        if (progressState.getMissingRequirements() != null && !progressState.getMissingRequirements().isEmpty()) {
            lines.add("- Missing requirements / blockers: " + String.join(" | ", progressState.getMissingRequirements()));
        }
        if (progressState.getRecentToolSteps() != null && !progressState.getRecentToolSteps().isEmpty()) {
            lines.add("- Recent browser tool activity:");
            int start = Math.max(0, progressState.getRecentToolSteps().size() - 6);
            for (String step : progressState.getRecentToolSteps().subList(start, progressState.getRecentToolSteps().size())) {
                lines.add("  - " + step);
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        return "Known progress for continuation:\n" + String.join("\n", lines);
    }

    public String getTaskText(String sessionId) {
        String sid = sessionId != null ? sessionId.trim() : "";
        if (sid.isEmpty()) {
            return "";
        }
        return sessionTasks.getOrDefault(sid, "");
    }

    public void shutdown() {
        shutdownRequested = true;
        if (heartbeatTask != null && !heartbeatTask.isDone()) {
            heartbeatTask.cancel(true);
        }
        stopManagedDriver();
        synchronized (this) {
            started = false;
            connectionHealthy = false;
            lastHeartbeatOk = false;
        }
        cancelStore.clear();
        failureContexts.clear();
        progressStates.clear();
        sessionTasks.clear();
    }

    public String getProvider() { return provider; }
    public String getApiKey() { return apiKey; }
    public String getApiBase() { return apiBase; }
    public String getModelName() { return modelName; }
    public McpServerConfig getMcpCfg() { return mcpCfg; }
    public BrowserRunGuardrails getGuardrails() { return guardrails; }
    public CompletableFuture<Void> getHeartbeatTask() { return heartbeatTask; }
    public boolean isConnectionHealthy() { return connectionHealthy; }
    public boolean isLastHeartbeatOk() { return lastHeartbeatOk; }
    public boolean isStarted() { return started; }
    
    public void setStarted(boolean started) { this.started = started; }
    public Path getMcpCwd() { return mcpCwd; }
    public BrowserProfileStore getProfileStore() { return profileStore; }
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = firstNonBlank(profileName, "jiuwenclaw"); }
    public String getDriverMode() { return driverMode; }
    public void setDriverMode(String driverMode) { this.driverMode = firstNonBlank(driverMode, "remote").toLowerCase(); }
    public BrowserProfile getActiveProfile() { return activeProfile; }
    public ManagedBrowserDriver getManagedDriver() { return managedDriver; }
    public void setManagedDriver(ManagedBrowserDriver managedDriver) { this.managedDriver = managedDriver; }
    public String getRegisteredCdpEndpoint() { return registeredCdpEndpoint; }
    public void setRegisteredCdpEndpoint(String registeredCdpEndpoint) {
        this.registeredCdpEndpoint = registeredCdpEndpoint != null ? registeredCdpEndpoint.trim() : "";
    }
    public Object getBrowserAgent() { return browserAgent; }
    public void setBrowserAgent(Object browserAgent) { this.browserAgent = browserAgent; }

    private Path resolveProfileStorePath() {
        String configured = System.getenv("BROWSER_PROFILE_STORE_PATH");
        if (configured != null && !configured.trim().isEmpty()) {
            return Path.of(configured.trim()).toAbsolutePath().normalize();
        }
        return mcpCwd.resolve(".browser").resolve("profiles.json").toAbsolutePath().normalize();
    }

    private static Path resolveMcpCwd(McpServerConfig mcpCfg) {
        Map<String, Object> params = mcpCfg != null && mcpCfg.getParams() != null ? mcpCfg.getParams() : Map.of();
        String raw = stringValue(params.get("cwd"));
        if (!raw.isEmpty()) {
            return Path.of(raw).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private static String resolveDriverMode() {
        String explicit = stringValue(System.getenv("BROWSER_DRIVER")).toLowerCase();
        if (explicit.isEmpty()) {
            return "remote";
        }
        if (!List.of("remote", "managed", "extension").contains(explicit)) {
            throw new IllegalArgumentException("BROWSER_DRIVER must be one of: remote, managed, extension");
        }
        return explicit;
    }

    private static int parsePositiveInt(String raw, String name) {
        try {
            int value = Integer.parseInt(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Invalid " + name + ": " + raw);
    }

    private static boolean isMaxIterationResult(Map<String, Object> parsed) {
        return isMaxIterationParsed(parsed);
    }

    private static boolean isRetryableRuntimeResult(Map<String, Object> parsed) {
        if (parsed == null || Boolean.TRUE.equals(parsed.get("ok"))) {
            return false;
        }
        String text = (stringValue(parsed.get("error")) + "\n" + stringValue(parsed.get("final"))).toLowerCase();
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
        if (parsed == null) {
            return false;
        }
        String text = (stringValue(parsed.get("error")) + "\n" + stringValue(parsed.get("final"))).toLowerCase();
        return List.of(
                "frame has been detached",
                "target page, context or browser has been closed",
                "target closed",
                "context closed",
                "page crashed"
        ).stream().anyMatch(text::contains);
    }

    private static boolean isRetryableTransportError(Throwable exc) {
        Throwable current = exc;
        while (current != null) {
            String name = current.getClass().getSimpleName();
            String text = stringValue(current.getMessage());
            if (isRetryableTransportMessage(name) || isRetryableTransportMessage(text)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isRetryableTransportMessage(String text) {
        String lowered = stringValue(text).toLowerCase();
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

    private static boolean isTimeoutFailure(Throwable exc) {
        Throwable current = exc;
        while (current != null) {
            if (current instanceof TimeoutException || current instanceof TaskTimeoutException) {
                return true;
            }
            String name = current.getClass().getSimpleName().toLowerCase();
            if (name.contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String normalizeProgressStatus(Object value) {
        String normalized = stringValue(value).toLowerCase();
        return switch (normalized) {
            case "complete", "completed", "done" -> "completed";
            case "partial", "in_progress", "in-progress" -> "partial";
            case "blocked" -> "blocked";
            case "failed" -> "failed";
            default -> "";
        };
    }

    private static List<String> pushRecentToolStep(List<String> existing, String step, int limit) {
        String normalized = String.join(" ", stringValue(step).split("\\s+")).trim();
        List<String> updated = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
        if (normalized.isEmpty()) {
            return updated;
        }
        updated.removeIf(normalized::equals);
        updated.add(normalized);
        int start = Math.max(0, updated.size() - limit);
        return new ArrayList<>(updated.subList(start, updated.size()));
    }

    private static PageSnapshot extractPageSnapshot(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new PageSnapshot("", "");
        }
        Map<?, ?> page = map.get("page") instanceof Map<?, ?> pageMap ? pageMap : Map.of();
        String url = trimText(firstNonBlank(map.get("url"), page.get("url")), 240);
        String title = trimText(firstNonBlank(map.get("title"), page.get("title")), 120);
        return new PageSnapshot(url, title);
    }

    private static Object extractScreenshotSnapshot(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Object screenshot = map.get("screenshot");
        if (screenshot == null || "".equals(screenshot)) {
            return null;
        }
        return screenshot;
    }

    private static String summarizeToolResult(String toolName, Object toolResult) {
        String payloadSummary = summarizeObservationPayload(toolResult);
        if (payloadSummary.isEmpty()) {
            payloadSummary = "completed";
        }
        String name = stringValue(toolName);
        return name.isEmpty() ? payloadSummary : name + ": " + payloadSummary;
    }

    private static String summarizeObservationPayload(Object value) {
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            String errorText = stringValue(map.get("error"));
            if (!errorText.isEmpty()) {
                parts.add("error=" + trimText(errorText, 140));
            }
            for (String key : List.of("message", "text", "result", "output", "value", "selector")) {
                Object candidate = map.get(key);
                if (candidate == null || "".equals(candidate) || candidate instanceof Map<?, ?> || candidate instanceof Iterable<?>) {
                    continue;
                }
                parts.add(trimText(candidate, 160));
                break;
            }
            PageSnapshot page = extractPageSnapshot(map);
            if (!page.url().isEmpty()) {
                parts.add("url=" + page.url());
            }
            if (!page.title().isEmpty()) {
                parts.add("title=" + page.title());
            }
            if (Boolean.TRUE.equals(map.get("ok")) && parts.isEmpty()) {
                parts.add("ok");
            }
            if (parts.isEmpty() && !map.isEmpty()) {
                List<String> keys = new ArrayList<>();
                for (Object key : map.keySet()) {
                    keys.add(String.valueOf(key));
                    if (keys.size() >= 4) {
                        break;
                    }
                }
                parts.add(String.join(", ", keys));
            }
            return String.join("; ", parts);
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> parts = new ArrayList<>();
            for (Object item : iterable) {
                String summary = summarizeObservationPayload(item);
                if (!summary.isEmpty()) {
                    parts.add(summary);
                }
                if (parts.size() >= 2) {
                    break;
                }
            }
            return String.join(" | ", parts);
        }
        return trimText(value, 160);
    }

    private static List<String> cleanProgressItems(Object value, int limit) {
        List<String> result = new ArrayList<>();
        Iterable<?> candidates;
        if (value instanceof Iterable<?> iterable) {
            candidates = iterable;
        } else if (value != null) {
            candidates = List.of(value);
        } else {
            candidates = List.of();
        }
        for (Object item : candidates) {
            String text = trimText(item, 220);
            if (text.isEmpty()) {
                continue;
            }
            boolean seen = result.stream().anyMatch(existing -> existing.equalsIgnoreCase(text));
            if (!seen) {
                result.add(text);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private static boolean isMaxIterationParsed(Map<String, Object> parsed) {
        if ("max_iterations_reached".equalsIgnoreCase(stringValue(parsed.get("error")))) {
            return true;
        }
        String marker = MAX_ITERATION_MESSAGE.toLowerCase();
        return stringValue(parsed.get("final")).toLowerCase().contains(marker)
                || stringValue(parsed.get("error")).toLowerCase().contains(marker);
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static String firstNonBlank(Object first, Object second) {
        String firstValue = stringValue(first);
        return !firstValue.isEmpty() ? firstValue : stringValue(second);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String trimText(Object value, int limit) {
        String text = String.join(" ", stringValue(value).split("\\s+")).trim();
        if (text.length() > limit) {
            return text.substring(0, limit) + "...[truncated]";
        }
        return text;
    }

    public static class TaskTimeoutException extends RuntimeException {
        public TaskTimeoutException(String message) {
            super(message);
        }
    }

    private record PageSnapshot(String url, String title) {
    }
}
