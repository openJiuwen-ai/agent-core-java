/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.stages;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.contexts.TaskContext;
import com.openjiuwen.autoharness.factory.AutoHarnessFactory;
import com.openjiuwen.autoharness.infra.CIGateResult;
import com.openjiuwen.autoharness.infra.FixLoopController;
import com.openjiuwen.autoharness.infra.FixLoopResult;
import com.openjiuwen.autoharness.infra.Parsers;
import com.openjiuwen.autoharness.schema.CycleResult;
import com.openjiuwen.autoharness.schema.Experience;
import com.openjiuwen.autoharness.schema.ExperienceType;
import com.openjiuwen.autoharness.schema.StageResult;
import com.openjiuwen.autoharness.schema.TaskStatus;
import com.openjiuwen.autoharness.schema.VerifyReportArtifact;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Public class VerifyStage used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class VerifyStage extends TaskStage {
    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String name() {
        return "verify";
    }

    /**
     * description.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String description() {
        return "Run CI/fix loop verification.";
    }

    /**
     * consumes.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> consumes() {
        return List.of("code_change");
    }

    /**
     * produces.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> produces() {
        return List.of("verify_report");
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
        List<Object> events = stream(ctx);
        for (int index = events.size() - 1; index >= 0; index--) {
            if (events.get(index) instanceof StageResult result) {
                return result;
            }
        }
        return StageResult.builder().status("failed").error("verify stage did not return StageResult").build();
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
            return List.of(StageResult.builder().status("failed").error("verify requires TaskContext").build());
        }
        List<Object> events = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        CIGateResult ci = taskContext.getOrchestrator().getCiGate().run("all");
        Map<String, Object> ciResult = ciResult(ci);
        events.add(BaseExecutionContext.message("[2/5] CI 门禁检查"));
        for (String message : iterCiGateMessages(ciResult, "")) {
            messages.add(message);
            events.add(BaseExecutionContext.message(message));
        }
        String fixErrors = "";
        if (!ci.isPassed()) {
            messages.add("[3/5] CI 未通过，启动修复循环");
            events.add(BaseExecutionContext.message("[3/5] CI 未通过，启动修复循环"));
            FixLoopRun fix = startFixLoop(taskContext,
                    taskContext.getRuntime().getFixAgent() != null
                            ? taskContext.getRuntime().getFixAgent()
                            : taskContext.getRuntime().getTaskAgent());
            events.addAll(fix.events());
            if (!fix.isOk()) {
                taskContext.getOrchestrator().getGit().discardWorktreeChanges();
                taskContext.getTask().setStatus(TaskStatus.REVERTED);
                String errorLog = String.join("\n", fix.result().getErrorLog());
                recordFixLoopFailure(taskContext, fix.result());
                events.add(StageResult.builder().status("failed")
                        .artifacts(Map.of("verify_report",
                                VerifyReportArtifact.builder().ciResult(ciResult).isReverted(true).error(errorLog)
                                        .build(),
                                "task_result", CycleResult.builder().isReverted(true).errorLog(errorLog).build()))
                        .messages(concat(messages, "修复失败，回滚变更")).error(errorLog).build());
                return events;
            }
            fixErrors = String.join("\n", fix.result().getErrorLog());
        }
        events.add(StageResult.builder()
                .artifacts(Map.of("verify_report",
                        VerifyReportArtifact.builder().ciResult(ciResult).fixErrors(fixErrors).build(),
                        "verify_report.summary", description()))
                .messages(messages).build());
        return events;
    }

    /**
     * iterCiGateMessages.
     * 
     * @param ciResult ciResult
     * @param prefix prefix
     * @return the result
     * @since 0.1.7
     */
    public static List<String> iterCiGateMessages(Map<String, Object> ciResult, String prefix) {
        Object rawGates = ciResult == null ? null : ciResult.get("gates");
        List<Map<String, Object>> gates = rawGates instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> castMap((Map<?, ?>) item)).toList()
                : List.of();
        String effectivePrefix = prefix == null ? "" : prefix;
        if (gates.isEmpty()) {
            Object rawErrors = ciResult == null ? "" : ciResult.getOrDefault("errors", "");
            String errors = summarizeText(String.valueOf(rawErrors), 6, 400);
            return List.of(effectivePrefix + "CI 检查未执行: " + (errors.isBlank() ? "未匹配到任何 CI 门禁" : errors));
        }
        String summary =
            String.join(", ", gates.stream().map(gate -> valueOrDefault(String.valueOf(gate.get("name")), "unknown")
                    + "=" + (isGatePassed(gate) ? "PASS" : "FAIL")).toList());
        List<String> messages = new ArrayList<>();
        messages.add(effectivePrefix + "CI 结果: " + summary);
        for (Map<String, Object> gate : gates) {
            if (isGatePassed(gate)) {
                continue;
            }
            String detail = summarizeText(String.valueOf(gate.getOrDefault("output", "")), 6, 400);
            messages.add(effectivePrefix + "[" + valueOrDefault(String.valueOf(gate.get("name")), "unknown") + "] "
                    + (detail.isBlank() ? "无错误输出" : detail));
        }
        return messages;
    }

    /**
     * formatCiStatusForEvaluator.
     * 
     * @param ciResult ciResult
     * @return the result
     * @since 0.1.7
     */
    public static String formatCiStatusForEvaluator(Map<String, Object> ciResult) {
        Object rawGates = ciResult == null ? null : ciResult.get("gates");
        List<Map<String, Object>> gates = rawGates instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> castMap((Map<?, ?>) item)).toList()
                : List.of();
        if (gates.isEmpty()) {
            Object rawErrors = ciResult == null ? "" : ciResult.getOrDefault("errors", "");
            String errors = summarizeText(String.valueOf(rawErrors), 6, 400);
            return "结论: blocking failure\n详情: " + (errors.isBlank() ? "未执行任何门禁" : errors);
        }
        List<String> lines = new ArrayList<>();
        lines.add(Boolean.TRUE.equals(ciResult.get("isPassed")) ? "结论: pass" : "结论: blocking failure");
        for (Map<String, Object> gate : gates) {
            String line = "- " + valueOrDefault(String.valueOf(gate.get("name")), "unknown") + ": "
                    + (isGatePassed(gate) ? "PASS" : "FAIL");
            String detail = summarizeText(String.valueOf(gate.getOrDefault("output", "")), 6, 400);
            if (!detail.isBlank() && !isGatePassed(gate)) {
                line += " | " + detail;
            }
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    /**
     * isGatePassed.
     * 
     * @param gate gate
     * @return the result
     * @since 0.1.7
     */
    private static boolean isGatePassed(Map<String, Object> gate) {
        if (gate == null) {
            return false;
        }
        Object passed = gate.get("passed");
        if (passed instanceof Boolean bool) {
            return bool;
        }
        Object legacy = gate.get("isPassed");
        return legacy instanceof Boolean bool && bool;
    }

    /**
     * startFixLoop.
     * 
     * @param ctx ctx
     * @param agent agent
     * @return the result
     * @since 0.1.7
     */
    public static FixLoopRun startFixLoop(TaskContext ctx, Object agent) {
        List<Object> events = new ArrayList<>();
        int[] ciAttempts = {0};
        int[] fixAttempts = {0};
        int[] evalAttempts = {0};
        try {
            FixLoopResult result = ctx.getOrchestrator().getFixLoop().run(() -> {
                ciAttempts[0]++;
                events.add(BaseExecutionContext.message("[修复循环] 第 " + ciAttempts[0] + " 次重跑 CI"));
                CIGateResult ci = ctx.getOrchestrator().getCiGate().run("all");
                Map<String, Object> ciResult = ciResult(ci);
                for (String message : iterCiGateMessages(ciResult, "[修复循环] ")) {
                    events.add(BaseExecutionContext.message(message));
                }
                return new FixLoopController.SimpleCheckResult(ci.isPassed(), ci.getErrors());
            }, errors -> {
                fixAttempts[0]++;
                events.add(BaseExecutionContext.message("[修复循环] 第 " + fixAttempts[0] + " 次修复"));
                String detail = summarizeText(errors, 6, 400);
                if (!detail.isBlank()) {
                    events.add(BaseExecutionContext.message("[修复循环] 修复目标:\n" + detail));
                }
                if (agent != null) {
                    events.addAll(streamAgent(agent, "CI 检查失败，请修复以下错误:\n" + truncate(errors, 3000)));
                }
            }, () -> {
                evalAttempts[0]++;
                events.add(BaseExecutionContext.message("[修复循环] 进入评审阶段，第 " + evalAttempts[0] + " 次评审"));
                Object evalAgent = AutoHarnessFactory.createEvalAgent(ctx.getOrchestrator().getConfig());
                String diff = ctx.getOrchestrator().getGit().diffAgainst("HEAD~1");
                CIGateResult ci = ctx.getOrchestrator().getCiGate().run("all");
                Map<String, Object> ciResult = ciResult(ci);
                String query =
                    "任务描述: " + value(ctx.getTask().getDescription()) + "\n\n" + "代码变更:\n" + truncate(diff, 5000)
                            + "\n\n" + "CI 状态:\n" + formatCiStatusForEvaluator(ciResult) + "\n\n" + "请评审这些变更。";
                String output = "";
                for (Object chunk : streamAgent(evalAgent, query)) {
                    events.add(chunk);
                    output += Parsers.extractText(chunk);
                }
                boolean isApproved = output.toLowerCase(Locale.ROOT).contains("verdict: pass");
                events.add(BaseExecutionContext.message("[修复循环] 评审结果: " + (isApproved ? "PASS" : "REJECT")));
                return new FixLoopController.SimpleApprovalResult(isApproved);
            });
            events.add(BaseExecutionContext.message("[修复循环] " + (result.isSuccess() ? "修复成功" : "修复耗尽")));
            return new FixLoopRun(result.isSuccess(), result, events);
        } catch (Exception ex) {
            FixLoopResult result =
                FixLoopResult.builder().errorLog(List.of(ex.getMessage() == null ? "" : ex.getMessage())).build();
            events.add(BaseExecutionContext.message("[修复循环] 修复耗尽"));
            return new FixLoopRun(false, result, events);
        }
    }

    /**
     * ciResult.
     * 
     * @param ci ci
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> ciResult(CIGateResult ci) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean isPassed = ci != null && ci.isPassed();
        result.put("isPassed", isPassed);
        result.put("errors", ci == null ? "" : ci.getErrors());
        List<Map<String, Object>> gates = new ArrayList<>();
        if (ci != null && ci.getGates() != null && !ci.getGates().isEmpty()) {
            for (Map<String, Object> gate : ci.getGates()) {
                gates.add(new LinkedHashMap<>(gate));
            }
        } else if (ci != null && ci.getGateOutputs() != null && !ci.getGateOutputs().isEmpty()) {
            for (String output : ci.getGateOutputs()) {
                String name = output;
                String detail = "";
                if (output != null && output.contains("\n")) {
                    String[] parts = output.split("\\R", 2);
                    name = parts[0].replace("[", "").replace("]", "").trim();
                    detail = parts.length > 1 ? parts[1] : "";
                }
                gates.add(new LinkedHashMap<>(
                        Map.of("name", valueOrDefault(name, "unknown"), "isPassed", isPassed, "output", detail)));
            }
        }
        result.put("gates", gates);
        result.put("gate_outputs", ci == null ? List.of() : ci.getGateOutputs());
        return result;
    }

    /**
     * recordFixLoopFailure.
     * 
     * @param ctx ctx
     * @param result result
     * @since 0.1.7
     */
    private static void recordFixLoopFailure(TaskContext ctx, FixLoopResult result) {
        try {
            List<String> errorLog = result == null || result.getErrorLog() == null ? List.of() : result.getErrorLog();
            ctx.getOrchestrator().getExperienceStore()
                    .record(Experience.builder().type(ExperienceType.FAILURE).topic(ctx.getTask().getTopic())
                            .summary("fix loop failed").outcome("reverted")
                            .details(String.join("\n", tail(errorLog, 3))).build());
        } catch (IOException ignored) {
            // Python records failure experience but does not isReplace verify result when persistence fails.
        }
    }

    /**
     * streamAgent.
     * 
     * @param agent agent
     * @param query query
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> streamAgent(Object agent, String query) {
        if (agent == null) {
            return List.of();
        }
        try {
            Object stream = agent.getClass().getMethod("stream", Map.class).invoke(agent, Map.of("query", query));
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

    /**
     * summarizeText.
     * 
     * @param text text
     * @param maxLines maxLines
     * @param maxChars maxChars
     * @return the result
     * @since 0.1.7
     */
    private static String summarizeText(String text, int maxLines, int maxChars) {
        String value = value(text);
        if (value.isBlank()) {
            return "";
        }
        List<String> lines = value.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        String summary = String.join("\n", lines.stream().limit(maxLines).toList()).trim();
        if (summary.length() > maxChars) {
            return summary.substring(0, Math.max(0, maxChars - 3)).stripTrailing() + "...";
        }
        if (lines.size() > maxLines) {
            return summary + "\n...";
        }
        return summary;
    }

    /**
     * tail.
     * 
     * @param values values
     * @param count count
     * @return the result
     * @since 0.1.7
     */
    private static List<String> tail(List<String> values, int count) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.subList(Math.max(0, values.size() - count), values.size());
    }

    /**
     * concat.
     * 
     * @param messages messages
     * @param last last
     * @return the result
     * @since 0.1.7
     */
    private static List<String> concat(List<String> messages, String last) {
        List<String> result = new ArrayList<>(messages == null ? List.of() : messages);
        result.add(last);
        return result;
    }

    /**
     * truncate.
     * 
     * @param value value
     * @param maxChars maxChars
     * @return the result
     * @since 0.1.7
     */
    private static String truncate(String value, int maxChars) {
        String text = value(value);
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }

    /**
     * castMap.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> castMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
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

    /**
     * valueOrDefault.
     * 
     * @param value value
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() || "null".equals(value) ? defaultValue : value;
    }

    /**
     * Public record FixLoopRun used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record FixLoopRun(boolean isOk, FixLoopResult result, List<Object> events) {
    }
}
