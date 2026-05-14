package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.schema.StageResult;

public class AssessStage extends SessionStage {
    @Override public String name() { return "assess"; }
    @Override public StageResult run(Object context) { return new StageResult(); }
}
