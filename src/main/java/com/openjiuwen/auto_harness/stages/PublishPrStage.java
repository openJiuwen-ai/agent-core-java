package com.openjiuwen.auto_harness.stages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.infra.InfraParsers;
import com.openjiuwen.auto_harness.schema.CommitArtifact;
import com.openjiuwen.auto_harness.schema.CommitFacts;
import com.openjiuwen.auto_harness.schema.CycleResult;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.ExperienceType;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.PullRequestArtifact;
import com.openjiuwen.auto_harness.schema.PullRequestDraft;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.auto_harness.schema.TaskStatus;
import com.openjiuwen.auto_harness.schema.VerifyReportArtifact;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.harness.DeepAgent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Push the branch, create a PR when configured, and finalize task results.
 *
 * <p>Mirrors Python's {@code PublishPRStage} in
 * {@code openjiuwen.auto_harness.stages.publish_pr}.</p>
 */
public class PublishPrStage extends TaskStage {
    private static final int PR_DRAFT_MAX_ATTEMPTS = 2;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DraftAgentFactory DEFAULT_AGENT_FACTORY = (ctx) -> {
        DeepAgent agent = AutoHarnessAgentFactory.createPrDraftAgent(
                ctx.getOrchestrator().getConfig(),
                ctx.getRuntime().getWtPath()
        );
        return inputs -> agent.stream(inputs, null, List.of(StreamMode.OUTPUT));
    };

    private final DraftAgentFactory agentFactory;

    public PublishPrStage() {
        this(DEFAULT_AGENT_FACTORY);
    }

    public PublishPrStage(DraftAgentFactory agentFactory) {
        this.agentFactory = agentFactory != null ? agentFactory : DEFAULT_AGENT_FACTORY;
    }

    @Override
    public String name() {
        return "publish_pr";
    }

