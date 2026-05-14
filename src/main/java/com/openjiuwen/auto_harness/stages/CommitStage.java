package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.schema.StageResult;

public class CommitStage extends TaskStage {
    @Override public String name() { return "commit"; }
    @Override public StageResult run(Object context) { return new StageResult(); }
}
