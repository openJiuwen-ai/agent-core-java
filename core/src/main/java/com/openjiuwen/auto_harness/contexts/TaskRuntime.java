/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.contexts;

import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;

import java.util.ArrayList;
import java.util.List;

/**
 * Prepared task-scoped execution dependencies.
 *
 * <p>Mirrors Python's {@code TaskRuntime} in
 * {@code openjiuwen/auto_harness/contexts/execution.py}.</p>
 */
public class TaskRuntime {

    private List<Experience> related = new ArrayList<>();
    private String wtPath = "";
    private Object editSafetyRail;
    private List<String> preexistingDirtyFiles = new ArrayList<>();
    private Object taskAgent;
    private Object commitAgent;
    private Object taskSession;
    private Object fixAgent;

    public List<Experience> getRelated() {
        return related;
    }

    public void setRelated(List<Experience> related) {
        this.related = related == null ? new ArrayList<>() : new ArrayList<>(related);
    }

    public String getWtPath() {
        return wtPath;
    }

    public void setWtPath(String wtPath) {
        this.wtPath = wtPath;
    }

    public Object getEditSafetyRail() {
        return editSafetyRail;
    }

    public void setEditSafetyRail(Object editSafetyRail) {
        this.editSafetyRail = editSafetyRail;
    }

    public List<String> getPreexistingDirtyFiles() {
        return preexistingDirtyFiles;
    }

    public void setPreexistingDirtyFiles(List<String> preexistingDirtyFiles) {
        this.preexistingDirtyFiles = preexistingDirtyFiles == null ? new ArrayList<>() : new ArrayList<>(preexistingDirtyFiles);
    }

    public Object getTaskAgent() {
        return taskAgent;
    }

    public void setTaskAgent(Object taskAgent) {
        this.taskAgent = taskAgent;
    }

    public Object getCommitAgent() {
        return commitAgent;
    }

    public void setCommitAgent(Object commitAgent) {
        this.commitAgent = commitAgent;
    }

    public Object getTaskSession() {
        return taskSession;
    }

    public void setTaskSession(Object taskSession) {
        this.taskSession = taskSession;
    }

    public Object getFixAgent() {
        return fixAgent;
    }

    public void setFixAgent(Object fixAgent) {
        this.fixAgent = fixAgent;
    }
}
