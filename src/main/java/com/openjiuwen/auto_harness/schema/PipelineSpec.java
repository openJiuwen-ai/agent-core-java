package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code PipelineSpec} in {@code openjiuwen.auto_harness.schema}.
 */
public class PipelineSpec {

    private final String name;
    private final Class<?> pipelineClass;
    private final String description;
    private final List<String> expectedOutputs;

    public PipelineSpec(String name, Class<?> pipelineClass, String description, List<String> expectedOutputs) {
        this.name = name;
        this.pipelineClass = pipelineClass;
        this.description = description != null ? description : "";
        this.expectedOutputs = expectedOutputs != null ? new ArrayList<>(expectedOutputs) : new ArrayList<>();
    }

    public String getName() { return name; }
    public Class<?> getPipelineClass() { return pipelineClass; }
    public String getDescription() { return description; }
    public List<String> getExpectedOutputs() { return new ArrayList<>(expectedOutputs); }
}
