package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.schema.StageResult;

public class PublishPrStage extends TaskStage {
    @Override public String name() { return "publish_pr"; }
    @Override public StageResult run(Object context) { return new StageResult(); }
}
