/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import java.util.Map;

/**
 * Auto-harness run request DTO.
 * <p>
 * Mirrors Python's {@code AutoHarnessRunRequest} in {@code openjiuwen.harness.cli.cli}.
 */
public class AutoHarnessRunRequest {

    private String task;
    private String taskFile;
    private boolean dryRun;
    private String stage;
    private boolean noPush;
    private Double budget;
    private String goal;
    private String competitor;

    public static AutoHarnessRunRequest fromMap(Map<String, Object> kwargs) {
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setTask((String) kwargs.get("task"));
        request.setTaskFile((String) kwargs.get("task_file"));
        request.setDryRun(Boolean.TRUE.equals(kwargs.get("dry_run")));
        request.setStage((String) kwargs.get("stage"));
        request.setNoPush(Boolean.TRUE.equals(kwargs.get("no_push")));
        Object budgetObj = kwargs.get("budget");
        if (budgetObj instanceof Number number) {
            request.setBudget(number.doubleValue());
        }
        request.setGoal((String) kwargs.get("goal"));
        request.setCompetitor((String) kwargs.get("competitor"));
        return request;
    }

    public String getTask() { return task; }
    public void setTask(String task) { this.task = task; }
    public String getTaskFile() { return taskFile; }
    public void setTaskFile(String taskFile) { this.taskFile = taskFile; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public boolean isNoPush() { return noPush; }
    public void setNoPush(boolean noPush) { this.noPush = noPush; }
    public Double getBudget() { return budget; }
    public void setBudget(Double budget) { this.budget = budget; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public String getCompetitor() { return competitor; }
    public void setCompetitor(String competitor) { this.competitor = competitor; }
}
