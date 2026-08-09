/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.infra.FixLoopController;
import com.openjiuwen.auto_harness.infra.FixLoopResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
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
 * Run CI and the fix loop for the current task.
 *
 * <p>Mirrors Python's {@code MetaVerifyStage} in
 * {@code openjiuwen/auto_harness/stages/verify.py}.</p>
 */
public class MetaVerifyStage extends VerifyStage {

    @Override
    public List<String> consumes() {
        return List.of("code_change");
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            throw new IllegalArgumentException("MetaVerifyStage requires TaskContext");
        }
        List<Object> events = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        Map<String, Object> ciResult = taskContext.getOrchestrator().getCiGate().run("all").join();
        for (String message : iterCiGateMessages(ciResult)) {
            messages.add(message);
            events.add(taskContext.message(message));
        }

        String fixErrors = "";
        if (!isTruthy(ciResult.get("passed"))) {
            messages.add("CI 未通过，启动修复循环");
            events.add(taskContext.message("CI 未通过，启动修复循环"));
            FixLoopResult fixResult = startFixLoop(taskContext, events);
            if (!fixResult.isSuccess()) {
                discardWorktreeChanges(taskContext);
                if (taskContext.getTask() != null) {
                    taskContext.getTask().setStatus(TaskStatus.REVERTED);
                }
                String errorLog = String.join("\n", fixResult.getErrorLog());
                taskContext.getOrchestrator().getExperienceStore().record(Experience.builder()
                        .type(ExperienceType.FAILURE)
                        .topic(taskContext.getTask() == null ? "" : taskContext.getTask().getTopic())
                        .summary("fix loop failed")
                        .outcome("reverted")
                        .details(tailLines(fixResult.getErrorLog(), 3))
                        .build()).join();
                Map<String, Object> artifacts = new LinkedHashMap<>();
                artifacts.put("verify_report", VerifyReportArtifact.builder()
                        .ciResult(ciResult)
                        .reverted(true)
                        .error(errorLog)
                        .build());
                artifacts.put("task_result", CycleResult.builder()
                        .reverted(true)
                        .errorLog(errorLog)
                        .build());
                List<String> failedMessages = new ArrayList<>(messages);
                failedMessages.add("修复失败，回滚变更");
                events.add(StageResult.builder()
                        .status("failed")
                        .artifacts(artifacts)
                        .messages(failedMessages)
                        .error(errorLog)
                        .build());
                return events.iterator();
            }
            fixErrors = String.join("\n", fixResult.getErrorLog());
        }

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("verify_report", VerifyReportArtifact.builder()
                .ciResult(ciResult)
                .fixErrors(fixErrors)
                .build());
        events.add(StageResult.builder()
                .artifacts(artifacts)
                .messages(messages)
                .build());
        return events.iterator();
    }

    static FixLoopResult startFixLoop(TaskContext ctx, List<Object> events) {
        DeepAgent agent = resolveFixAgent(ctx);
        return ctx.getOrchestrator().getFixLoop().run(
                () -> {
                    events.add(ctx.message("[修复循环] 重跑 CI"));
                    Map<String, Object> result = ctx.getOrchestrator().getCiGate().run("all").join();
                    for (String message : iterCiGateMessages(result, "[修复循环] ")) {
                        events.add(ctx.message(message));
                    }
                    return new FixLoopController.CiResult(
                            isTruthy(result.get("passed")),
                            String.valueOf(result.getOrDefault("errors", ""))
                    );
                },
                errors -> {
                    events.add(ctx.message("[修复循环] 修复目标:\n" + summarizeText(errors)));
                    if (agent == null) {
                        return;
                    }
                    Iterator<?> stream = agent.stream(Map.of("query", "CI 检查失败，请修复以下错误:\n" + errors));
                    while (stream.hasNext()) {
                        events.add(stream.next());
                    }
                },
                () -> {
                    events.add(ctx.message("[修复循环] 进入评审阶段"));
                    DeepAgent evalAgent = AutoHarnessAgentFactory.createEvalAgent(
                            ctx.getOrchestrator().getConfig(),
                            MetaPlanStage.deepAgentRails(ctx.getOrchestrator().getStreamRails())
                    );
                    String diff = diffAgainstHead(ctx);
                    Map<String, Object> result = ctx.getOrchestrator().getCiGate().run("all").join();
                    String query = "任务描述: "
                            + (ctx.getTask() == null ? "" : ctx.getTask().getDescription())
                            + "\n\n代码变更:\n" + preview(diff, 5000)
                            + "\n\nCI 状态:\n" + formatCiStatusForEvaluator(result)
                            + "\n\n请评审这些变更。";
                    StringBuilder output = new StringBuilder();
                    Iterator<?> stream = evalAgent.stream(Map.of("query", query));
                    while (stream.hasNext()) {
                        Object chunk = stream.next();
                        events.add(chunk);
                        output.append(com.openjiuwen.auto_harness.infra.Parsers.extractText(chunk));
                    }
                    boolean approved = output.toString().toLowerCase().contains("verdict: pass");
                    events.add(ctx.message("[修复循环] 评审结果: " + (approved ? "PASS" : "REJECT")));
                    return new FixLoopController.ReviewResult(approved);
                }
        );
    }

    private static DeepAgent resolveFixAgent(TaskContext ctx) {
        if (ctx.getRuntime() == null) {
            return null;
        }
        if (ctx.getRuntime().getFixAgent() instanceof DeepAgent agent) {
            return agent;
        }
        return ctx.getRuntime().getTaskAgent() instanceof DeepAgent agent ? agent : null;
    }

    private static void discardWorktreeChanges(TaskContext ctx) {
        try {
            ctx.getOrchestrator().getGit().discardWorktreeChanges();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to discard worktree changes", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while discarding worktree changes", e);
        }
    }

    private static String diffAgainstHead(TaskContext ctx) {
        try {
            return ctx.getOrchestrator().getGit().diffAgainst("HEAD~1");
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static String tailLines(List<String> lines, int count) {
        List<String> source = lines == null ? List.of() : lines;
        int start = Math.max(0, source.size() - count);
        return String.join("\n", source.subList(start, source.size()));
    }

    private static String preview(String value, int maxChars) {
        String text = value == null ? "" : value;
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}