    @Override
    public StageResult run(Object context) {
        if (!(context instanceof TaskContext taskContext)) {
            return new StageResult();
        }
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

    public interface DraftAgent {
        Iterator<Object> stream(Map<String, Object> inputs);
    }

    public interface DraftAgentFactory {
        DraftAgent create(TaskContext ctx);
    }

    public Iterator<Object> stream(TaskContext ctx) {
        List<Object> items = new ArrayList<>();
        VerifyReportArtifact verifyReport = (VerifyReportArtifact) ctx.requireArtifact("verify_report");
        CommitArtifact commitResult = (CommitArtifact) ctx.requireArtifact("commit_result");
        if (!commitResult.isCommitted() || commitResult.getFacts() == null) {
            String error = blank(commitResult.getError()) ? "commit result missing" : commitResult.getError();
            ctx.getTask().setStatus(TaskStatus.FAILED);
            items.add(failedResult(error));
            return items.iterator();
        }

        String branchName = commitResult.getBranchName();
        String prUrl = "";
        List<String> messages = new ArrayList<>();
        PullRequestDraft prDraft = new PullRequestDraft();

        if (shouldCreatePr(ctx)) {
            String draftError = "";
            String previousOutput = "";
            for (int attempt = 1; attempt <= PR_DRAFT_MAX_ATTEMPTS; attempt++) {
                if (attempt == 1) {
                    items.add(TaskContext.message("[post] generate PR draft"));
                } else {
                    items.add(TaskContext.message(
                            "[post] repair PR draft (" + attempt + "/" + PR_DRAFT_MAX_ATTEMPTS + ")"));
                }
                DraftGenerationResult generated = generatePrDraftAttempt(
                        ctx,
                        commitResult.getFacts(),
                        verifyReport.getCiResult(),
                        commitResult.getLastCommitStat(),
                        draftError,
                        previousOutput,
                        items
                );
                prDraft = generated.draft() != null ? generated.draft() : new PullRequestDraft();
                draftError = generated.error();
                previousOutput = generated.output();
                if (!blank(prDraft.getTitle()) && !blank(prDraft.getBody())) {
                    break;
                }
            }
            if (blank(prDraft.getTitle()) || blank(prDraft.getBody())) {
                String error = "PR draft generation failed after " + PR_DRAFT_MAX_ATTEMPTS + " attempts: "
                        + (!blank(draftError) ? draftError : "communicate agent did not return a valid JSON draft.");
                ctx.getTask().setStatus(TaskStatus.FAILED);
                items.add(failedResult(error));
                return items.iterator();
            }
            messages.add("PR draft generated: " + prDraft.getTitle());
        }

        if (!blank(ctx.getOrchestrator().getConfig().getGitRemote())) {
            items.add(TaskContext.message("[post] push branch"));
            pushBranch(ctx, branchName);
            if (shouldCreatePr(ctx)) {
                items.add(TaskContext.message("[post] create PR"));
                Map<String, Object> prResult = createPr(ctx, prDraft, branchName);
                Object rawUrl = prResult.get("pr_url");
                prUrl = rawUrl != null ? String.valueOf(rawUrl) : "";
                if (!blank(prUrl)) {
                    messages.add("PR created: " + prUrl);
                }
            }
        }

        String summary = buildCompletionSummary(
                ctx.getTask(),
                commitResult.getFacts(),
                verifyReport.getCiResult(),
                prUrl
        );
        ctx.getTask().setStatus(TaskStatus.SUCCESS);
        recordExperience(ctx, prUrl);

        CycleResult result = new CycleResult();
        result.setSuccess(true);
        result.setSummary(summary);
        result.setPrUrl(prUrl);

        StageResult stageResult = new StageResult();
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("pull_request", new PullRequestArtifact(prUrl, summary));
        artifacts.put("task_result", result);
        stageResult.setArtifacts(artifacts);
        List<String> finalMessages = new ArrayList<>(messages);
        finalMessages.add("Task summary: " + summary);
        finalMessages.add(!blank(prUrl) ? "Task completed: " + prUrl : "Task completed (local commit)");
        stageResult.setMessages(finalMessages);
        items.add(stageResult);
        return items.iterator();
    }

    public static String buildCompletionSummary(
            OptimizationTask task,
            CommitFacts facts,
            Map<String, Object> ciResult,
            String prUrl) {
        String ciSummary = formatCiSummary(ciResult);
        List<String> allowed = facts.getAllowedFiles();
        String changedFiles = allowed.isEmpty()
                ? "none"
                : String.join(", ", allowed.stream().limit(5).toList());
        String suffix = allowed.size() > 5 ? " and " + allowed.size() + " files" : "";
        String location = blank(prUrl) ? "local commit" : prUrl;
        return task.getTopic() + ": completed; CI=" + ciSummary
                + "; changed files=" + changedFiles + suffix
                + "; delivery=" + location;
    }

    public static String buildPrDraftQuery(
            TaskContext ctx,
            CommitFacts facts,
            Map<String, Object> ciResult,
            String lastCommitStat,
            String validationError,
            String previousOutput) {
        String relatedText = ctx.getRuntime().getRelated().stream()
                .limit(5)
                .map(item -> "- [" + item.getType() + "] " + item.getTopic() + ": " + item.getSummary())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("none");
        String repairText = "";
        if (!blank(validationError)) {
            repairText = "\n\nPrevious PR draft validation failure:\n"
                    + validationError + "\n\n"
                    + "Previous output:\n"
                    + (blank(previousOutput) ? "none" : previousOutput) + "\n";
        }
        return "Task topic: " + ctx.getTask().getTopic() + "\n"
                + "Task description: " + defaultIfBlank(ctx.getTask().getDescription(), "none") + "\n"
                + "Expected effect: " + defaultIfBlank(ctx.getTask().getExpectedEffect(), "none") + "\n"
                + "Issue: " + defaultIfBlank(ctx.getTask().getIssueRef(), "none") + "\n"
                + "Allowed files: " + defaultIfBlank(String.join(", ", facts.getAllowedFiles()), "none") + "\n"
                + "Edited files: " + defaultIfBlank(String.join(", ", facts.getEditedFiles()), "none") + "\n"
                + "Diff stat:\n" + defaultIfBlank(facts.getDiffStat(), "none") + "\n\n"
                + "Last commit stat:\n" + defaultIfBlank(lastCommitStat, "none") + "\n\n"
                + "CI result(JSON):\n" + toJson(ciResult) + "\n\n"
                + "Related experience:\n" + relatedText + "\n"
                + repairText
                + "\nGenerate a GitCode PR draft as JSON with title, kind, and body.";
    }

    public static boolean shouldCreatePr(TaskContext ctx) {
        return !blank(ctx.getOrchestrator().getConfig().getGitRemote())
                && !blank(ctx.getOrchestrator().getConfig().getForkOwner());
    }

    private DraftGenerationResult generatePrDraftAttempt(
            TaskContext ctx,
            CommitFacts facts,
            Map<String, Object> ciResult,
            String lastCommitStat,
            String validationError,
            String previousOutput,
            List<Object> items) {
        DraftAgent agent = agentFactory.create(ctx);
        String query = buildPrDraftQuery(ctx, facts, ciResult, lastCommitStat, validationError, previousOutput);
        StringBuilder output = new StringBuilder();
        Iterator<Object> chunks = agent.stream(Map.of("query", query));
        while (chunks.hasNext()) {
            Object chunk = chunks.next();
            items.add(chunk);
            output.append(InfraParsers.extractText(chunk));
        }
        InfraParsers.Pair<PullRequestDraft, String> parsed =
                InfraParsers.parsePrDraftWithError(output.toString());
        return new DraftGenerationResult(parsed.first, parsed.second, output.toString());
    }

    private void pushBranch(TaskContext ctx, String branchName) {
        try {
            ctx.getOrchestrator().getGit().push(branchName);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Git push failed", e);
        }
    }

    private Map<String, Object> createPr(TaskContext ctx, PullRequestDraft draft, String branchName) {
        try {
            return ctx.getOrchestrator().getGit().createPr(draft.getTitle(), draft.getBody(), branchName);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("PR creation failed", e);
        }
    }

    private static StageResult failedResult(String error) {
        CycleResult cycleResult = new CycleResult();
        cycleResult.setSuccess(false);
        cycleResult.setError(error);
        StageResult result = new StageResult();
        result.setStatus("failed");
        result.setArtifacts(Map.of("task_result", cycleResult));
        result.setMessages(List.of("Publish PR failed: " + error));
        result.setError(error);
        return result;
    }

    private static void recordExperience(TaskContext ctx, String prUrl) {
        Experience experience = new Experience();
        experience.setType(ExperienceType.OPTIMIZATION);
        experience.setTopic(ctx.getTask().getTopic());
        experience.setSummary("completed: " + ctx.getTask().getTopic());
        experience.setOutcome("success");
        experience.setPrUrl(prUrl);
        ctx.getOrchestrator().getExperienceStore().record(experience);
    }

    private static String formatCiSummary(Map<String, Object> ciResult) {
        if (ciResult == null || ciResult.isEmpty()) {
            return "not run";
        }
        Object gatesObj = ciResult.get("gates");
        if (gatesObj instanceof List<?> gates && !gates.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Object gate : gates) {
                if (gate instanceof Map<?, ?> map) {
                    Object name = map.get("name");
                    if (name == null) {
                        name = "unknown";
                    }
                    boolean passed = Boolean.TRUE.equals(map.get("passed"));
                    parts.add(name + "=" + (passed ? "PASS" : "FAIL"));
                }
            }
            if (!parts.isEmpty()) {
                return String.join(", ", parts);
            }
        }
        return Boolean.TRUE.equals(ciResult.get("passed")) ? "PASS" : "FAIL";
    }

    private static String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(
                    value != null ? value : Map.of());
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private record DraftGenerationResult(PullRequestDraft draft, String error, String output) {}
}
