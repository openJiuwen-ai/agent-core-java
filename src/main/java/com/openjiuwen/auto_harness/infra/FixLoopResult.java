package com.openjiuwen.auto_harness.infra;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code FixLoopResult} in
 * {@code openjiuwen/auto_harness/infra/fix_loop.py}.
 */
public class FixLoopResult {

    private boolean success;
    private int attempts;
    private int phase = 1;
    private List<String> errorLog = new ArrayList<>();

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    public List<String> getErrorLog() {
        return errorLog;
    }

    public void setErrorLog(List<String> errorLog) {
        this.errorLog = errorLog != null ? new ArrayList<>(errorLog) : new ArrayList<>();
    }
}
