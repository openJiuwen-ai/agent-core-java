package com.openjiuwen.auto_harness.stages;

/**
 * Mirrors Python's {@code TaskStage} in {@code openjiuwen.auto_harness.stages.base}.
 */
public abstract class TaskStage extends BaseStage {
    @Override
    public String scope() {
        return "task";
    }
}
