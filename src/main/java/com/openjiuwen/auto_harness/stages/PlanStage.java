package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.schema.StageResult;

public class PlanStage extends SessionStage {
    @Override public String name() { return "plan"; }
    @Override public StageResult run(Object context) { return new StageResult(); }
}
