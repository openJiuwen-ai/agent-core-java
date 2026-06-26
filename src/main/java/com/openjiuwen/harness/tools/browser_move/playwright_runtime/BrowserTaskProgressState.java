/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Continuation progress state for browser tasks.
 *
 * <p>Mirrors Python's {@code BrowserTaskProgressState} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/service.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrowserTaskProgressState {

    private String requestId = "";
    private String status = "unknown";
    private final List<String> completedSteps = new ArrayList<>();
    private final List<String> remainingSteps = new ArrayList<>();
    private String nextStep = "";
    private final List<String> completionEvidence = new ArrayList<>();
    private final List<String> missingRequirements = new ArrayList<>();
    private final List<String> recentToolSteps = new ArrayList<>();
    private String lastPageUrl = "";
    private String lastPageTitle = "";
    private Object lastScreenshot;
    private String lastWorkerFinal = "";

    public static BrowserTaskProgressState fromMap(Map<String, Object> data) {
        BrowserTaskProgressState state = new BrowserTaskProgressState();
        if (data == null) {
            return state;
        }
        state.setRequestId(stringValue(data.get("request_id")));
        state.setStatus(stringValue(data.getOrDefault("status", "unknown")));
        state.setCompletedSteps(stringList(data.get("completed_steps")));
        state.setRemainingSteps(stringList(data.get("remaining_steps")));
        state.setNextStep(stringValue(data.get("next_step")));
        state.setCompletionEvidence(stringList(data.get("completion_evidence")));
        state.setMissingRequirements(stringList(data.get("missing_requirements")));
        state.setRecentToolSteps(stringList(data.get("recent_tool_steps")));
        if (data.get("last_page") instanceof Map<?, ?> page) {
            state.setLastPageUrl(stringValue(page.get("url")));
            state.setLastPageTitle(stringValue(page.get("title")));
        }
        state.setLastScreenshot(data.get("last_screenshot"));
        state.setLastWorkerFinal(stringValue(data.get("last_worker_final")));
        return state;
    }

    public boolean isEmpty() {
        return "unknown".equals(status)
                && completedSteps.isEmpty()
                && remainingSteps.isEmpty()
                && nextStep.isBlank()
                && completionEvidence.isEmpty()
                && missingRequirements.isEmpty()
                && recentToolSteps.isEmpty()
                && lastPageUrl.isBlank()
                && lastPageTitle.isBlank()
                && lastWorkerFinal.isBlank()
                && (lastScreenshot == null || String.valueOf(lastScreenshot).isBlank());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("completed_steps", new ArrayList<>(completedSteps));
        result.put("remaining_steps", new ArrayList<>(remainingSteps));
        result.put("next_step", nextStep.isBlank() ? null : nextStep);
        result.put("completion_evidence", new ArrayList<>(completionEvidence));
        result.put("missing_requirements", new ArrayList<>(missingRequirements));
        result.put("recent_tool_steps", new ArrayList<>(recentToolSteps));
        result.put("last_page", Map.of("url", lastPageUrl, "title", lastPageTitle));
        result.put("last_screenshot", lastScreenshot);
        result.put("last_worker_final", lastWorkerFinal.isBlank() ? null : lastWorkerFinal);
        result.put("request_id", requestId.isBlank() ? null : requestId);
        return result;
    }

    @JsonProperty("request_id")
    public String getRequestId() {
        return requestId;
    }

    @JsonProperty("request_id")
    public void setRequestId(String requestId) {
        this.requestId = requestId == null ? "" : requestId.trim();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        String value = status == null ? "" : status.trim();
        this.status = value.isEmpty() ? "unknown" : value;
    }

    @JsonProperty("completed_steps")
    public List<String> getCompletedSteps() {
        return new ArrayList<>(completedSteps);
    }

    @JsonProperty("completed_steps")
    public void setCompletedSteps(List<String> completedSteps) {
        this.completedSteps.clear();
        this.completedSteps.addAll(completedSteps == null ? List.of() : completedSteps);
    }

    @JsonProperty("remaining_steps")
    public List<String> getRemainingSteps() {
        return new ArrayList<>(remainingSteps);
    }

    @JsonProperty("remaining_steps")
    public void setRemainingSteps(List<String> remainingSteps) {
        this.remainingSteps.clear();
        this.remainingSteps.addAll(remainingSteps == null ? List.of() : remainingSteps);
    }

    @JsonProperty("next_step")
    public String getNextStep() {
        return nextStep;
    }

    @JsonProperty("next_step")
    public void setNextStep(String nextStep) {
        this.nextStep = nextStep == null ? "" : nextStep.trim();
    }

    @JsonProperty("completion_evidence")
    public List<String> getCompletionEvidence() {
        return new ArrayList<>(completionEvidence);
    }

    @JsonProperty("completion_evidence")
    public void setCompletionEvidence(List<String> completionEvidence) {
        this.completionEvidence.clear();
        this.completionEvidence.addAll(completionEvidence == null ? List.of() : completionEvidence);
    }

    @JsonProperty("missing_requirements")
    public List<String> getMissingRequirements() {
        return new ArrayList<>(missingRequirements);
    }

    @JsonProperty("missing_requirements")
    public void setMissingRequirements(List<String> missingRequirements) {
        this.missingRequirements.clear();
        this.missingRequirements.addAll(missingRequirements == null ? List.of() : missingRequirements);
    }

    @JsonProperty("recent_tool_steps")
    public List<String> getRecentToolSteps() {
        return new ArrayList<>(recentToolSteps);
    }

    @JsonProperty("recent_tool_steps")
    public void setRecentToolSteps(List<String> recentToolSteps) {
        this.recentToolSteps.clear();
        this.recentToolSteps.addAll(recentToolSteps == null ? List.of() : recentToolSteps);
    }

    public String getLastPageUrl() {
        return lastPageUrl;
    }

    public void setLastPageUrl(String lastPageUrl) {
        this.lastPageUrl = lastPageUrl == null ? "" : lastPageUrl.trim();
    }

    public String getLastPageTitle() {
        return lastPageTitle;
    }

    public void setLastPageTitle(String lastPageTitle) {
        this.lastPageTitle = lastPageTitle == null ? "" : lastPageTitle.trim();
    }

    public Object getLastScreenshot() {
        return lastScreenshot;
    }

    public void setLastScreenshot(Object lastScreenshot) {
        this.lastScreenshot = lastScreenshot;
    }

    public String getLastWorkerFinal() {
        return lastWorkerFinal;
    }

    public void setLastWorkerFinal(String lastWorkerFinal) {
        this.lastWorkerFinal = lastWorkerFinal == null ? "" : lastWorkerFinal.trim();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            String text = stringValue(item);
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }
}
