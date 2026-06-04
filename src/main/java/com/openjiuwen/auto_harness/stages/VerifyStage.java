package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Verify-stage helpers and CI/fix-loop entry point.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.stages.verify}.</p>
 */
public class VerifyStage extends TaskStage {
    @Override public String name() { return "verify"; }
    @Override public StageResult run(Object context) { return new StageResult(); }

    public interface CIGate {
        Map<String, Object> run(String action);
    }

    public interface FixLoopControllerPort {
        FixLoopResult run(Supplier<CIResult> ciRunner, java.util.function.Consumer<String> agentFixer,
                          Supplier<EvalResult> evaluator);
    }

    public record CIResult(boolean passed, String errors) {}

    public record EvalResult(boolean approved, String feedback) {}

    public record FixLoopResult(boolean success, List<String> errorLog) {}

    public record FixLoopRun(boolean ok, FixLoopResult result, List<Object> items) {}

    public static List<String> iterCiGateMessages(Map<String, Object> ciResult) {
        return iterCiGateMessages(ciResult, "");
    }

    @SuppressWarnings("unchecked")
    public static List<String> iterCiGateMessages(Map<String, Object> ciResult, String prefix) {
        List<Map<String, Object>> gates = (List<Map<String, Object>>) ciResult.getOrDefault("gates", List.of());
        if (gates.isEmpty()) {
            String errors = summarizeText(String.valueOf(ciResult.getOrDefault("errors", "")));
            if (errors.isBlank()) {
                errors = "\u672a\u5339\u914d\u5230\u4efb\u4f55 CI \u95e8\u7981";
            }
            return List.of(prefix + "CI \u68c0\u67e5\u672a\u6267\u884c: " + errors);
        }
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> gate : gates) {
            parts.add(gate.getOrDefault("name", "unknown") + "="
                    + (Boolean.TRUE.equals(gate.get("passed")) ? "PASS" : "FAIL"));
        }
        List<String> messages = new ArrayList<>();
        messages.add(prefix + "CI \u7ed3\u679c: " + String.join(", ", parts));
        for (Map<String, Object> gate : gates) {
            if (Boolean.TRUE.equals(gate.get("passed"))) {
                continue;
            }
            String detail = summarizeText(String.valueOf(gate.getOrDefault("output", "")));
            if (detail.isBlank()) {
                detail = "\u65e0\u9519\u8bef\u8f93\u51fa";
            }
            messages.add(prefix + "[" + gate.getOrDefault("name", "unknown") + "] " + detail);
        }
        return messages;
    }

    public static String summarizeText(String text) {
        return summarizeText(text, 6, 400);
    }

    public static String summarizeText(String text, int maxLines, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> lines = text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .limit(maxLines + 1L)
                .toList();
        String summary = String.join("\n", lines.stream().limit(maxLines).toList()).strip();
        if (summary.length() > maxChars) {
            return summary.substring(0, Math.max(maxChars - 3, 0)).stripTrailing() + "...";
        }
        if (lines.size() > maxLines) {
            return summary + "\n...";
        }
        return summary;
    }

    public static FixLoopRun startFixLoop(
            AutoHarnessConfig config,
            OptimizationTask task,
            Object agent,
            Object git,
            CIGate ciGate,
            FixLoopControllerPort fixLoopCtrl,
            Function<String, OutputSchema> msgFactory) {
        List<Object> items = new ArrayList<>();
        int[] ciAttempts = {0};
        int[] fixAttempts = {0};
        Supplier<CIResult> ciRunner = () -> {
            ciAttempts[0]++;
            items.add(msgFactory.apply("[\u4fee\u590d\u5faa\u73af] \u7b2c " + ciAttempts[0] + " \u6b21\u91cd\u8dd1 CI"));
            Map<String, Object> result = ciGate.run("all");
            for (String message : iterCiGateMessages(result, "[\u4fee\u590d\u5faa\u73af] ")) {
                items.add(msgFactory.apply(message));
            }
            return new CIResult(Boolean.TRUE.equals(result.get("passed")),
                    String.valueOf(result.getOrDefault("errors", "")));
        };
        java.util.function.Consumer<String> fixer = errors -> {
            fixAttempts[0]++;
            items.add(msgFactory.apply("[\u4fee\u590d\u5faa\u73af] \u7b2c " + fixAttempts[0] + " \u6b21\u4fee\u590d"));
            String detail = summarizeText(errors);
            if (!detail.isBlank()) {
                items.add(msgFactory.apply("[\u4fee\u590d\u5faa\u73af] \u4fee\u590d\u76ee\u6807:\n" + detail));
            }
        };
        Supplier<EvalResult> evaluator = () -> new EvalResult(false, "");
        FixLoopResult result = fixLoopCtrl.run(ciRunner, fixer, evaluator);
        items.add(msgFactory.apply("[\u4fee\u590d\u5faa\u73af] "
                + (result.success() ? "\u4fee\u590d\u6210\u529f" : "\u4fee\u590d\u8017\u5c3d")));
        return new FixLoopRun(result.success(), result, items);
    }
}
