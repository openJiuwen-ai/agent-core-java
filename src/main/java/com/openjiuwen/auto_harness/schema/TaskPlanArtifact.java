package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan-stage structured artifact.
 *
 * <p>Mirrors Python's {@code TaskPlanArtifact} in
 * {@code openjiuwen.auto_harness.schema}.</p>
 */
public class TaskPlanArtifact {

    private List<OptimizationTask> tasks = new ArrayList<>();
    private String rawPlan = "";

    public TaskPlanArtifact() {
    }

    public TaskPlanArtifact(List<OptimizationTask> tasks, String rawPlan) {
        setTasks(tasks);
        setRawPlan(rawPlan);
    }

    public List<OptimizationTask> getTasks() {
        return new ArrayList<>(tasks);
    }

    public void setTasks(List<OptimizationTask> tasks) {
        this.tasks = tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
    }

    public String getRawPlan() {
        return rawPlan;
    }

    public void setRawPlan(String rawPlan) {
        this.rawPlan = rawPlan != null ? rawPlan : "";
    }
}
