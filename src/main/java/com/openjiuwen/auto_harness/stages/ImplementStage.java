package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.schema.CodeChangeArtifact;
import com.openjiuwen.auto_harness.schema.CycleResult;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.auto_harness.schema.TaskStatus;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Implement stage for task-scoped code changes.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.stages.implement}.</p>
 */
public class ImplementStage extends TaskStage {
    private static final Logger LOGGER = Logger.getLogger(ImplementStage.class.getName());
    private static final List<String> ALLOWED_EDIT_PREFIXES = List.of(
            "openjiuwen/harness/",
            "openjiuwen/core/",
            "tests/",
            "examples/",
            "docs/en/",
            "docs/zh/"
    );

    @Override public String name() { return "implement"; }
    @Override
    public StageResult run(Object context) {
        if (context instanceof TaskContext taskContext) {
            StageResult last = new StageResult();
            Iterator<Object> iterator = stream(taskContext);
            while (iterator.hasNext()) {
                Object item = iterator.next();
                if (item instanceof StageResult stageResult) {
                    last = stageResult;
                }
            }
            return last;
        }
        return new StageResult();
    }

    public interface ImplementAgent {
        Iterator<Object> stream(Map<String, Object> inputs, Object session);
    }

    public interface ImplementSession {
        Object preRun(Map<String, Object> kwargs);
        Object postRun();
    }

    public static String buildImplementPrompt(OptimizationTask task, List<Experience> related) {
        List<String> contextParts = new ArrayList<>();
        for (Experience experience : related != null ? related : List.<Experience>of()) {
            contextParts.add("- [" + experience.getType() + "] " + experience.getTopic() + ": "
                    + nullSafe(experience.getSummary()));
        }
        String context = contextParts.isEmpty() ? "\u65e0" : String.join("\n", contextParts);
        String files = task.getFiles() == null || task.getFiles().isEmpty()
                ? "\u81ea\u884c\u5224\u65ad"
                : String.join(", ", task.getFiles());
        return "\u4efb\u52a1: " + nullSafe(task.getTopic()) + "\n"
                + "\u63cf\u8ff0: " + nullSafe(task.getDescription()) + "\n"
                + "\u76ee\u6807\u6587\u4ef6: " + files + "\n"
                + "\n\u76f8\u5173\u7ecf\u9a8c:\n" + context + "\n"
                + "\n" + renderEditScope("\u672c\u8f6e\u5b9e\u73b0\u9636\u6bb5\u5141\u8bb8\u6539\u52a8\u7684\u8def\u5f84") + "\n"
                + "\n\u672c\u9636\u6bb5\u53ea\u5141\u8bb8\u5b8c\u6210\u4ee3\u7801\u4fee\u6539\u4e0e\u5c40\u90e8\u9a8c\u8bc1\u3002"
                + "\n\u9ed8\u8ba4\u76f4\u63a5\u5f00\u59cb\u5b9e\u65bd\u4fee\u6539\uff0c\u4e0d\u8981\u7b49\u5f85\u4eba\u5de5\u786e\u8ba4\u3002"
                + "\n\u7981\u6b62\u8f93\u51fa\u201c\u662f\u5426\u9700\u8981\u6211\u5f00\u59cb\u5b9e\u73b0\u201d"
                + "\u201c\u5982\u679c\u9700\u8981\u8bf7\u6307\u793a\u201d\u201c\u662f\u5426\u7ee7\u7eed\u201d\u4e4b\u7c7b\u7684\u56de\u95ee\uff0c"
                + "\u9664\u975e\u5b58\u5728\u660e\u786e\u8303\u56f4\u51b2\u7a81\u3001\u7f3a\u5c11\u5173\u952e\u8f93\u5165\u6216\u5fc5\u987b\u8d8a\u754c\u7f16\u8f91\uff0c"
                + "\u5426\u5219\u5fc5\u987b\u76f4\u63a5\u52a8\u624b\u4fee\u6539\u4ee3\u7801\u3002"
                + "\n\u5982\u679c `task.files` \u5305\u542b\u8303\u56f4\u5916\u8def\u5f84\uff0c"
                + "\u6216\u4f60\u5224\u65ad\u5fc5\u987b\u4fee\u6539\u8303\u56f4\u5916\u6587\u4ef6\u624d\u80fd\u5b8c\u6210\u4efb\u52a1\uff0c"
                + "\u7acb\u5373\u505c\u6b62\u5e76\u660e\u786e\u62a5\u544a\uff0c\u4e0d\u8981\u5c1d\u8bd5\u8d8a\u754c\u7f16\u8f91\u3002"
                + "\n\u4e25\u7981\u6267\u884c git add\u3001git commit \u6216\u5176\u4ed6\u63d0\u4ea4\u52a8\u4f5c\uff1b"
                + "\u63d0\u4ea4\u53ea\u5141\u8bb8\u5728\u540e\u7eed\u72ec\u7acb commit phase \u4e2d\u8fdb\u884c\u3002";
    }

