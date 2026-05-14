package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.schema.StageResult;

public class LearningsStage extends SessionStage {
    @Override public String name() { return "learnings"; }
    @Override public StageResult run(Object context) { return new StageResult(); }
}
