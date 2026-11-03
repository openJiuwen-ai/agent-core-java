/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.infra.Parsers;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CommitArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CommitFacts;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PullRequestArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PullRequestDraft;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.VerifyReportArtifact;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Push the branch, open a PR, and finalize the task result.
 *
 * <p>Mirrors Python's {@code PublishPRStage} and module helpers in
 * {@code openjiuwen/auto_harness/stages/publish_pr.py}.</p>
 */
public class PublishPRStage extends TaskStage {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PR_DRAFT_MAX_ATTEMPTS = 2;
    private static final String VERIFY_REPORT = "verify_report";
    private static final String COMMIT_RESULT = "commit_result";
    private static final String PULL_REQUEST = "pull_request";
    private static final String TASK_RESULT = "task_result";

    public static final PrDraftAgentFactory DEFAULT_AGENT_FACTORY = (config, workspaceOverride, extraRails) -> {
        DeepAgent agent = AutoHarnessAgentFactory.createPrDraftAgent(
                config,
                workspaceOverride,
                MetaPlanStage.deepAgentRails(extraRails)
        );
        return agent::stream;
    };

    private final PrDraftAgentFactory agentFactory;

    public PublishPRStage() {
        this(DEFAULT_AGENT_FACTORY);
    }

    public PublishPRStage(PrDraftAgentFactory agentFactory) {
        this.agentFactory = agentFactory == null ? DEFAULT_AGENT_FACTORY : agentFactory;
    }

    @Override
    public String name() {
        return "publish_pr";
    }

    @Override
    public String slot() {
        return "publish";
    }

    @Override
    public String displayName() {
        return "发布 PR";
    }

    @Override
    public String description() {
        return "Push branch and create PR when configured.";
    }

    @Override
    public List<String> consumes() {
        return List.of(VERIFY_REPORT, COMMIT_RESULT);
    }

    @Override
    public List<String> produces() {
        return List.of(PULL_REQUEST, TASK_RESULT);
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            throw new IllegalArgumentException("PublishPRStage requires TaskContext");
        }
        VerifyReportArtifact verifyReport = requireVerifyReport(taskContext.requireArtifact(VERIFY_REPORT));
        CommitArtifact commitResult = requireCommitArtifact(taskContext.requireArtifact(COMMIT_RESULT));
        if (!commitResult.isCommitted() || commitResult.getFacts() == null) {
            String error = isBlank(commitResult.getError()) ? "commit result missing" : commitResult.getError();
            if (taskContext.getTask() != null) {
                taskContext.getTask().setStatus(TaskStatus.FAILED);
            }
            Map<String, Object> artifacts = new LinkedHashMap<>();
            artifacts.put(TASK_RESULT, CycleResult.builder()
                    .success(false)
                    .error(error)
                    .build());
            return List.of((Object) StageResult.builder()
                    .status("failed")
                    .artifacts(artifacts)
                    .messages(List.of("发布 PR 失败: " + error))
                    .error(error)
                    .build()).iterator();
        }

        List<Object> events = new ArrayList<>();
        String branchName = nullToEmpty(commitResult.getBranchName());
        String prUrl = "";
        List<String> messages = new ArrayList<>();
        PullRequestDraft prDraft = new PullRequestDraft();

