/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public class TodoItem used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoItem {
    private String id;
    private String content;
    private String activeForm;
    private String description;
    @Builder.Default
    private TodoStatus status = TodoStatus.TODO;

    @JsonProperty("depends_on")
    @JsonAlias("dependsOn")
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> dependsOn = new ArrayList<>();

    @JsonProperty("result_summary")
    @JsonAlias("resultSummary")
    private String resultSummary;

    @JsonProperty("meta_data")
    @JsonAlias("metaData")
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metaData = new LinkedHashMap<>();

    @JsonProperty("selected_model_id")
    @JsonAlias("selectedModelId")
    private String selectedModelId;

    private String priority;

    /**
     * create.
     * 
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    public static TodoItem create(String content) {
        return create(content, null, null, TodoStatus.TODO, null);
    }

    /**
     * create.
     * 
     * @param content content
     * @param activeForm activeForm
     * @param description description
     * @param status status
     * @param selectedModelId selectedModelId
     * @return the result
     * @since 0.1.7
     */
    public static TodoItem create(String content, String activeForm, String description, TodoStatus status,
            String selectedModelId) {
        return TodoItem.builder().id(UUID.randomUUID().toString()).content(content == null ? "" : content)
                .activeForm(activeForm == null || activeForm.isBlank()
                        ? "Executing " + (content == null ? "" : content)
                        : activeForm)
                .description(description == null ? "" : description).status(status == null ? TodoStatus.TODO : status)
                .selectedModelId(selectedModelId).build();
    }

    /**
     * isPending.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public boolean isPending() {
        return status == TodoStatus.TODO || status == TodoStatus.PENDING;
    }

    /**
     * isCompleted.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public boolean isCompleted() {
        return status == TodoStatus.DONE || status == TodoStatus.COMPLETED;
    }

    /**
     * isTerminal.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public boolean isTerminal() {
        return isCompleted() || status == TodoStatus.CANCELLED;
    }
}
