package com.openjiuwen.harness.schema.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors Python's {@code TodoItem} in {@code openjiuwen.harness.schema.task}.
 */
public class TodoItem {

    private final String id;
    private String content;
    private String activeForm;
    private String description;
    private TodoStatus status;
    private List<String> dependsOn;
    private String resultSummary;
    private Map<String, Object> metaData;
    private String selectedModelId;

    public TodoItem(String id, String content, String activeForm, String description, TodoStatus status,
                    List<String> dependsOn, String resultSummary, Map<String, Object> metaData, String selectedModelId) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.content = content == null ? "" : content;
        this.activeForm = activeForm == null ? "" : activeForm;
        this.description = description == null ? "" : description;
        this.status = status == null ? TodoStatus.PENDING : status;
        this.dependsOn = dependsOn != null ? new ArrayList<>(dependsOn) : new ArrayList<>();
        this.resultSummary = resultSummary;
        this.metaData = metaData != null ? new LinkedHashMap<>(metaData) : new LinkedHashMap<>();
        this.selectedModelId = selectedModelId;
    }

    public TodoItem(String id, String content) {
        this(id, content, "", "", TodoStatus.PENDING, List.of(), null, Map.of(), null);
    }

    public static TodoItem create(String content) {
        return create(content, "", "", TodoStatus.PENDING, null);
    }

    public static TodoItem create(String content, String selectedModelId) {
        return create(content, "", "", TodoStatus.PENDING, selectedModelId);
    }

    public static TodoItem create(String content, String activeForm, String description, TodoStatus status, String selectedModelId) {
        String safeContent = content == null ? "" : content;
        String resolvedActiveForm = activeForm == null || activeForm.isBlank()
                ? "Executing " + safeContent
                : activeForm;
        return new TodoItem(UUID.randomUUID().toString(), safeContent, resolvedActiveForm, description,
                status, List.of(), null, Map.of(), selectedModelId);
    }

    public static TodoItem fromMap(Map<String, Object> map) {
        if (map == null) {
            return create("", "", "", TodoStatus.PENDING, null);
        }
        @SuppressWarnings("unchecked")
        List<String> dependsOn = map.get("depends_on") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> metaData = map.get("meta_data") instanceof Map<?, ?> raw
                ? raw.entrySet().stream().collect(LinkedHashMap::new,
                (acc, entry) -> acc.put(String.valueOf(entry.getKey()), entry.getValue()),
                Map::putAll)
                : Map.of();
        return new TodoItem(
                stringValue(map.get("id"), UUID.randomUUID().toString()),
                stringValue(map.get("content"), ""),
                stringValue(map.get("activeForm"), stringValue(map.get("active_form"), "")),
                stringValue(map.get("description"), ""),
                TodoStatus.fromValue(stringValue(map.get("status"), "pending")),
                dependsOn,
                stringOrNull(map.get("result_summary")),
                metaData,
                stringOrNull(map.get("selected_model_id"))
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("content", content);
        data.put("activeForm", activeForm);
        data.put("description", description);
        data.put("status", status.getValue());
        data.put("depends_on", new ArrayList<>(dependsOn));
        data.put("result_summary", resultSummary);
        data.put("meta_data", new LinkedHashMap<>(metaData));
        data.put("selected_model_id", selectedModelId);
        return data;
    }

    public String getId() { return id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content == null ? "" : content; }
    public String getActiveForm() { return activeForm; }
    public void setActiveForm(String activeForm) { this.activeForm = activeForm == null ? "" : activeForm; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description == null ? "" : description; }
    public TodoStatus getStatus() { return status; }
    public void setStatus(TodoStatus status) { this.status = status == null ? TodoStatus.PENDING : status; }
    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn != null ? new ArrayList<>(dependsOn) : new ArrayList<>(); }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public Map<String, Object> getMetaData() { return metaData; }
    public void setMetaData(Map<String, Object> metaData) { this.metaData = metaData != null ? new LinkedHashMap<>(metaData) : new LinkedHashMap<>(); }
    public String getSelectedModelId() { return selectedModelId; }
    public void setSelectedModelId(String selectedModelId) { this.selectedModelId = selectedModelId; }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