    public static Map<String, Integer> buildPromptDebugStats(String prompt) {
        String text = prompt != null ? prompt : "";
        return Map.of(
                "chars", text.length(),
                "lines", text.isEmpty() ? 1 : text.split("\n", -1).length,
                "bytes", text.getBytes(StandardCharsets.UTF_8).length
        );
    }

    public static List<String> extractRepoEditCandidates(String statusText, List<String> diffFiles) {
        return extractRepoEditCandidates(statusText, diffFiles, List.of());
    }

    public static List<String> extractRepoEditCandidates(
            String statusText,
            List<String> diffFiles,
            List<String> preexistingDirtyFiles) {
        LinkedHashSet<String> files = new LinkedHashSet<>();
        LinkedHashSet<String> preexisting = new LinkedHashSet<>();
        for (String path : preexistingDirtyFiles != null ? preexistingDirtyFiles : List.<String>of()) {
            String normalized = normalizeRepoPath(path);
            if (!normalized.isBlank()) {
                preexisting.add(normalized);
            }
        }
        for (String line : nullSafe(statusText).split("\\R")) {
            String raw = stripTrailing(line);
            if (raw.length() < 3) {
                continue;
            }
            String path = "";
            if (raw.startsWith("?? ")) {
                path = raw.substring(3).strip();
            } else if (raw.length() >= 4 && raw.charAt(2) == ' ') {
                path = raw.substring(3).strip();
            } else if (raw.length() >= 3 && raw.charAt(1) == ' ') {
                path = raw.substring(2).strip();
            }
            if (path.isBlank()) {
                continue;
            }
            if (path.contains(" -> ")) {
                path = path.split(" -> ", 2)[1].strip();
            }
            String normalized = normalizeRepoPath(path);
            if (!normalized.isBlank()) {
                files.add(normalized);
            }
        }
        for (String path : diffFiles != null ? diffFiles : List.<String>of()) {
            String normalized = normalizeRepoPath(path);
            if (!normalized.isBlank()) {
                files.add(normalized);
            }
        }
        List<String> filtered = new ArrayList<>();
        for (String path : files) {
            if (isAllowedRepoEditPath(path) && !preexisting.contains(path)) {
                filtered.add(path);
            }
        }
        return filtered;
    }

    public static Iterator<Object> runImplementStream(
            ImplementAgent agent,
            OptimizationTask task,
            List<Experience> related) {
        return runImplementStream(agent, task, related, null, null);
    }

