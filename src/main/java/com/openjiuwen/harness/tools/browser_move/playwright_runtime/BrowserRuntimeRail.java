package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rail that makes direct browser sessions resumable and completion-aware.
 *
 * <p>Mirrors Python's {@code BrowserRuntimeRail} in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.runtime}.</p>
 */
public class BrowserRuntimeRail extends DeepAgentRail {

    private static final String BROWSER_PROGRESS_STATE_KEY = "__browser_subagent_progress_state__";
    private static final String BROWSER_PROGRESS_TASK_KEY = "__browser_subagent_last_task__";
    private static final String BROWSER_PROGRESS_SECTION_NAME = "browser_progress_continuation";
    private static final String BROWSER_PROGRESS_FORMAT_SECTION_NAME = "browser_progress_format";
    private static final Pattern BROWSER_PROGRESS_TAG_RE = Pattern.compile(
            "<browser_progress>\\s*(\\{.*?\\})\\s*</browser_progress>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private static final Map<String, String> BROWSER_PROGRESS_FORMAT_GUIDANCE = Map.of(
            "en", "When you stop and answer without another browser tool call, append exactly one " +
                    "<browser_progress>{...}</browser_progress> JSON block. " +
                    "Use status=completed only when the requested browser outcome is evidenced. " +
                    "Include compact fields: status, completed_steps, remaining_steps, next_step, " +
                    "completion_evidence, missing_requirements.",
            "cn", "当您暂停并回答问题，且未调用其他浏览器工具时，请在后面接上且仅接一个 " +
                    "<browser_progress>{...}</browser_progress> JSON 块。" +
                    "仅在请求的浏览器结果得到验证时才使用 status=completed。 " +
                    "包含以下紧凑字段：status、completed_steps、remaining_steps、next_step、" +
                    "completion_evidence、missing_requirements。"
    );

    private final BrowserAgentRuntime runtime;

