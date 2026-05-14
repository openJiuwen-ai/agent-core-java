package com.openjiuwen.auto_harness.infra;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Mirrors Python's {@code FixLoopController} in {@code openjiuwen.auto_harness.infra.fix_loop}.
 */
public class FixLoopController {

    public interface CiRunner {
        CiResult run() throws Exception;
    }

    public interface AgentFixer {
        void fix(String errors) throws Exception;
    }

    public interface Evaluator {
        ReviewResult evaluate() throws Exception;
    }

    public record CiResult(boolean passed, String errors) {}

    public record ReviewResult(boolean approved) {}

    private final int phase1MaxRetries;
    private final int phase2MaxRetries;
    private final double timeoutPerAttempt;

    public FixLoopController() {
        this(10, 9, 600.0);
    }

    public FixLoopController(int phase1MaxRetries, int phase2MaxRetries, double timeoutPerAttempt) {
        this.phase1MaxRetries = phase1MaxRetries;
        this.phase2MaxRetries = phase2MaxRetries;
        this.timeoutPerAttempt = timeoutPerAttempt;
    }

    public FixLoopResult run(CiRunner ciRunner, AgentFixer agentFixer) {
        return run(ciRunner, agentFixer, null);
    }

    public FixLoopResult run(CiRunner ciRunner, AgentFixer agentFixer, Evaluator evaluator) {
        FixLoopResult result = new FixLoopResult();
        result.setPhase(1);

        for (int i = 1; i <= phase1MaxRetries; i++) {
            result.setAttempts(i);
            CiResult ci;
            try {
                ci = withTimeout(ciRunner::run);
            } catch (Exception e) {
                result.getErrorLog().add("Phase 1 attempt " + i + ": CI timeout");
                continue;
            }
            if (ci.passed()) {
                result.setSuccess(true);
                return result;
            }
            String errors = ci.errors() == null || ci.errors().isBlank() ? "unknown error" : ci.errors();
            result.getErrorLog().add("Phase 1 attempt " + i + ": " + trim(errors));
            try {
                withTimeout(() -> { agentFixer.fix(errors); return null; });
            } catch (Exception e) {
                result.getErrorLog().add("Phase 1 attempt " + i + ": fixer timeout");
            }
        }

        if (evaluator == null) {
            return result;
        }

        result.setPhase(2);
        for (int j = 1; j <= phase2MaxRetries; j++) {
            result.setAttempts(result.getAttempts() + 1);
            ReviewResult review;
            try {
                review = withTimeout(evaluator::evaluate);
            } catch (Exception e) {
                result.getErrorLog().add("Phase 2 attempt " + j + ": evaluator timeout");
                continue;
            }
            if (review.approved()) {
                result.setSuccess(true);
                return result;
            }
            result.getErrorLog().add("Phase 2 attempt " + j + ": evaluator rejected");
            try {
                withTimeout(() -> { agentFixer.fix("evaluator rejected"); return null; });
            } catch (Exception e) {
                result.getErrorLog().add("Phase 2 attempt " + j + ": fixer timeout");
            }
        }
        return result;
    }

    private <T> T withTimeout(Callable<T> callable) throws Exception {
        long start = System.nanoTime();
        T value = callable.call();
        double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
        if (elapsed > timeoutPerAttempt) {
            throw new RuntimeException("timeout");
        }
        return value;
    }

    private String trim(String errors) {
        return errors.length() <= 200 ? errors : errors.substring(0, 200);
    }
}
