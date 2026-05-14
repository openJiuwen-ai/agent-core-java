package com.openjiuwen.auto_harness.stages;

/**
 * Mirrors Python's {@code SessionStage} in {@code openjiuwen.auto_harness.stages.base}.
 */
public abstract class SessionStage extends BaseStage {
    @Override
    public String scope() {
        return "session";
    }
}
