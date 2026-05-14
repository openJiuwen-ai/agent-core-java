package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.schema.StageResult;

public class VerifyStage extends TaskStage {
    @Override public String name() { return "verify"; }
    @Override public StageResult run(Object context) { return new StageResult(); }
}