        if (shouldCreatePr(taskContext)) {
            String draftError = "";
            String previousOutput = "";
            for (int attempt = 1; attempt <= PR_DRAFT_MAX_ATTEMPTS; attempt++) {
                if (attempt == 1) {
                    events.add(taskContext.message("生成 PR draft"));
                } else {
                    events.add(taskContext.message("修正 PR draft (" + attempt + "/" + PR_DRAFT_MAX_ATTEMPTS + ")"));
                }
                DraftAttemptStreamResult attemptResult = generatePrDraftAttempt(
                        taskContext,
                        commitResult.getFacts(),
                        verifyReport.getCiResult(),
                        commitResult.getLastCommitStat(),
                        draftError,
                        previousOutput,
                        agentFactory
                );
                events.addAll(attemptResult.events());
                DraftGenerationResult result = attemptResult.result();
                prDraft = result.draft() == null ? new PullRequestDraft() : result.draft();
                draftError = result.error();
                previousOutput = result.output();
                if (!isBlank(prDraft.getTitle()) && !isBlank(prDraft.getBody())) {
                    break;
                }
            }
            if (isBlank(prDraft.getTitle()) || isBlank(prDraft.getBody())) {
                String error = "PR draft generation failed after " + PR_DRAFT_MAX_ATTEMPTS
                        + " attempts: " + (isBlank(draftError)
                        ? "communicate agent did not return a valid JSON draft."
                        : draftError);
                if (taskContext.getTask() != null) {
                    taskContext.getTask().setStatus(TaskStatus.FAILED);
                }
                Map<String, Object> artifacts = new LinkedHashMap<>();
                artifacts.put(TASK_RESULT, CycleResult.builder()
                        .success(false)
                        .error(error)
                        .build());
                events.add(StageResult.builder()
                        .status("failed")
                        .artifacts(artifacts)
                        .messages(List.of("发布 PR 失败: " + error))
                        .error(error)
                        .build());
                return events.iterator();
            }
            messages.add("PR draft 已生成: " + prDraft.getTitle());
        }

        if (!isBlank(taskContext.getOrchestrator().getConfig().getGitRemote())) {
            events.add(taskContext.message("推送分支"));
            runGitPush(taskContext, branchName);
            if (shouldCreatePr(taskContext)) {
                events.add(taskContext.message("创建 PR"));
                Map<String, Object> prResult = runCreatePr(taskContext, prDraft, branchName);
                Object rawUrl = prResult.get("pr_url");
                prUrl = rawUrl == null ? "" : String.valueOf(rawUrl);
                if (!prUrl.isBlank()) {
                    messages.add("PR 已创建: " + prUrl);
                }
            }
        }

        String completionSummary = buildCompletionSummary(
                taskContext.getTask(),
                commitResult.getFacts(),
                verifyReport.getCiResult(),
                prUrl
        );
        if (taskContext.getTask() != null) {
            taskContext.getTask().setStatus(TaskStatus.SUCCESS);
        }
        taskContext.getOrchestrator().getExperienceStore().record(Experience.builder()
                .type(ExperienceType.OPTIMIZATION)
                .topic(taskContext.getTask() == null ? "" : nullToEmpty(taskContext.getTask().getTopic()))
                .summary("completed: " + (taskContext.getTask() == null ? "" : nullToEmpty(taskContext.getTask().getTopic())))
                .outcome("success")
                .prUrl(prUrl)
                .build()).join();

