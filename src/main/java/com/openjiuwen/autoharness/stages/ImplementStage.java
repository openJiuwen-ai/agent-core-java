/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.stages;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.contexts.TaskContext;
import com.openjiuwen.autoharness.infra.EditScope;
import com.openjiuwen.autoharness.schema.CodeChangeArtifact;
import com.openjiuwen.autoharness.schema.CycleResult;
import com.openjiuwen.autoharness.schema.Experience;
import com.openjiuwen.autoharness.schema.ExperienceType;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.StageResult;
import com.openjiuwen.autoharness.schema.TaskStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Public class ImplementStage used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class ImplementStage extends TaskStage {
    private static final Logger LOG = LoggerFactory.getLogger(ImplementStage.class);

    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String name() {
        return "implement";
    }

    /**
     * description.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String description() {
        return "Run the implement stage for PR pipeline.";
    }

    /**
     * produces.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> produces() {
        return List.of("code_change");
    }

    /**
     * run.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    @Override
    public StageResult run(BaseExecutionContext ctx) {
        for (Object event : stream(ctx)) {
            if (event instanceof StageResult result) {
                return result;
            }
        }
        return StageResult.builder().status("failed").error("implement stage did not return StageResult").build();
    }

    /**
     * stream.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            return List.of(failed("implement requires TaskContext", null, List.of()));
        }
        OptimizationTask task = taskContext.getTask();
        List<Experience> related = taskContext.getRuntime().getRelated();
        String prompt = buildImplementPrompt(task, related);
        Map<String, Integer> promptStats = buildPromptDebugStats(prompt);
        String startedAt = Instant.now().toString();
        long startNanos = System.nanoTime();
        double modelTimeoutSecs = taskContext.getOrchestrator().getConfig().getModelTimeoutSecs();
        LOG.info(
                "Implement LLM call starting: task={}, started_at={}, prompt_chars={}, prompt_lines={}, "
                        + "prompt_bytes={}, model_timeout_secs={}",
                TaskContext.taskKey(task), startedAt, promptStats.get("chars"), promptStats.get("lines"),
                promptStats.get("bytes"), modelTimeoutSecs);

        List<Object> events = new ArrayList<>();
        events.add(BaseExecutionContext.message("任务准备就绪: " + TaskContext.taskKey(task)));
        events.add(BaseExecutionContext.message("[1/5] 执行代码修改"));
        String implementError = "";
        for (Object chunk : runImplementStream(taskContext.getRuntime().getTaskAgent(), task, related,
                taskContext.getRuntime().getTaskSession(), prompt)) {
            events.add(chunk);
            implementError = extractControllerTaskFailedError(chunk);
            if (!implementError.isBlank()) {
                break;
            }
        }
        if (!implementError.isBlank()) {
            double elapsedSecs = elapsedSecs(startNanos);
            String error = "Implement model call failed after " + String.format(Locale.ROOT, "%.1f", elapsedSecs)
                    + "s (started_at=" + startedAt + ", prompt_chars=" + promptStats.get("chars") + ", prompt_lines="
                    + promptStats.get("lines") + ", prompt_bytes=" + promptStats.get("bytes") + ", model_timeout_secs="
                    + String.format(Locale.ROOT, "%.1f", modelTimeoutSecs) + ").\n" + implementError;
            events.add(failed(error, taskContext, related));
            return events;
        }

        String logTemplate = "Implement LLM call finished: task={}, elapsed_secs={}, "
                + "prompt_chars={}, prompt_lines={}, prompt_bytes={}";
        LOG.info(logTemplate, TaskContext.taskKey(task), String.format(Locale.ROOT, "%.1f", elapsedSecs(startNanos)),
                promptStats.get("chars"), promptStats.get("lines"), promptStats.get("bytes"));
        List<String> editedFiles = extractRepoEditCandidates(taskContext.getOrchestrator().getGit().statusPorcelain(),
                taskContext.getOrchestrator().getGit().diffNameOnly("HEAD"),
                taskContext.getRuntime().getPreexistingDirtyFiles());
        if (editedFiles.isEmpty()) {
            String error = "Implement phase finished without any code edits. "
                    + "No allowed repo file was changed according to git status/diff.";
            events.add(failed(error, taskContext, related));
            return events;
        }
        events.add(
                StageResult.builder()
                        .artifacts(Map.of("code_change", CodeChangeArtifact.builder()
                                .related(related == null ? List.of() : related).editedFiles(editedFiles).build()))
                        .build());
        return events;
    }

    /**
     * buildImplementPrompt.
     * 
     * @param task task
     * @param related related
     * @return the result
     * @since 0.1.7
     */
    public static String buildImplementPrompt(OptimizationTask task, List<Experience> related) {
        String context = formatExperiences(related);
        return "任务: " + TaskContext.taskKey(task) + "\n" + "描述: " + value(task == null ? "" : task.getDescription())
                + "\n" + "目标文件: " + joinOrDefault(task == null ? List.of() : task.getFiles(), "自行判断") + "\n"
                + "\n相关经验:\n" + context + "\n" + "\n" + EditScope.renderEditScope("本轮实现阶段允许改动的路径") + "\n"
                + "\n本阶段只允许完成代码修改与局部验证。" + "\n默认直接开始实施修改，不要等待人工确认。" + "\n禁止输出“是否需要我开始实现”“如果需要请指示”“是否继续”之类的回问；"
                + "除非存在明确范围冲突、缺少关键输入或必须越界编辑，否则必须直接动手修改代码。" + "\n如果 `task.files` 包含范围外路径，或你判断必须修改范围外文件才能完成任务，"
                + "立即停止并明确报告，不要尝试越界编辑。" + "\n严禁执行 git add、git commit 或其他提交动作；" + "提交只允许在后续独立 commit phase 中进行。";
    }

    /**
     * buildPromptDebugStats.
     * 
     * @param prompt prompt
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Integer> buildPromptDebugStats(String prompt) {
        String value = value(prompt);
        return Map.of("chars", value.length(), "lines", value.isEmpty() ? 1 : value.split("\n", -1).length, "bytes",
                value.getBytes(StandardCharsets.UTF_8).length);
    }

    /**
     * runImplementStream.
     * 
     * @param agent agent
     * @param task task
     * @param related related
     * @return the result
     * @since 0.1.7
     */
    public static List<Object> runImplementStream(Object agent, OptimizationTask task, List<Experience> related) {
        return runImplementStream(agent, task, related, null, null);
    }

    /**
     * runImplementStream.
     * 
     * @param agent agent
     * @param task task
     * @param related related
     * @param session session
     * @param prompt prompt
     * @return the result
     * @since 0.1.7
     */
    public static List<Object> runImplementStream(Object agent, OptimizationTask task, List<Experience> related,
            Object session, String prompt) {
        if (agent == null) {
            LOG.warn("No agent, skipping implement");
            return List.of();
        }
        String effectivePrompt = prompt == null ? buildImplementPrompt(task, related) : prompt;
        if (session != null) {
            invokeSessionMethod(session, "preRun", new Object[]{Map.of("query", effectivePrompt)});
            invokeSessionMethod(session, "pre_run", new Object[]{Map.of("query", effectivePrompt)});
        }
        try {
            return streamAgent(agent, effectivePrompt, session);
        } finally {
            if (session != null) {
                invokeSessionMethod(session, "postRun", new Object[0]);
                invokeSessionMethod(session, "post_run", new Object[0]);
            }
        }
    }

    /**
     * extractRepoEditCandidates.
     * 
     * @param statusText statusText
     * @param diffFiles diffFiles
     * @param preexistingDirtyFiles preexistingDirtyFiles
     * @return the result
     * @since 0.1.7
     */
    public static List<String> extractRepoEditCandidates(String statusText, List<String> diffFiles,
            List<String> preexistingDirtyFiles) {
        List<String> files = new ArrayList<>();
        Set<String> preexisting = new LinkedHashSet<>();
        for (String path : preexistingDirtyFiles == null ? List.<String>of() : preexistingDirtyFiles) {
            String normalized = EditScope.normalizeRepoPath(path);
            if (!normalized.isBlank()) {
                preexisting.add(normalized);
            }
        }
        for (String line : value(statusText).split("\\R")) {
            String raw = line.stripTrailing();
            if (raw.length() < 3) {
                continue;
            }
            String path = "";
            if (raw.startsWith("?? ")) {
                path = raw.substring(3).trim();
            } else if (raw.length() >= 4 && raw.charAt(2) == ' ') {
                path = raw.substring(3).trim();
            } else if (raw.length() >= 3 && raw.charAt(1) == ' ') {
                path = raw.substring(2).trim();
            }
            if (path.isBlank()) {
                continue;
            }
            if (path.contains(" -> ")) {
                path = path.split(" -> ", 2)[1].trim();
            }
            String normalized = EditScope.normalizeRepoPath(path);
            if (!normalized.isBlank()) {
                files.add(normalized);
            }
        }
        for (String path : diffFiles == null ? List.<String>of() : diffFiles) {
            String normalized = EditScope.normalizeRepoPath(path);
            if (!normalized.isBlank()) {
                files.add(normalized);
            }
        }
        List<String> filtered = new ArrayList<>();
        for (String path : new LinkedHashSet<>(files)) {
            if (!EditScope.isAllowedRepoEditPath(path)) {
                continue;
            }
            if (preexisting.contains(path)) {
                continue;
            }
            filtered.add(path);
        }
        return filtered;
    }

    /**
     * failed.
     * 
     * @param error error
     * @param ctx ctx
     * @param related related
     * @return the result
     * @since 0.1.7
     */
    private static StageResult failed(String error, TaskContext ctx, List<Experience> related) {
        if (ctx != null) {
            ctx.getTask().setStatus(TaskStatus.FAILED);
        }
        return StageResult.builder().status("failed").artifacts(Map.of("code_change",
                CodeChangeArtifact.builder().related(related == null ? List.of() : related).editedFiles(List.of())
                        .build(),
                "task_result", CycleResult.builder().isSuccess(false).error(value(error)).build()))
                .messages(List.of(value(error))).error(value(error)).build();
    }

    /**
     * streamAgent.
     * 
     * @param agent agent
     * @param prompt prompt
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> streamAgent(Object agent, String prompt, Object session) {
        try {
            Object stream = invokeStream(agent, prompt, session);
            if (stream instanceof Iterator<?> iterator) {
                List<Object> events = new ArrayList<>();
                while (iterator.hasNext()) {
                    events.add(iterator.next());
                }
                return events;
            }
            if (stream instanceof Iterable<?> iterable) {
                List<Object> events = new ArrayList<>();
                for (Object event : iterable) {
                    events.add(event);
                }
                return events;
            }
        } catch (ReflectiveOperationException ex) {
            return List.of();
        }
        return List.of();
    }

    /**
     * invokeStream.
     * 
     * @param agent agent
     * @param prompt prompt
     * @param session session
     * @return the result
     * @throws ReflectiveOperationException ReflectiveOperationException
     * @since 0.1.7
     */
    private static Object invokeStream(Object agent, String prompt, Object session)
            throws ReflectiveOperationException {
        Map<String, Object> inputs = Map.of("query", prompt);
        if (session != null) {
            try {
                return agent.getClass().getMethod("stream", Map.class, Object.class).invoke(agent, inputs, session);
            } catch (NoSuchMethodException ignored) {
                return agent.getClass().getMethod("stream", Map.class).invoke(agent, inputs);
            }
        }
        return agent.getClass().getMethod("stream", Map.class).invoke(agent, inputs);
    }

    /**
     * invokeSessionMethod.
     * 
     * @param session session
     * @param methodName methodName
     * @param args args
     * @since 0.1.7
     */
    private static void invokeSessionMethod(Object session, String methodName, Object[] args) {
        try {
            if (args.length == 0) {
                session.getClass().getMethod(methodName).invoke(session);
            } else {
                session.getClass().getMethod(methodName, Map.class).invoke(session, args);
            }
        } catch (NoSuchMethodException ignored) {
            // Java Session currently has no Python pre_run/post_run API; use it only when exposed.
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            // Best-effort lifecycle hooks must not block the implement stage.
        }
    }

    /**
     * extractControllerTaskFailedError.
     * 
     * @param chunk chunk
     * @return the result
     * @since 0.1.7
     */
    private static String extractControllerTaskFailedError(Object chunk) {
        if (chunk == null || !"controller_output".equals(String.valueOf(field(chunk, "type")))) {
            return "";
        }
        Object payload = field(chunk, "payload");
        Object payloadType = payload instanceof Map<?, ?> map ? map.get("type") : field(payload, "type");
        if (!"task_failed".equals(String.valueOf(payloadType).toLowerCase(Locale.ROOT))) {
            return "";
        }
        Object payloadData = payload instanceof Map<?, ?> map ? map.get("data") : field(payload, "data");
        List<String> texts = new ArrayList<>();
        if (payloadData instanceof List<?> list) {
            for (Object item : list) {
                Object text = item instanceof Map<?, ?> map ? map.get("text") : field(item, "text");
                String value = String.valueOf(text == null ? "" : text).trim();
                if (!value.isBlank()) {
                    texts.add(value);
                }
            }
        }
        if (!texts.isEmpty()) {
            return String.join("\n", texts);
        }
        return String.valueOf(payload == null ? "" : payload).trim();
    }

    /**
     * field.
     * 
     * @param target target
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    private static Object field(Object target, String name) {
        if (target == null || name == null || name.isBlank()) {
            return null;
        }
        try {
            String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            return target.getClass().getMethod("get" + suffix).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            // Fall back to direct field access for map-like event payload carriers.
        }
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * formatExperiences.
     * 
     * @param related related
     * @return the result
     * @since 0.1.7
     */
    private static String formatExperiences(List<Experience> related) {
        if (related == null || related.isEmpty()) {
            return "无";
        }
        List<String> lines = new ArrayList<>();
        for (Experience exp : related) {
            lines.add("- [" + typeValue(exp.getType()) + "] " + value(exp.getTopic()) + ": " + value(exp.getSummary()));
        }
        return String.join("\n", lines);
    }

    /**
     * typeValue.
     * 
     * @param type type
     * @return the result
     * @since 0.1.7
     */
    private static String typeValue(ExperienceType type) {
        return type == null ? "insight" : type.name().toLowerCase(Locale.ROOT);
    }

    /**
     * joinOrDefault.
     * 
     * @param values values
     * @param fallback fallback
     * @return the result
     * @since 0.1.7
     */
    private static String joinOrDefault(List<String> values, String fallback) {
        return values == null || values.isEmpty() ? fallback : String.join(", ", values);
    }

    /**
     * elapsedSecs.
     * 
     * @param startNanos startNanos
     * @return the result
     * @since 0.1.7
     */
    private static double elapsedSecs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }

    /**
     * value.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String value(String value) {
        return value == null ? "" : value;
    }
}
