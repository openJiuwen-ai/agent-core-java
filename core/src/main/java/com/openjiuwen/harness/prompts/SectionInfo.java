/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

/**
 * Mirrors Python's {@code SectionInfo} in
 * {@code openjiuwen/harness/prompts/report.py}.
 */
public class SectionInfo {

    private String name;
    private int priority;
    private int charCount;

    public SectionInfo() {
    }

    public SectionInfo(String name, int priority, int charCount) {
        this.name = name;
        this.priority = priority;
        this.charCount = charCount;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getCharCount() {
        return charCount;
    }

    public void setCharCount(int charCount) {
        this.charCount = charCount;
    }
}
