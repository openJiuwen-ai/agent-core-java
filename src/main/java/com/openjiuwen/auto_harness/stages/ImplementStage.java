package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.schema.StageResult;

public class ImplementStage extends TaskStage {
    @Override public String name() { return "implement"; }
    @Override public StageResult run(Object context) { return new StageResult(); }
}
