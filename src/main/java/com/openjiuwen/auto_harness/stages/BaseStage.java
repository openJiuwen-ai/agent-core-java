package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.auto_harness.schema.StageSpec;

import java.util.List;

/**
 * Mirrors Python's {@code BaseStage} in {@code openjiuwen.auto_harness.stages.base}.
 */
public abstract class BaseStage {

    public static final String NAME = "";

    public abstract String name();

    public String description() {
        return "";
    }

    public List<String> consumes() {
        return List.of();
    }

    public List<String> produces() {
        return List.of();
    }

    public String scope() {
        return "session";
    }

    public StageSpec spec() {
        return new StageSpec(name(), getClass(), scope(), consumes(), produces(), description());
    }

    public abstract StageResult run(Object context);
}
