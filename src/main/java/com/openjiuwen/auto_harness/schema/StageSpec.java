package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code StageSpec} in {@code openjiuwen.auto_harness.schema}.
 */
public class StageSpec {

    private final String name;
    private final Class<?> stageClass;
    private final String scope;
    private final List<String> consumes;
    private final List<String> produces;
    private final String description;

    public StageSpec(String name, Class<?> stageClass, String scope, List<String> consumes, List<String> produces, String description) {
        this.name = name;
        this.stageClass = stageClass;
        this.scope = scope != null ? scope : "session";
        this.consumes = consumes != null ? new ArrayList<>(consumes) : new ArrayList<>();
        this.produces = produces != null ? new ArrayList<>(produces) : new ArrayList<>();
        this.description = description != null ? description : "";
    }

    public String getName() { return name; }
    public Class<?> getStageClass() { return stageClass; }
    public String getScope() { return scope; }
    public List<String> getConsumes() { return new ArrayList<>(consumes); }
    public List<String> getProduces() { return new ArrayList<>(produces); }
    public String getDescription() { return description; }
}