    public BrowserRuntimeRail(BrowserAgentRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void init(Object agent) {
        if (agent instanceof DeepAgent) {
            runtime.ensureStarted();
        }
    }

    public BrowserAgentRuntime getRuntime() {
        return runtime;
    }

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        runtime.ensureRuntimeReady();
        ensureBrowserMcpAbility(ctx);
        Session session = ctx.getSession();
        if (session == null) {
            return;
        }
        hydrateServiceProgressFromSession(session);
        String taskText = "";
        if (ctx.getInputs() != null) {
            Object query = getInputAttribute(ctx, "query");
            taskText = query != null ? String.valueOf(query).trim() : "";
        }
        if (!taskText.isEmpty()) {
            session.updateState(Map.of(BROWSER_PROGRESS_TASK_KEY, taskText));
        }
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        Session session = ctx.getSession();
        Object agent = ctx.getAgent();
        Object builder = getAgentAttribute(agent, "systemPromptBuilder");
        if (session == null || builder == null) {
            return;
        }

        addPromptSection(builder, new PromptSection(
                BROWSER_PROGRESS_FORMAT_SECTION_NAME,
                BROWSER_PROGRESS_FORMAT_GUIDANCE,
                84
        ));

        BrowserTaskProgressState progressState = loadProgressState(session);
        if (progressState.isEmpty()) {
            removePromptSection(builder, BROWSER_PROGRESS_SECTION_NAME);
            return;
        }

        String progressContext = BrowserService.buildProgressContext(progressState);
        if (progressContext.isEmpty()) {
            removePromptSection(builder, BROWSER_PROGRESS_SECTION_NAME);
            return;
        }

        String continuationTextEn = progressContext + "\n" +
                "Use this stored browser progress as continuation context. " +
                "Avoid repeating completed actions unless recovery requires it.";
        String continuationTextCn = progressContext + "\n" +
                "将此存储的浏览器进度用作延续上下文。" +
                "除非恢复操作有此需求，否则请避免重复已完成的操作。";

        addPromptSection(builder, new PromptSection(
                BROWSER_PROGRESS_SECTION_NAME,
                Map.of("en", continuationTextEn, "cn", continuationTextCn),
                83
        ));
    }

    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        Session session = ctx.getSession();
        if (session == null) {
            return;
        }
        String toolName = "";
        Object toolNameObj = getInputAttribute(ctx, "toolName");
        if (toolNameObj != null) {
            toolName = String.valueOf(toolNameObj).trim();
        }
        if (!isBrowserProgressTool(toolName)) {
            return;
        }
        Object toolResult = getInputAttribute(ctx, "toolResult");
        Object normalizedResult = normalizeToolResult(toolResult);
        String sessionId = session.getSessionId();
        runtime.getService().recordToolProgress(
                sessionId,
                "",
                toolName,
                normalizedResult
        );
        persistServiceProgressToSession(session);
    }

    @Override
    public void afterInvoke(AgentCallbackContext ctx) {
        Session session = ctx.getSession();
        Object resultObj = getInputAttribute(ctx, "result");
        if (session == null || !(resultObj instanceof Map)) {
            return;
        }

        Map<String, Object> result = (Map<String, Object>) resultObj;
        String sessionId = session.getSessionId();
        hydrateServiceProgressFromSession(session);

        String outputText = result.containsKey("output") ? String.valueOf(result.get("output")) : "";
        ExtractResult extractResult = extractProgressPayload(outputText);
        String cleanOutput = extractResult.cleaned;
        Map<String, Object> progressPayload = extractResult.payload;

        if (!cleanOutput.equals(outputText)) {
            result.put("output", cleanOutput);
        }

        if (progressPayload != null) {
            Map<String, Object> parsedProgress = buildProgressResult(progressPayload, cleanOutput);
            runtime.getService().recordWorkerProgress(
                    sessionId,
                    "",
                    parsedProgress
            );
            BrowserTaskProgressState progressState = runtime.getService().getProgressState(sessionId);
            Map<String, Object> exported = runtime.getService().exportProgressState(sessionId);

            if (runtime.getService().shouldTreatAsCompleted(parsedProgress)) {
                result.put("result_type", "answer");
                result.put("progress_state", exported);
                clearProgressState(session);
                return;
            }

            String taskText = loadTaskText(session);
            String error = parsedProgress.containsKey("error") && parsedProgress.get("error") != null
                    ? String.valueOf(parsedProgress.get("error"))
                    : "browser_task_incomplete";
            String pageUrl = progressState != null ? progressState.getNextStep() : "";
            String pageTitle = "";
            Object screenshot = progressState != null ? null : null;

            String failureSummary = runtime.getService().buildFailureSummary(
                    taskText,
                    error,
                    pageUrl,
                    pageTitle,
                    cleanOutput,
                    screenshot,
                    1,
                    progressState
            );
            result.put("result_type", "error");
            result.put("failure_summary", failureSummary);
            result.put("progress_state", exported);
            result.put("output", cleanOutput.isEmpty() ? failureSummary : cleanOutput + "\n\n" + failureSummary);
            persistServiceProgressToSession(session);
            return;
        }

        if (isMaxIterationResult(result)) {
            BrowserTaskProgressState progressState = runtime.getService().getProgressState(sessionId);
            String taskText = loadTaskText(session);
            String failureSummary = runtime.getService().buildFailureSummary(
                    taskText,
                    "max_iterations_reached",
                    progressState != null ? progressState.getNextStep() : "",
                    "",
                    cleanOutput.isEmpty() ? outputText : cleanOutput,
                    null,
                    1,
                    progressState
            );
            result.put("failure_summary", failureSummary);
            result.put("progress_state", runtime.getService().exportProgressState(sessionId));
            result.put("output", failureSummary);
            persistServiceProgressToSession(session);
            return;
        }

        String resultType = result.containsKey("result_type")
                ? String.valueOf(result.get("result_type")).toLowerCase()
                : "";
        if ("answer".equals(resultType)) {
            clearProgressState(session);
            return;
        }

        Map<String, Object> exported = runtime.getService().exportProgressState(sessionId);
        if (exported != null) {
            result.put("progress_state", exported);
            persistServiceProgressToSession(session);
        }
    }

    protected static Object normalizeToolResult(Object toolResult) {
        if (toolResult == null) {
            return null;
        }
        if (hasToolOutputAttributes(toolResult)) {
            Object data = getAttribute(toolResult, "data");
            if (data != null) {
                return data;
            }
            String error = String.valueOf(getAttribute(toolResult, "error"));
            if (error != null && !error.trim().isEmpty()) {
                return Map.of("ok", false, "error", error);
            }
        }
        return toolResult;
    }

    protected static boolean hasToolOutputAttributes(Object obj) {
        try {
            return obj.getClass().getMethod("getData") != null && obj.getClass().getMethod("getSuccess") != null;
        } catch (NoSuchMethodException e) {
            try {
                return obj.getClass().getField("data") != null && obj.getClass().getField("success") != null;
            } catch (NoSuchFieldException ex) {
                return false;
            }
        }
    }

    protected static Object getAttribute(Object obj, String name) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod("get" + name.substring(0, 1).toUpperCase() + name.substring(1));
            return method.invoke(obj);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = obj.getClass().getField(name);
                return field.get(obj);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    protected static Object getInputAttribute(AgentCallbackContext ctx, String name) {
        Object inputs = ctx.getInputs();
        if (inputs == null) {
            return null;
        }
        try {
            if (inputs instanceof Map) {
                return ((Map<?, ?>) inputs).get(name);
            }
            java.lang.reflect.Method method = inputs.getClass().getMethod("get" + name.substring(0, 1).toUpperCase() + name.substring(1));
            return method.invoke(inputs);
        } catch (Exception e) {
            return null;
        }
    }

    protected static Object getAgentAttribute(Object agent, String name) {
        if (agent == null) {
            return null;
        }
        try {
            java.lang.reflect.Method method = agent.getClass().getMethod("get" + name.substring(0, 1).toUpperCase() + name.substring(1));
            return method.invoke(agent);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = agent.getClass().getField(name);
                return field.get(agent);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    protected static void addPromptSection(Object builder, PromptSection section) {
        if (builder == null) {
            return;
        }
        try {
            java.lang.reflect.Method method = builder.getClass().getMethod("addSection", PromptSection.class);
            method.invoke(builder, section);
        } catch (Exception e) {
        }
    }

    protected static void removePromptSection(Object builder, String sectionName) {
        if (builder == null) {
            return;
        }
        try {
            java.lang.reflect.Method method = builder.getClass().getMethod("removeSection", String.class);
            method.invoke(builder, sectionName);
        } catch (Exception e) {
        }
    }

    protected static boolean isBrowserProgressTool(String toolName) {
        String name = toolName != null ? toolName.trim().toLowerCase() : "";
        if (name.isEmpty()) {
            return false;
        }
        Set<String> excludedTools = Set.of(
                "browser_cancel_run",
                "browser_clear_cancel",
                "browser_list_custom_actions",
                "browser_runtime_health"
        );
        if (excludedTools.contains(name)) {
            return false;
        }
        return name.startsWith("browser_") || name.contains(".browser_");
    }

    protected static ExtractResult extractProgressPayload(String outputText) {
        String text = outputText != null ? outputText : "";
        Matcher matcher = BROWSER_PROGRESS_TAG_RE.matcher(text);
        if (!matcher.find()) {
            return new ExtractResult(text, null);
        }
        String payloadText = matcher.group(1).trim();
        Map<String, Object> payload = null;
        try {
            payload = parseJsonMap(payloadText);
        } catch (Exception e) {
            return new ExtractResult(text, null);
        }
        String cleaned = matcher.replaceFirst("").trim();
        return new ExtractResult(cleaned, payload);
    }

    protected static Map<String, Object> parseJsonMap(String jsonText) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(jsonText, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    protected static Map<String, Object> buildProgressResult(Map<String, Object> progressPayload, String cleanOutput) {
        String status = progressPayload.containsKey("status")
                ? String.valueOf(progressPayload.get("status")).trim().toLowerCase()
                : "partial";
        if (status.isEmpty()) {
            status = "partial";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", "completed".equals(status));
        result.put("status", status);
        result.put("progress", progressPayload);
        result.put("final", cleanOutput);
        result.put("error", "completed".equals(status) ? null : "browser_task_incomplete");
        return result;
    }

    protected static boolean isMaxIterationResult(Map<String, Object> result) {
        String output = result.containsKey("output") ? String.valueOf(result.get("output")).trim() : "";
        String resultType = result.containsKey("result_type")
                ? String.valueOf(result.get("result_type")).toLowerCase()
                : "";
        return "error".equals(resultType) && BrowserService.MAX_ITERATION_MESSAGE.toLowerCase().contains(output.toLowerCase());
    }

    protected static String loadTaskText(Session session) {
        if (session == null) {
            return "";
        }
        Object taskObj = session.getState(BROWSER_PROGRESS_TASK_KEY);
        return taskObj != null ? String.valueOf(taskObj).trim() : "";
    }

    protected static BrowserTaskProgressState loadProgressState(Session session) {
        if (session == null) {
            return new BrowserTaskProgressState();
        }
        Object stateObj = session.getState(BROWSER_PROGRESS_STATE_KEY);
        if (stateObj instanceof Map) {
            return BrowserTaskProgressState.fromDict((Map<String, Object>) stateObj);
        }
        return new BrowserTaskProgressState();
    }

    protected void hydrateServiceProgressFromSession(Session session) {
        if (session == null) {
            return;
        }
        String sessionId = session.getSessionId();
        BrowserTaskProgressState progressState = loadProgressState(session);
        if (progressState.isEmpty()) {
            runtime.getService().clearProgressState(sessionId);
        } else {
            runtime.getService().setProgressState(sessionId, progressState);
        }
    }

    protected void persistServiceProgressToSession(Session session) {
        if (session == null) {
            return;
        }
        String sessionId = session.getSessionId();
        Map<String, Object> exported = runtime.getService().exportProgressState(sessionId);
        BrowserTaskProgressState progressState = runtime.getService().getProgressState(sessionId);

        Map<String, Object> stateUpdate = new LinkedHashMap<>();
        Object stateValue = exported instanceof Map && !((Map<?, ?>) exported).isEmpty()
                ? exported
                : progressState != null && !progressState.isEmpty()
                        ? progressState.toDict()
                        : Map.of();
        stateUpdate.put(BROWSER_PROGRESS_STATE_KEY, stateValue);
        session.updateState(stateUpdate);
    }

    protected void clearProgressState(Session session) {
        if (session == null) {
            return;
        }
        String sessionId = session.getSessionId();
        runtime.getService().clearProgressState(sessionId);
        session.updateState(Map.of(
                BROWSER_PROGRESS_STATE_KEY, Map.of(),
                BROWSER_PROGRESS_TASK_KEY, ""
        ));
    }

    protected void ensureBrowserMcpAbility(AgentCallbackContext ctx) {
        Object agent = ctx.getAgent();
        Object abilityManager = getAgentAttribute(agent, "abilityManager");
        if (abilityManager == null) {
            return;
        }
        try {
            java.lang.reflect.Method addMethod = abilityManager.getClass().getMethod("add", Object.class);
            addMethod.invoke(abilityManager, runtime.getService().getMcpCfg());
        } catch (Exception e) {
        }
    }

    protected static class ExtractResult {
        final String cleaned;
        final Map<String, Object> payload;

        ExtractResult(String cleaned, Map<String, Object> payload) {
            this.cleaned = cleaned;
            this.payload = payload;
        }
    }
}