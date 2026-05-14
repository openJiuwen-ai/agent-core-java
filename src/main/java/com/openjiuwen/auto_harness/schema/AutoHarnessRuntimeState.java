package com.openjiuwen.auto_harness.schema;

/**
 * Mirrors Python's {@code AutoHarnessRuntimeState} in {@code openjiuwen.auto_harness.schema}.
 */
public class AutoHarnessRuntimeState {

    private String currentWorkspace = "";
    private String selectedPipeline = "";
    private boolean configBootstrapped;
    private String suggestedLocalRepo = "";

    public String getCurrentWorkspace() { return currentWorkspace; }
    public void setCurrentWorkspace(String currentWorkspace) { this.currentWorkspace = currentWorkspace; }
    public String getSelectedPipeline() { return selectedPipeline; }
    public void setSelectedPipeline(String selectedPipeline) { this.selectedPipeline = selectedPipeline; }
    public boolean isConfigBootstrapped() { return configBootstrapped; }
    public void setConfigBootstrapped(boolean configBootstrapped) { this.configBootstrapped = configBootstrapped; }
    public String getSuggestedLocalRepo() { return suggestedLocalRepo; }
    public void setSuggestedLocalRepo(String suggestedLocalRepo) { this.suggestedLocalRepo = suggestedLocalRepo; }
}
