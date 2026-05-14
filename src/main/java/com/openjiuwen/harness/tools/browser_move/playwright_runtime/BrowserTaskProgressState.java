package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code BrowserTaskProgressState} in browser_move service.
 */
public class BrowserTaskProgressState {

    private String requestId = "";
    private String status = "unknown";
    private List<String> completedSteps = new ArrayList<>();
    private List<String> remainingSteps = new ArrayList<>();
    private String nextStep = "";
    private List<String> completionEvidence = new ArrayList<>();
    private List<String> missingRequirements = new ArrayList<>();

    public boolean isEmpty() {
        return "unknown".equals(status)
                && completedSteps.isEmpty()
                && remainingSteps.isEmpty()
                && nextStep.isBlank()
                && completionEvidence.isEmpty()
                && missingRequirements.isEmpty();
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getCompletedSteps() { return completedSteps; }
    public void setCompletedSteps(List<String> completedSteps) { this.completedSteps = completedSteps != null ? new ArrayList<>(completedSteps) : new ArrayList<>(); }
    public List<String> getRemainingSteps() { return remainingSteps; }
    public void setRemainingSteps(List<String> remainingSteps) { this.remainingSteps = remainingSteps != null ? new ArrayList<>(remainingSteps) : new ArrayList<>(); }
    public String getNextStep() { return nextStep; }
    public void setNextStep(String nextStep) { this.nextStep = nextStep; }
    public List<String> getCompletionEvidence() { return completionEvidence; }
    public void setCompletionEvidence(List<String> completionEvidence) { this.completionEvidence = completionEvidence != null ? new ArrayList<>(completionEvidence) : new ArrayList<>(); }
    public List<String> getMissingRequirements() { return missingRequirements; }
    public void setMissingRequirements(List<String> missingRequirements) { this.missingRequirements = missingRequirements != null ? new ArrayList<>(missingRequirements) : new ArrayList<>(); }
}
