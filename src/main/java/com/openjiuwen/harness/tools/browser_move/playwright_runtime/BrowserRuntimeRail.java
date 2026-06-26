/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.tools.ToolOutput;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rail that makes direct browser sessions resumable and completion-aware.
 *
 * <p>Mirrors Python's {@code BrowserRuntimeRail} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime.py}.</p>
 */
public class BrowserRuntimeRail extends DeepAgentRail {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };
    private static final String BROWSER_PROGRESS_STATE_KEY = "__browser_subagent_progress_state__";
    private static final String BROWSER_PROGRESS_TASK_KEY = "__browser_subagent_last_task__";
    private static final String BROWSER_PROGRESS_SECTION_NAME = "browser_progress_continuation";
    private static final String BROWSER_PROGRESS_FORMAT_SECTION_NAME = "browser_progress_format";
    private static final Pattern BROWSER_PROGRESS_TAG_RE = Pattern.compile(
            "<browser_progress>\\s*(\\{.*?})\\s*</browser_progress>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final String FORMAT_GUIDANCE =
            "When you stop and answer without another browser tool call, append exactly one "
                    + "<browser_progress>{...}</browser_progress> JSON block. "
                    + "Use status=completed only when the requested browser outcome is evidenced. "
                    + "Include compact fields: status, completed_steps, remaining_steps, next_step, "
                    + "completion_evidence, missing_requirements.";

    private final BrowserAgentRuntime runtime;

    public BrowserRuntimeRail(BrowserAgentRuntime runtime) {
        this.runtime = runtime;
        setPriority(83);
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        if (runtime != null) {
            runtime.ensureRuntimeReady();
        }
        if (ctx != null) {
            ctx.put("browser_runtime_ready", true);
            ensureBrowserMcpAbility(ctx);
            AgentSessionApi session = session(ctx);
            if (session == null) {
                return;
            }
            hydrateServiceProgressFromSession(session);
            String taskText = stringValue(query(ctx));
            if (!taskText.isBlank()) {
                session.updateState(Map.of(BROWSER_PROGRESS_TASK_KEY, taskText));
            }
        }
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        AgentSessionApi session = session(ctx);
        SystemPromptBuilder builder = promptBuilder(ctx);
        if (session == null || builder == null) {
            return;
        }

        builder.addSection(new PromptSection(
                BROWSER_PROGRESS_FORMAT_SECTION_NAME,
                Map.of("en", FORMAT_GUIDANCE, "cn", FORMAT_GUIDANCE),
                84
        ));

        BrowserTaskProgressState progressState = loadProgressState(session);
        if (progressState.isEmpty()) {
            builder.removeSection(BROWSER_PROGRESS_SECTION_NAME);
            return;
        }

        String progressContext = BrowserService.buildProgressContext(progressState);
        if (progressContext.isBlank()) {
            builder.removeSection(BROWSER_PROGRESS_SECTION_NAME);
            return;
        }

        String continuationText = progressContext + "\n"
                + "Use this stored browser progress as continuation context. "
                + "Avoid repeating completed actions unless recovery requires it.";
        builder.addSection(new PromptSection(
                BROWSER_PROGRESS_SECTION_NAME,
                Map.of("en", continuationText, "cn", continuationText),
                83
        ));
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        if (ctx == null || runtime == null || runtime.getService() == null) {
            return;
        }
        AgentSessionApi session = session(ctx);
        if (session == null) {
            return;
        }
        String toolName = stringValue(toolName(ctx));
        if (!isBrowserProgressTool(toolName)) {
            return;
        }

        Object toolResult = normalizeToolResult(toolResult(ctx));
        runtime.getService().recordToolProgress(session.getSessionId(), "", toolName, toolResult);
        persistServiceProgressToSession(session);
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        if (ctx != null) {
            ctx.put("browser_runtime_checked", true);
        }
        if (ctx == null || runtime == null || runtime.getService() == null) {
            return;
        }
        AgentSessionApi session = session(ctx);
        Map<String, Object> result = result(ctx);
        if (session == null || result == null) {
            return;
        }

        String sessionId = session.getSessionId();
        hydrateServiceProgressFromSession(session);
        String outputText = stringValue(result.get("output"));
        ProgressPayload extracted = extractProgressPayload(outputText);
        if (!extracted.cleanOutput().equals(outputText)) {
            result.put("output", extracted.cleanOutput());
        }

        if (extracted.payload() != null) {
            Map<String, Object> parsedProgress = buildProgressResult(extracted.payload(), extracted.cleanOutput());
            runtime.getService().recordWorkerProgress(sessionId, "", parsedProgress);
            BrowserTaskProgressState progressState = runtime.getService().getProgressState(sessionId);
            Map<String, Object> exported = runtime.getService().exportProgressState(sessionId);
            if (runtime.getService().shouldTreatAsCompleted(parsedProgress)) {
                result.put("result_type", "answer");
                result.put("progress_state", exported);
                clearProgressState(session);
                return;
            }

            String failureSummary = runtime.getService().buildFailureSummary(
                    loadTaskText(session),
                    stringValue(parsedProgress.get("error")),
                    progressState == null ? "" : progressState.getLastPageUrl(),
                    progressState == null ? "" : progressState.getLastPageTitle(),
                    extracted.cleanOutput(),
                    progressState == null ? null : progressState.getLastScreenshot(),
                    1,
                    progressState
            );
            result.put("result_type", "error");
            result.put("failure_summary", failureSummary);
            result.put("progress_state", exported);
            result.put("output", extracted.cleanOutput().isBlank()
                    ? failureSummary
                    : extracted.cleanOutput() + "\n\n" + failureSummary);
            persistServiceProgressToSession(session);
            return;
        }

        if (isMaxIterationResult(result)) {
            BrowserTaskProgressState progressState = runtime.getService().getProgressState(sessionId);
            String failureSummary = runtime.getService().buildFailureSummary(
                    loadTaskText(session),
                    "max_iterations_reached",
                    progressState == null ? "" : progressState.getLastPageUrl(),
                    progressState == null ? "" : progressState.getLastPageTitle(),
                    extracted.cleanOutput().isBlank() ? outputText : extracted.cleanOutput(),
                    progressState == null ? null : progressState.getLastScreenshot(),
                    1,
                    progressState
            );
            result.put("failure_summary", failureSummary);
            result.put("progress_state", runtime.getService().exportProgressState(sessionId));
            result.put("output", failureSummary);
            persistServiceProgressToSession(session);
            return;
        }

        if ("answer".equalsIgnoreCase(stringValue(result.get("result_type")))) {
            clearProgressState(session);
            return;
        }

        Map<String, Object> exported = runtime.getService().exportProgressState(sessionId);
        if (exported != null) {
            result.put("progress_state", exported);
            persistServiceProgressToSession(session);
        }
    }

    public BrowserAgentRuntime getRuntime() {
        return runtime;
    }

    private void ensureBrowserMcpAbility(CallbackContext ctx) {
        if (ctx == null || runtime == null || runtime.getService() == null) {
            return;
        }
        DeepAgent agent = ctx.getAgent();
        if (agent == null || agent.getAbilityManager() == null) {
            return;
        }
        agent.getAbilityManager().add(runtime.getService().getMcpConfig());
    }

    private void hydrateServiceProgressFromSession(AgentSessionApi session) {
        if (runtime == null || runtime.getService() == null || session == null) {
            return;
        }
        BrowserTaskProgressState progressState = loadProgressState(session);
        if (progressState.isEmpty()) {
            runtime.getService().clearProgressState(session.getSessionId());
            return;
        }
        runtime.getService().setProgressState(session.getSessionId(), progressState);
    }

    private void persistServiceProgressToSession(AgentSessionApi session) {
        if (runtime == null || runtime.getService() == null || session == null) {
            return;
        }
        BrowserTaskProgressState progressState = runtime.getService().getProgressState(session.getSessionId());
        Map<String, Object> exported = runtime.getService().exportProgressState(session.getSessionId());
        Object stateValue = exported != null && !exported.isEmpty()
                ? exported
                : progressState != null && !progressState.isEmpty()
                ? progressState.toMap()
                : Map.of();
        session.updateState(Map.of(BROWSER_PROGRESS_STATE_KEY, stateValue));
    }

    private void clearProgressState(AgentSessionApi session) {
        if (runtime != null && runtime.getService() != null && session != null) {
            runtime.getService().clearProgressState(session.getSessionId());
        }
        if (session != null) {
            session.updateState(Map.of(
                    BROWSER_PROGRESS_STATE_KEY, Map.of(),
                    BROWSER_PROGRESS_TASK_KEY, ""
            ));
        }
    }

    private static BrowserTaskProgressState loadProgressState(AgentSessionApi session) {
        if (session == null) {
            return new BrowserTaskProgressState();
        }
        Object raw = session.getState(BROWSER_PROGRESS_STATE_KEY);
        if (raw instanceof BrowserTaskProgressState progressState) {
            return progressState;
        }
        if (raw instanceof Map<?, ?> map) {
            return BrowserTaskProgressState.fromMap(stringObjectMap(map));
        }
        return new BrowserTaskProgressState();
    }

    private static String loadTaskText(AgentSessionApi session) {
        return session == null ? "" : stringValue(session.getState(BROWSER_PROGRESS_TASK_KEY));
    }

    private static AgentSessionApi session(CallbackContext ctx) {
        Object session = ctx == null ? null : firstNonNull(ctx.get("session"), ctx.get("agent_session"));
        return session instanceof AgentSessionApi api ? api : null;
    }

    private static Object query(CallbackContext ctx) {
        Object inputs = inputs(ctx);
        if (inputs instanceof InvokeInputs invokeInputs) {
            return invokeInputs.getQuery();
        }
        if (inputs instanceof Map<?, ?> map) {
            return firstNonNull(map.get("query"), map.get("task"));
        }
        return ctx == null ? null : firstNonNull(ctx.get("query"), ctx.get("task"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(CallbackContext ctx) {
        Object direct = ctx == null ? null : ctx.get("result");
        if (direct instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Object inputs = inputs(ctx);
        if (inputs instanceof InvokeInputs invokeInputs) {
            return invokeInputs.getResult();
        }
        if (inputs instanceof Map<?, ?> map && map.get("result") instanceof Map<?, ?> resultMap) {
            return (Map<String, Object>) resultMap;
        }
        return null;
    }

    private static Object toolName(CallbackContext ctx) {
        Object inputs = inputs(ctx);
        if (inputs instanceof ToolCallInputs toolCallInputs) {
            return toolCallInputs.getToolName();
        }
        if (inputs instanceof Map<?, ?> map) {
            return firstNonNull(map.get("tool_name"), map.get("toolName"));
        }
        return ctx == null ? null : firstNonNull(ctx.get("tool_name"), ctx.get("toolName"));
    }

    private static Object toolResult(CallbackContext ctx) {
        Object inputs = inputs(ctx);
        if (inputs instanceof ToolCallInputs toolCallInputs) {
            return toolCallInputs.getToolResult();
        }
        if (inputs instanceof Map<?, ?> map) {
            return firstNonNull(map.get("tool_result"), map.get("toolResult"));
        }
        return ctx == null ? null : firstNonNull(ctx.get("tool_result"), ctx.get("toolResult"));
    }

    private static Object inputs(CallbackContext ctx) {
        return ctx == null ? null : ctx.get("inputs");
    }

    private static SystemPromptBuilder promptBuilder(CallbackContext ctx) {
        Object direct = ctx == null ? null : firstNonNull(ctx.get("system_prompt_builder"), ctx.get("prompt_builder"));
        if (direct instanceof SystemPromptBuilder builder) {
            return builder;
        }
        DeepAgent agent = ctx == null ? null : ctx.getAgent();
        if (agent == null || agent.reactAgent() == null) {
            return null;
        }
        try {
            Method getter = agent.reactAgent().getClass().getMethod("getSystemPromptBuilder");
            Object builder = getter.invoke(agent.reactAgent());
            return builder instanceof SystemPromptBuilder promptBuilder ? promptBuilder : null;
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
            return null;
        }
    }

    private static Object normalizeToolResult(Object toolResult) {
        if (toolResult instanceof ToolOutput output) {
            if (output.getData() != null) {
                return output.getData();
            }
            String error = output.getError();
            if (error != null && !error.isBlank()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", false);
                result.put("error", error);
                return result;
            }
        }
        return toolResult;
    }

    private static boolean isBrowserProgressTool(String toolName) {
        String name = stringValue(toolName).toLowerCase();
        if (name.isBlank()) {
            return false;
        }
        if (name.equals("browser_cancel_run")
                || name.equals("browser_clear_cancel")
                || name.equals("browser_list_custom_actions")
                || name.equals("browser_runtime_health")) {
            return false;
        }
        return name.startsWith("browser_") || name.contains(".browser_");
    }

    private static ProgressPayload extractProgressPayload(String outputText) {
        String text = outputText == null ? "" : outputText;
        Matcher matcher = BROWSER_PROGRESS_TAG_RE.matcher(text);
        if (!matcher.find()) {
            return new ProgressPayload(text, null);
        }
        String payloadText = matcher.group(1).trim();
        Map<String, Object> payload;
        try {
            payload = OBJECT_MAPPER.readValue(payloadText, STRING_OBJECT_MAP);
        } catch (JsonProcessingException exception) {
            return new ProgressPayload(text, null);
        }
        String cleaned = BROWSER_PROGRESS_TAG_RE.matcher(text).replaceFirst("").trim();
        return new ProgressPayload(cleaned, payload);
    }

    private static Map<String, Object> buildProgressResult(Map<String, Object> progressPayload, String cleanOutput) {
        String status = stringValue(progressPayload.get("status")).toLowerCase();
        if (status.isBlank()) {
            status = "partial";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", "completed".equals(status));
        result.put("status", status);
        result.put("progress", new LinkedHashMap<>(progressPayload));
        result.put("final", cleanOutput == null ? "" : cleanOutput);
        result.put("error", "completed".equals(status) ? null : "browser_task_incomplete");
        return result;
    }

    private static boolean isMaxIterationResult(Map<String, Object> result) {
        String output = stringValue(result.get("output"));
        String resultType = stringValue(result.get("result_type")).toLowerCase();
        return "error".equals(resultType)
                && output.toLowerCase().contains(BrowserService.MAX_ITERATION_MESSAGE.toLowerCase());
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (map != null) {
            map.forEach((key, value) -> {
                if (key != null) {
                    result.put(String.valueOf(key), value);
                }
            });
        }
        return result;
    }

    private static Object firstNonNull(Object first, Object second) {
        return first == null ? second : first;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ProgressPayload(String cleanOutput, Map<String, Object> payload) {
    }
}
