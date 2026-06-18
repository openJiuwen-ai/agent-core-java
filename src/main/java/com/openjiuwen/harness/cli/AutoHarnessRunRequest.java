/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import java.util.Map;

/**
 * Auto-harness run request DTO.
 *
 * <p>Mirrors Python's {@code AutoHarnessRunRequest} in
 * {@code openjiuwen/harness/cli/cli.py}.</p>
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
        Map<String, Object> safe = kwargs == null ? Map.of() : kwargs;
        AutoHarnessRunRequest request = new AutoHarnessRunRequest();
        request.setTask(stringValue(firstPresent(safe, "task")));
        request.setTaskFile(stringValue(firstPresent(safe, "task_file", "taskFile")));
        request.setDryRun(booleanValue(firstPresent(safe, "dry_run", "dryRun")));
        request.setStage(stringValue(firstPresent(safe, "stage")));
        request.setNoPush(booleanValue(firstPresent(safe, "no_push", "noPush")));
        request.setBudget(doubleValue(firstPresent(safe, "budget")));
        request.setGoal(stringValue(firstPresent(safe, "goal")));
        request.setCompetitor(stringValue(firstPresent(safe, "competitor")));
        return request;
    }

    public boolean hasManualTasks() {
        return !isBlank(task) || !isBlank(taskFile);
    }

    private static Object firstPresent(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getTaskFile() {
        return taskFile;
    }

    public void setTaskFile(String taskFile) {
        this.taskFile = taskFile;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public boolean isNoPush() {
        return noPush;
    }

    public void setNoPush(boolean noPush) {
        this.noPush = noPush;
    }

    public Double getBudget() {
        return budget;
    }

    public void setBudget(Double budget) {
        this.budget = budget;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getCompetitor() {
        return competitor;
    }

    public void setCompetitor(String competitor) {
        this.competitor = competitor;
    }
}