    public static Iterator<Object> runImplementStream(
            ImplementAgent agent,
            OptimizationTask task,
            List<Experience> related,
            ImplementSession session,
            String prompt) {
        if (agent == null) {
            LOGGER.warning("No agent, skipping implement");
            return List.<Object>of().iterator();
        }
        String effectivePrompt = prompt != null ? prompt : buildImplementPrompt(task, related);
        Map<String, Object> inputs = Map.of("query", effectivePrompt);
        if (session == null) {
            return agent.stream(inputs, null);
        }
        session.preRun(Map.of("inputs", inputs));
        List<Object> chunks = new ArrayList<>();
        try {
            agent.stream(inputs, session).forEachRemaining(chunks::add);
        } finally {
            session.postRun();
        }
        return chunks.iterator();
    }

    public Iterator<Object> stream(TaskContext ctx) {
        List<Object> items = new ArrayList<>();
        OptimizationTask task = ctx.getTask();
        String prompt = buildImplementPrompt(task, ctx.getRuntime().getRelated());
        Map<String, Integer> promptStats = buildPromptDebugStats(prompt);
        String startedAt = Instant.now().toString();
        double modelTimeoutSecs = ctx.getOrchestrator() != null && ctx.getOrchestrator().getConfig() != null
                ? ctx.getOrchestrator().getConfig().getModelTimeoutSecs()
                : 0.0;
        items.add(TaskContext.message("\u4efb\u52a1\u51c6\u5907\u5c31\u7eea: " + task.getTopic()));
        items.add(TaskContext.message("[1/5] \u6267\u884c\u4ee3\u7801\u4fee\u6539"));

        ImplementAgent agent = adaptAgent(ctx.getRuntime().getTaskAgent());
        ImplementSession session = adaptSession(ctx.getRuntime().getTaskSession());
        String implementError = "";
        Iterator<Object> chunks = runImplementStream(agent, task, ctx.getRuntime().getRelated(), session, prompt);
        while (chunks.hasNext()) {
            Object chunk = chunks.next();
            items.add(chunk);
            implementError = extractControllerTaskFailedError(chunk);
            if (!implementError.isBlank()) {
                break;
            }
        }
        if (!implementError.isBlank()) {
            String error = "Implement model call failed after 0.0s "
                    + "(started_at=" + startedAt
                    + ", prompt_chars=" + promptStats.get("chars")
                    + ", prompt_lines=" + promptStats.get("lines")
                    + ", prompt_bytes=" + promptStats.get("bytes")
                    + ", model_timeout_secs=" + String.format(java.util.Locale.ROOT, "%.1f", modelTimeoutSecs)
                    + ").\n" + implementError;
            task.setStatus(TaskStatus.FAILED);
            items.add(failedResult(ctx, error, List.of()));
            return items.iterator();
        }

        GitOperations git = ctx.getOrchestrator().getGit();
        String statusText = "";
        List<String> diffFiles = List.of();
        try {
            statusText = git.statusPorcelain();
            diffFiles = git.diffNameOnly("HEAD");
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        List<String> editedFiles = extractRepoEditCandidates(
                statusText,
                diffFiles,
                ctx.getRuntime().getPreexistingDirtyFiles()
        );
        if (editedFiles.isEmpty()) {
            String error = "Implement phase finished without any code edits. "
                    + "No allowed repo file was changed according to git status/diff.";
            task.setStatus(TaskStatus.FAILED);
            items.add(failedResult(ctx, error, List.of()));
            return items.iterator();
        }

        StageResult result = new StageResult();
        result.setArtifacts(Map.of(
                "code_change", new CodeChangeArtifact(ctx.getRuntime().getRelated(), editedFiles)
        ));
        items.add(result);
        return items.iterator();
    }

    public static String extractControllerTaskFailedError(Object chunk) {
        if (!(chunk instanceof OutputSchema schema) || !"controller_output".equals(schema.getType())) {
            return "";
        }
        Object payloadObj = schema.getPayload();
        if (!(payloadObj instanceof Map<?, ?> payload)
                || !"task_failed".equals(String.valueOf(payload.get("type")))) {
            return "";
        }
        Object dataObj = payload.get("data");
        List<String> texts = new ArrayList<>();
        if (dataObj instanceof List<?> data) {
            for (Object item : data) {
                if (item instanceof Map<?, ?> map) {
                    Object text = map.get("text");
                    if (text != null && !String.valueOf(text).strip().isBlank()) {
                        texts.add(String.valueOf(text).strip());
                    }
                }
            }
        }
        if (!texts.isEmpty()) {
            return String.join("\n", texts);
        }
        return payload.toString().strip();
    }

    private static StageResult failedResult(TaskContext ctx, String error, List<String> editedFiles) {
        CycleResult cycleResult = new CycleResult();
        cycleResult.setSuccess(false);
        cycleResult.setError(error);
        StageResult result = new StageResult();
        result.setStatus("failed");
        result.setArtifacts(Map.of(
                "code_change", new CodeChangeArtifact(ctx.getRuntime().getRelated(), editedFiles),
                "task_result", cycleResult
        ));
        result.setMessages(List.of(error));
        result.setError(error);
        return result;
    }

    private static ImplementAgent adaptAgent(Object agent) {
        if (agent == null) {
            return null;
        }
        if (agent instanceof ImplementAgent implementAgent) {
            return implementAgent;
        }
        return (inputs, session) -> {
            try {
                Method method = agent.getClass().getMethod("stream", Map.class, Object.class);
                Object result = method.invoke(agent, inputs, session);
                if (result instanceof Iterator<?> iterator) {
                    return (Iterator<Object>) iterator;
                }
            } catch (ReflectiveOperationException ignored) {
                try {
                    Method method = agent.getClass().getMethod("stream", Map.class);
                    Object result = method.invoke(agent, inputs);
                    if (result instanceof Iterator<?> iterator) {
                        return (Iterator<Object>) iterator;
                    }
                } catch (ReflectiveOperationException ignoredAgain) {
                    return List.<Object>of().iterator();
                }
            }
            return List.<Object>of().iterator();
        };
    }

    private static ImplementSession adaptSession(Object session) {
        if (session == null) {
            return null;
        }
        if (session instanceof ImplementSession implementSession) {
            return implementSession;
        }
        return null;
    }

    private static String renderEditScope(String header) {
        return header + ":\n"
                + "- \u6e90\u7801\u8def\u5f84\u53ea\u5141\u8bb8 `openjiuwen/harness/**`\u3001`openjiuwen/core/**`\n"
                + "- `openjiuwen/harness/**`\u3001`openjiuwen/core/**` \u4e0b\u7684 README/Markdown "
                + "\u53ef\u6b63\u5e38\u4fee\u6539\uff0c\u4f8b\u5982 `openjiuwen/harness/cli/README.md`\n"
                + "- \u914d\u5957\u6587\u4ef6\u5141\u8bb8\u65b0\u589e\u6216\u4fee\u6539 `tests/**`\u3001`examples/**`\n"
                + "- \u4ed3\u5e93\u7ea7\u6587\u6863\u53ea\u80fd\u5199\u5165 `docs/en/` \u548c `docs/zh/`\n"
                + "- \u4e0d\u8981\u4fee\u6539 `openjiuwen/auto_harness/**` \u6216\u5176\u4ed6\u6e90\u7801\u76ee\u5f55\n"
                + "- \u5982\u679c\u4efb\u52a1\u5fc5\u987b\u6539\u5230\u8303\u56f4\u5916\u8def\u5f84\uff0c"
                + "\u505c\u6b62\u5e76\u660e\u786e\u62a5\u544a\u8303\u56f4\u51b2\u7a81";
    }

    private static boolean isAllowedRepoEditPath(String path) {
        for (String prefix : ALLOWED_EDIT_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeRepoPath(String path) {
        return path == null ? "" : path.strip().replace('\\', '/');
    }

    private static String stripTrailing(String value) {
        return value == null ? "" : value.stripTrailing();
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
