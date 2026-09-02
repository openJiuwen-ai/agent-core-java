/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.service;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.service.task_memory_service.AddMemoryRequest}.
 * 
 * @since 0.1.7
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
     * AddMemoryRequest.
     * 
     * @since 0.1.7
     */
    public AddMemoryRequest() {
        // Default constructor
    }

    /**
     * AddMemoryRequest.
     * 
     * @param content content
     * @since 0.1.7
     */
    public AddMemoryRequest(String content) {
        this.content = content;
    }

    /**
     * getContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getContent() {
        return content;
    }

    /**
     * setContent.
     * 
     * @param content content
     * @since 0.1.7
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * getQuery.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getQuery() {
        return query;
    }

    /**
     * setQuery.
     * 
     * @param query query
     * @since 0.1.7
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * getWhenToUse.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getWhenToUse() {
        return whenToUse;
    }

    /**
     * setWhenToUse.
     * 
     * @param whenToUse whenToUse
     * @since 0.1.7
     */
    public void setWhenToUse(String whenToUse) {
        this.whenToUse = whenToUse;
    }

    /**
     * getTitle.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTitle() {
        return title;
    }

    /**
     * setTitle.
     * 
     * @param title title
     * @since 0.1.7
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * getDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDescription() {
        return description;
    }

    /**
     * setDescription.
     * 
     * @param description description
     * @since 0.1.7
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * getSection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSection() {
        return section;
    }

    /**
     * setSection.
     * 
     * @param section section
     * @since 0.1.7
     */
    public void setSection(String section) {
        this.section = section != null && !section.isBlank() ? section : "general";
    }

    /**
     * getLabel.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Boolean getLabel() {
        return label;
    }

    /**
     * setLabel.
     * 
     * @param label label
     * @since 0.1.7
     */
    public void setLabel(Boolean label) {
        this.label = label;
    }
}
