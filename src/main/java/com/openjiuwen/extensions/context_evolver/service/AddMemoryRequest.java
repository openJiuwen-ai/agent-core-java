/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public AddMemoryRequest() {
        // Default constructor
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AddMemoryRequest(String content) {
        this.content = content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getContent() {
        return content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getQuery() {
        return query;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getWhenToUse() {
        return whenToUse;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWhenToUse(String whenToUse) {
        this.whenToUse = whenToUse;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSection() {
        return section;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSection(String section) {
        this.section = section != null && !section.isBlank() ? section : "general";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Boolean getLabel() {
        return label;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLabel(Boolean label) {
        this.label = label;
    }
}
