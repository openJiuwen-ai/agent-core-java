/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.stages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.contexts.TaskContext;
import com.openjiuwen.autoharness.factory.AutoHarnessFactory;
import com.openjiuwen.autoharness.infra.Parsers;
import com.openjiuwen.autoharness.schema.CommitArtifact;
import com.openjiuwen.autoharness.schema.CommitFacts;
import com.openjiuwen.autoharness.schema.CycleResult;
import com.openjiuwen.autoharness.schema.Experience;
import com.openjiuwen.autoharness.schema.ExperienceType;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.PullRequestArtifact;
import com.openjiuwen.autoharness.schema.PullRequestDraft;
import com.openjiuwen.autoharness.schema.StageResult;
import com.openjiuwen.autoharness.schema.TaskStatus;
import com.openjiuwen.autoharness.schema.VerifyReportArtifact;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Public class PublishPrStage used by the Java parity implementation.
 *
 * @since 1.0
 */
public class PublishPrStage extends TaskStage {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int PR_DRAFT_MAX_ATTEMPTS = 2;

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String name() {
        return "publish_pr";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String description() {
        return "Push branch and create PR when configured.";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> consumes() {
        return List.of("verify_report", "commit_result");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> produces() {
        return List.of("pull_request", "task_result");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public StageResult run(BaseExecutionContext ctx) {
        for (Object event : stream(ctx)) {
            if (event instanceof StageResult result) {
                return result;
            }
        }
        return StageResult.builder()
                .status("failed")
                .error("publish_pr stage did not return StageResult")
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            return List.of(failed("publish_pr requires TaskContext", null));
        }
        Object verifyRaw = taskContext.requireArtifact("verify_report");
        Object commitRaw = taskContext.requireArtifact("commit_result");
        VerifyReportArtifact verifyReport = verifyRaw instanceof VerifyReportArtifact v
                ? v
                : VerifyReportArtifact.builder().build();
        CommitArtifact commitResult = commitRaw instanceof CommitArtifact c
                ? c
                : CommitArtifact.builder().error("commit result missing").build();
        if (!commitResult.isCommitted() || commitResult.getFacts() == null) {
            String error = valueOrDefault(commitResult.getError(), "commit result missing");
            taskContext.getTask().setStatus(TaskStatus.FAILED);
            return List.of(failed(error, "发布 PR 失败: " + error));
        }

        String prUrl = "";
        List<String> messages = new ArrayList<>();
        List<Object> events = new ArrayList<>();
        PullRequestDraft draft = PullRequestDraft.builder().build();
        if (shouldCreatePr(taskContext)) {
            DraftGenerationResult draftResult = generatePrDraft(taskContext, commitResult, verifyReport, events);
            draft = draftResult.draft() != null ? draftResult.draft() : PullRequestDraft.builder().build();
            if (!hasText(draft.getTitle()) || !hasText(draft.getBody())) {
                String error = "PR draft generation failed after " + PR_DRAFT_MAX_ATTEMPTS + " attempts: "
                        + valueOrDefault(draftResult.error(),
                        "communicate agent did not return a valid JSON draft.");
                taskContext.getTask().setStatus(TaskStatus.FAILED);
                events.add(failed(error, "发布 PR 失败: " + error));
                return events;
            }
            messages.add("PR draft 已生成: " + draft.getTitle());
        }
        if (hasText(taskContext.getOrchestrator().getConfig().getGitRemote())) {
            events.add(BaseExecutionContext.message("[后置] 推送分支"));
            taskContext.getOrchestrator().getGit().push(commitResult.getBranchName());
            if (shouldCreatePr(taskContext)) {
                events.add(BaseExecutionContext.message("[后置] 创建 PR"));
                Map<String, Object> prResult = taskContext.getOrchestrator().getGit().createPr(
                        draft.getTitle(),
                        draft.getBody(),
                        commitResult.getBranchName()
                );
                prUrl = String.valueOf(prResult.getOrDefault("pr_url", ""));
                if (hasText(prUrl)) {
                    messages.add("PR 已创建: " + prUrl);
                }
            }
            messages.add("[后置] 推送分支");
        }

        String completionSummary = buildCompletionSummary(
                taskContext.getTask(),
                commitResult,
                verifyReport,
                prUrl
        );
        taskContext.getTask().setStatus(TaskStatus.SUCCESS);
        recordSuccessExperience(taskContext, prUrl);
        CycleResult result = CycleResult.builder()
                .isSuccess(true)
                .summary(completionSummary)
                .prUrl(prUrl)
                .build();
        messages.addAll(List.of(
                "任务总结: " + completionSummary,
                hasText(prUrl) ? "任务完成: " + prUrl : "任务完成（本地提交）"
        ));
        events.add(StageResult.builder()
                .artifacts(Map.of(
                        "pull_request", PullRequestArtifact.builder()
                                .prUrl(prUrl)
                                .summary(completionSummary)
                                .build(),
                        "task_result", result
                ))
                .messages(messages)
                .build());
        return events;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String buildCompletionSummary(OptimizationTask task, CommitArtifact commitResult,
                                                VerifyReportArtifact verifyReport, String prUrl) {
        String ciSummary = formatCiSummary(verifyReport == null ? Map.of() : verifyReport.getCiResult());
        List<String> allowedFiles = commitResult != null && commitResult.getFacts() != null
                ? commitResult.getFacts().getAllowedFiles()
                : List.of();
        String changedFiles = allowedFiles == null || allowedFiles.isEmpty()
                ? "无"
                : String.join(", ", allowedFiles.subList(0, Math.min(5, allowedFiles.size())));
        String suffix = allowedFiles != null && allowedFiles.size() > 5
                ? " 等 " + allowedFiles.size() + " 个文件"
                : "";
        String location = hasText(prUrl) ? prUrl : "本地提交";
        return TaskContext.taskKey(task) + ": 已完成；CI=" + ciSummary + "；变更文件="
                + changedFiles + suffix + "；交付=" + location;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Experience buildSuccessExperience(OptimizationTask task, String prUrl) {
        return Experience.builder()
                .type(ExperienceType.OPTIMIZATION)
                .topic(TaskContext.taskKey(task))
                .summary("completed: " + TaskContext.taskKey(task))
                .outcome("success")
                .prUrl(value(prUrl))
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String buildPrDraftQuery(TaskContext ctx, CommitFacts facts, Map<String, Object> ciResult,
                                           String lastCommitStat, String validationError, String previousOutput) {
        String relatedText = relatedText(ctx);
        String repairText = "";
        if (hasText(validationError)) {
            repairText = "\n\n上一次 PR draft 校验失败原因:\n"
                    + validationError + "\n\n"
                    + "上一次输出如下，请修正为合法的完整 GitCode 模板，"
                    + "不要重复输出简化版格式，也不要省略 HTML 注释和标准 checklist：\n"
                    + valueOrDefault(previousOutput, "无") + "\n";
        }
        OptimizationTask task = ctx.getTask();
        return "任务主题: " + TaskContext.taskKey(task) + "\n"
                + "任务描述: " + valueOrDefault(task == null ? "" : task.getDescription(), "无") + "\n"
                + "预期效果: " + valueOrDefault(task == null ? "" : task.getExpectedEffect(), "无") + "\n"
                + "关联 issue: " + valueOrDefault(task == null ? "" : task.getIssueRef(), "无") + "\n"
                + "允许提交文件: " + joinOrNone(facts == null ? List.of() : facts.getAllowedFiles()) + "\n"
                + "本轮实际修改: " + joinOrNone(facts == null ? List.of() : facts.getEditedFiles()) + "\n"
                + "diff 统计:\n" + valueOrDefault(facts == null ? "" : facts.getDiffStat(), "无") + "\n\n"
                + "最近一次提交摘要:\n" + valueOrDefault(lastCommitStat, "无") + "\n\n"
                + "验证结果(JSON):\n" + json(ciResult == null ? Map.of() : ciResult) + "\n\n"
                + "相关经验:\n" + relatedText + "\n\n"
                + repairText
                + "请基于这些事实，生成可直接提交到 GitCode 的 PR draft。";
    }

    private static StageResult failed(String error, String message) {
        List<String> messages = message == null ? List.of() : List.of(message);
        return StageResult.builder()
                .status("failed")
                .artifacts(Map.of("task_result", CycleResult.builder()
                        .isSuccess(false)
                        .error(value(error))
                        .build()))
                .messages(messages)
                .error(value(error))
                .build();
    }

    private static DraftGenerationResult generatePrDraft(TaskContext ctx, CommitArtifact commitResult,
                                                        VerifyReportArtifact verifyReport, List<Object> events) {
        String draftError = "";
        String previousOutput = "";
        DraftGenerationResult result = new DraftGenerationResult(null, "", "");
        for (int attempt = 1; attempt <= PR_DRAFT_MAX_ATTEMPTS; attempt++) {
            events.add(BaseExecutionContext.message(attempt == 1
                    ? "[后置] 生成 PR draft"
                    : "[后置] 修正 PR draft (" + attempt + "/" + PR_DRAFT_MAX_ATTEMPTS + ")"));
            String query = buildPrDraftQuery(
                    ctx,
                    commitResult.getFacts(),
                    verifyReport == null ? Map.of() : verifyReport.getCiResult(),
                    commitResult.getLastCommitStat(),
                    draftError,
                    previousOutput
            );
            String output = "";
            for (Object chunk : streamAgent(prDraftAgent(ctx), query)) {
                events.add(chunk);
                output += Parsers.extractText(chunk);
            }
            Parsers.PullRequestDraftParseResult parsed = Parsers.parsePrDraftWithError(output);
            result = new DraftGenerationResult(parsed.draft(), parsed.error(), output);
            draftError = parsed.error();
            previousOutput = output;
            if (parsed.draft() != null
                    && hasText(parsed.draft().getTitle())
                    && hasText(parsed.draft().getBody())) {
                return result;
            }
        }
        return result;
    }

    private static Object prDraftAgent(TaskContext ctx) {
        return AutoHarnessFactory.createPrDraftAgent(
                ctx.getOrchestrator().getConfig(),
                ctx.getRuntime() == null ? "" : ctx.getRuntime().getWtPath()
        );
    }

    private static List<Object> streamAgent(Object agent, String prompt) {
        if (agent == null) {
            return List.of();
        }
        try {
            Object stream = agent.getClass()
                    .getMethod("stream", Map.class)
                    .invoke(agent, Map.of("query", prompt));
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
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
        return List.of();
    }

    private static boolean shouldCreatePr(TaskContext ctx) {
        return hasText(ctx.getOrchestrator().getConfig().getGitRemote())
                && hasText(ctx.getOrchestrator().getConfig().getForkOwner());
    }

    private static void recordSuccessExperience(TaskContext ctx, String prUrl) {
        try {
            ctx.getOrchestrator().getExperienceStore().record(buildSuccessExperience(ctx.getTask(), prUrl));
        } catch (IOException ignored) {
            // Python records successful delivery experience but does not fail the stage on persistence errors.
        }
    }

    private static String relatedText(TaskContext ctx) {
        if (ctx == null || ctx.getRuntime() == null || ctx.getRuntime().getRelated() == null
                || ctx.getRuntime().getRelated().isEmpty()) {
            return "无";
        }
        List<String> lines = new ArrayList<>();
        ctx.getRuntime().getRelated().stream().limit(5).forEach(item -> lines.add(
                "- [" + (item.getType() == null ? "" : item.getType().name().toLowerCase(Locale.ROOT)) + "] "
                        + value(item.getTopic()) + ": " + value(item.getSummary())
        ));
        return lines.isEmpty() ? "无" : String.join("\n", lines);
    }

    private static String formatCiSummary(Map<String, Object> ciResult) {
        if (ciResult == null || ciResult.isEmpty()) {
            return "未执行";
        }
        Object gates = ciResult.get("gates");
        if (gates instanceof List<?> list && !list.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> gate) {
                    Object rawName = gate.get("name");
                    String name = rawName == null ? "unknown" : String.valueOf(rawName);
                    boolean isPassed = Boolean.TRUE.equals(gate.get("passed"))
                            || Boolean.TRUE.equals(gate.get("isPassed"));
                    parts.add(name + "=" + (isPassed ? "PASS" : "FAIL"));
                }
            }
            if (!parts.isEmpty()) {
                return String.join(", ", parts);
            }
        }
        return Boolean.TRUE.equals(ciResult.get("passed"))
                || Boolean.TRUE.equals(ciResult.get("isPassed"))
                ? "PASS"
                : "FAIL";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private static String joinOrNone(List<String> values) {
        return values == null || values.isEmpty() ? "无" : String.join(", ", values);
    }

    private static String json(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
 * Public record DraftGenerationResult used by the Java parity implementation.
 *
 * @since 1.0
 */
public record DraftGenerationResult(PullRequestDraft draft, String error, String output) {
    }
}
