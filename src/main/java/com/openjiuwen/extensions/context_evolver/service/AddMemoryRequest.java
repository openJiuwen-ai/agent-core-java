/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.service;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.service.task_memory_service.AddMemoryRequest}.
 */
public class AddMemoryRequest {

    private String content;
    private String query;
    private String whenToUse;
    private String title;
    private String description;
    private String section = "general";
    private Boolean label;

    public AddMemoryRequest() {
        // Default constructor
    }

    public AddMemoryRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getWhenToUse() {
        return whenToUse;
    }

    public void setWhenToUse(String whenToUse) {
        this.whenToUse = whenToUse;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section != null && !section.isBlank() ? section : "general";
    }

    public Boolean getLabel() {
        return label;
    }

    public void setLabel(Boolean label) {
        this.label = label;
    }
}
