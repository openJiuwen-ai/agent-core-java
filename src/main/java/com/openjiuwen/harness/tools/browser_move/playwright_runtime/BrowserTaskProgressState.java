package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Browser task progress state for tracking browser task execution.
 *
 * <p>Mirrors Python's {@code BrowserTaskProgressState} in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.service}.</p>
 */
public class BrowserTaskProgressState {

    private String requestId = "";
    private String status = "unknown";
    private List<String> completedSteps = new ArrayList<>();
    private List<String> remainingSteps = new ArrayList<>();
    private String nextStep = "";
    private List<String> completionEvidence = new ArrayList<>();
    private List<String> missingRequirements = new ArrayList<>();
    private List<String> recentToolSteps = new ArrayList<>();
    private String lastPageUrl = "";
    private String lastPageTitle = "";
    private Object lastScreenshot = null;
    private String lastWorkerFinal = "";

    public BrowserTaskProgressState() {
    }

    public boolean isEmpty() {
        return "unknown".equals(status)
                && completedSteps.isEmpty()
                && remainingSteps.isEmpty()
                && nextStep.isBlank()
                && completionEvidence.isEmpty()
                && missingRequirements.isEmpty()
                && recentToolSteps.isEmpty()
                && lastPageUrl.isEmpty()
                && lastPageTitle.isEmpty()
                && lastWorkerFinal.isEmpty()
                && (lastScreenshot == null || "".equals(lastScreenshot));
    }

    public static BrowserTaskProgressState fromDict(Map<String, Object> data) {
        if (data == null) {
            return new BrowserTaskProgressState();
        }
        BrowserTaskProgressState state = new BrowserTaskProgressState();

        state.setRequestId(getString(data, "request_id"));
        String status = getString(data, "status");
        state.setStatus(status.isEmpty() ? "unknown" : status);

        state.setCompletedSteps(getStringList(data, "completed_steps"));
        state.setRemainingSteps(getStringList(data, "remaining_steps"));
        state.setNextStep(getString(data, "next_step"));
        state.setCompletionEvidence(getStringList(data, "completion_evidence"));
        state.setMissingRequirements(getStringList(data, "missing_requirements"));
        state.setRecentToolSteps(getStringList(data, "recent_tool_steps"));

        Object lastPageObj = data.get("last_page");
        if (lastPageObj instanceof Map) {
            Map<String, Object> lastPage = (Map<String, Object>) lastPageObj;
            state.setLastPageUrl(getString(lastPage, "url"));
            state.setLastPageTitle(getString(lastPage, "title"));
        }

        state.setLastScreenshot(data.get("last_screenshot"));
        state.setLastWorkerFinal(getString(data, "last_worker_final"));

        return state;
    }

    protected static String getString(Map<String, Object> map, String key) {
        if (!map.containsKey(key) || map.get(key) == null) {
            return "";
        }
        return String.valueOf(map.get(key)).trim();
    }

    protected static List<String> getStringList(Map<String, Object> map, String key) {
        if (!map.containsKey(key) || map.get(key) == null) {
            return new ArrayList<>();
        }
        Object obj = map.get(key);
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                String str = item != null ? String.valueOf(item).trim() : "";
                if (!str.isEmpty()) {
                    result.add(str);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    public Map<String, Object> toDict() {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("status", status);
        dict.put("completed_steps", new ArrayList<>(completedSteps));
        dict.put("remaining_steps", new ArrayList<>(remainingSteps));
        dict.put("next_step", nextStep.isEmpty() ? null : nextStep);
        dict.put("completion_evidence", new ArrayList<>(completionEvidence));
        dict.put("missing_requirements", new ArrayList<>(missingRequirements));
        dict.put("recent_tool_steps", new ArrayList<>(recentToolSteps));

        Map<String, Object> lastPage = new LinkedHashMap<>();
        lastPage.put("url", lastPageUrl);
        lastPage.put("title", lastPageTitle);
        dict.put("last_page", lastPage);

        dict.put("last_screenshot", lastScreenshot);
        dict.put("last_worker_final", lastWorkerFinal.isEmpty() ? null : lastWorkerFinal);
        dict.put("request_id", requestId.isEmpty() ? null : requestId);
        return dict;
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId != null ? requestId.trim() : ""; }

    public String getStatus() { return status; }
    public void setStatus(String status) {
        String normalized = status != null ? status.trim() : "";
        this.status = normalized.isEmpty() ? "unknown" : normalized;
    }

    public List<String> getCompletedSteps() { return completedSteps; }
    public void setCompletedSteps(List<String> completedSteps) {
        this.completedSteps = completedSteps != null ? new ArrayList<>(completedSteps) : new ArrayList<>();
    }

    public List<String> getRemainingSteps() { return remainingSteps; }
    public void setRemainingSteps(List<String> remainingSteps) {
        this.remainingSteps = remainingSteps != null ? new ArrayList<>(remainingSteps) : new ArrayList<>();
    }

    public String getNextStep() { return nextStep; }
    public void setNextStep(String nextStep) { this.nextStep = nextStep != null ? nextStep.trim() : ""; }

    public List<String> getCompletionEvidence() { return completionEvidence; }
    public void setCompletionEvidence(List<String> completionEvidence) {
        this.completionEvidence = completionEvidence != null ? new ArrayList<>(completionEvidence) : new ArrayList<>();
    }

    public List<String> getMissingRequirements() { return missingRequirements; }
    public void setMissingRequirements(List<String> missingRequirements) {
        this.missingRequirements = missingRequirements != null ? new ArrayList<>(missingRequirements) : new ArrayList<>();
    }

    public List<String> getRecentToolSteps() { return recentToolSteps; }
    public void setRecentToolSteps(List<String> recentToolSteps) {
        this.recentToolSteps = recentToolSteps != null ? new ArrayList<>(recentToolSteps) : new ArrayList<>();
    }

    public String getLastPageUrl() { return lastPageUrl; }
    public void setLastPageUrl(String lastPageUrl) { this.lastPageUrl = lastPageUrl != null ? lastPageUrl.trim() : ""; }

    public String getLastPageTitle() { return lastPageTitle; }
    public void setLastPageTitle(String lastPageTitle) { this.lastPageTitle = lastPageTitle != null ? lastPageTitle.trim() : ""; }

    public Object getLastScreenshot() { return lastScreenshot; }
    public void setLastScreenshot(Object lastScreenshot) { this.lastScreenshot = lastScreenshot; }

    public String getLastWorkerFinal() { return lastWorkerFinal; }
    public void setLastWorkerFinal(String lastWorkerFinal) { this.lastWorkerFinal = lastWorkerFinal != null ? lastWorkerFinal.trim() : ""; }
}