        CycleResult result = CycleResult.builder()
                .success(true)
                .summary(completionSummary)
                .prUrl(prUrl)
                .build();
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put(PULL_REQUEST, PullRequestArtifact.builder()
                .prUrl(prUrl)
                .summary(completionSummary)
                .build());
        artifacts.put(TASK_RESULT, result);
        List<String> finalMessages = new ArrayList<>(messages);
        finalMessages.add("任务总结: " + completionSummary);
        finalMessages.add(prUrl.isBlank() ? "任务完成（本地提交）" : "任务完成: " + prUrl);
        events.add(StageResult.builder()
                .artifacts(artifacts)
                .messages(finalMessages)
                .build());
        return events.iterator();
    }

    public static String buildCompletionSummary(
            OptimizationTask task,
            CommitFacts facts,
            Map<String, Object> ciResult,
            String prUrl
    ) {
        String ciSummary = ciSummary(ciResult);
        List<String> allowedFiles = facts == null || facts.getAllowedFiles() == null
                ? List.of()
                : facts.getAllowedFiles();
        String changedFiles = allowedFiles.isEmpty()
                ? "无"
                : String.join(", ", allowedFiles.subList(0, Math.min(5, allowedFiles.size())));
        String suffix = allowedFiles.size() > 5 ? " 等 " + allowedFiles.size() + " 个文件" : "";
        String location = isBlank(prUrl) ? "本地提交" : prUrl;
        String topic = task == null ? "" : nullToEmpty(task.getTopic());
        return topic + ": 已完成；CI=" + ciSummary
                + "；变更文件=" + changedFiles + suffix
                + "；交付=" + location;
    }

    public static String buildPrDraftQuery(
            TaskContext ctx,
            CommitFacts facts,
            Map<String, Object> ciResult,
            String lastCommitStat,
            String validationError,
            String previousOutput
    ) {
        String relatedText = formatRelated(ctx);
        String repairText = "";
        if (!isBlank(validationError)) {
            repairText = "\n\n上一次 PR draft 校验失败原因:\n"
                    + validationError + "\n\n"
                    + "上一次输出如下，请修正为合法的完整 GitCode 模板，不要重复输出简化格式，"
                    + "也不要省略 HTML 注释和标准 checklist:\n"
                    + (isBlank(previousOutput) ? "无" : previousOutput) + "\n";
        }
        OptimizationTask task = ctx == null ? null : ctx.getTask();
        return "任务主题: " + nullToEmpty(task == null ? "" : task.getTopic()) + "\n"
                + "任务描述: " + defaultText(task == null ? "" : task.getDescription()) + "\n"
                + "预期效果: " + defaultText(task == null ? "" : task.getExpectedEffect()) + "\n"
                + "关联 issue: " + defaultText(task == null ? "" : task.getIssueRef()) + "\n"
                + "允许提交文件: " + joinOrNone(facts == null ? List.of() : facts.getAllowedFiles()) + "\n"
                + "本轮实际修改: " + joinOrNone(facts == null ? List.of() : facts.getEditedFiles()) + "\n"
                + "diff 统计:\n" + defaultText(facts == null ? "" : facts.getDiffStat()) + "\n\n"
                + "最近一次提交摘要:\n" + defaultText(lastCommitStat) + "\n\n"
                + "验证结果(JSON):\n" + toJson(ciResult) + "\n\n"
                + "相关经验:\n" + relatedText + "\n\n"
                + repairText
                + "请基于这些事实，生成可直接提交到 GitCode 的 PR draft。";
    }

    public static DraftAttemptStreamResult generatePrDraftAttempt(
            TaskContext ctx,
            CommitFacts facts,
            Map<String, Object> ciResult,
            String lastCommitStat,
            String validationError,
            String previousOutput,
            PrDraftAgentFactory agentFactory
    ) {
        PrDraftAgent agent = safeFactory(agentFactory).create(
                ctx.getOrchestrator().getConfig(),
                ctx.getRuntime() == null ? "" : ctx.getRuntime().getWtPath(),
                ctx.getOrchestrator().getStreamRails()
        );
        String query = buildPrDraftQuery(ctx, facts, ciResult, lastCommitStat, validationError, previousOutput);
        StringBuilder output = new StringBuilder();
        List<Object> events = new ArrayList<>();
        Iterator<?> stream = agent.stream(Map.of("query", query));
        while (stream.hasNext()) {
            Object chunk = stream.next();
            events.add(chunk);
            output.append(Parsers.extractText(chunk));
        }
        Parsers.PullRequestDraftParseResult parsed = Parsers.parsePrDraftWithError(output.toString());
        return new DraftAttemptStreamResult(
                events,
                new DraftGenerationResult(parsed.draft(), parsed.error(), output.toString())
        );
    }

    public static boolean shouldCreatePr(TaskContext ctx) {
        if (ctx == null || ctx.getOrchestrator() == null || ctx.getOrchestrator().getConfig() == null) {
            return false;
        }
        AutoHarnessConfig config = ctx.getOrchestrator().getConfig();
        return !isBlank(config.getGitRemote()) && !isBlank(config.getForkOwner());
    }

    private static String ciSummary(Map<String, Object> ciResult) {
        if (ciResult != null && ciResult.get("gates") instanceof List<?> gates && !gates.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Object gate : gates) {
                if (!(gate instanceof Map<?, ?> map)) {
                    continue;
                }
                Object rawName = map.containsKey("name") ? map.get("name") : "unknown";
                String name = String.valueOf(rawName);
                boolean passed = isTruthy(map.get("passed"));
                parts.add(name + "=" + (passed ? "PASS" : "FAIL"));
            }
            if (!parts.isEmpty()) {
                return String.join(", ", parts);
            }
        }
        if (ciResult == null || ciResult.isEmpty()) {
            return "未执行";
        }
        return isTruthy(ciResult.get("passed")) ? "PASS" : "FAIL";
    }

    private static String formatRelated(TaskContext ctx) {
        if (ctx == null || ctx.getRuntime() == null || ctx.getRuntime().getRelated().isEmpty()) {
            return "无";
        }
        List<String> lines = new ArrayList<>();
        List<Experience> related = ctx.getRuntime().getRelated();
        int limit = Math.min(5, related.size());
        for (int i = 0; i < limit; i++) {
            Experience item = related.get(i);
            ExperienceType type = item.getType();
            String typeValue = type == null ? "" : type.value();
            lines.add("- [" + typeValue + "] " + nullToEmpty(item.getTopic()) + ": " + nullToEmpty(item.getSummary()));
        }
        return String.join("\n", lines);
    }

    private static void runGitPush(TaskContext taskContext, String branchName) {
        try {
            taskContext.getOrchestrator().getGit().push(branchName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to push branch", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pushing branch", e);
        }
    }

    private static Map<String, Object> runCreatePr(
            TaskContext taskContext,
            PullRequestDraft prDraft,
            String branchName
    ) {
        try {
            return taskContext.getOrchestrator().getGit().createPr(
                    prDraft.getTitle(),
                    prDraft.getBody(),
                    branchName
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create PR", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating PR", e);
        }
    }

    private static VerifyReportArtifact requireVerifyReport(Object artifact) {
        if (artifact instanceof VerifyReportArtifact verifyReport) {
            return verifyReport;
        }
        throw new IllegalArgumentException("verify_report must be VerifyReportArtifact");
    }

    private static CommitArtifact requireCommitArtifact(Object artifact) {
        if (artifact instanceof CommitArtifact commitArtifact) {
            return commitArtifact;
        }
        throw new IllegalArgumentException("commit_result must be CommitArtifact");
    }

    private static PrDraftAgentFactory safeFactory(PrDraftAgentFactory factory) {
        return factory == null ? DEFAULT_AGENT_FACTORY : factory;
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0;
        }
        return value != null && !"false".equalsIgnoreCase(String.valueOf(value));
    }

    private static String toJson(Map<String, Object> value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String joinOrNone(List<String> values) {
        return values == null || values.isEmpty() ? "无" : String.join(", ", values);
    }

    private static String defaultText(String value) {
        return isBlank(value) ? "无" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Mirrors Python's private draft generation dataclass in
     * {@code openjiuwen/auto_harness/stages/publish_pr.py}.
     */
    public record DraftGenerationResult(PullRequestDraft draft, String error, String output) {
    }

    public record DraftAttemptStreamResult(List<Object> events, DraftGenerationResult result) {
    }

    /**
     * Streaming surface used by the PR draft agent.
     *
     * <p>Mirrors Python's late import of {@code create_pr_draft_agent} in
     * {@code openjiuwen/auto_harness/stages/publish_pr.py}.</p>
     */
    @FunctionalInterface
    public interface PrDraftAgent {
        Iterator<?> stream(Map<String, Object> inputs);
    }

    /**
     * Factory for the PR draft agent.
     *
     * <p>Mirrors Python's late import of {@code create_pr_draft_agent} in
     * {@code openjiuwen/auto_harness/stages/publish_pr.py}.</p>
     */
    @FunctionalInterface
    public interface PrDraftAgentFactory {
        PrDraftAgent create(AutoHarnessConfig config, String workspaceOverride, List<?> extraRails);
    }
}
