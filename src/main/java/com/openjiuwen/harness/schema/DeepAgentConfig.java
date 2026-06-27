/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.security.PermissionsSection;
import com.openjiuwen.harness.security.ToolPermissionHost;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime construction config for DeepAgent.
 *
 * <p>Mirrors Python's {@code DeepAgentConfig}, {@code SubAgentConfig},
 * {@code VisionModelConfig}, and {@code AudioModelConfig} in
 * {@code openjiuwen/harness/schema/config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeepAgentConfig {

    public static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_OPENROUTER_VISION_MODEL = "google/gemini-2.5-pro";
    public static final String DEFAULT_OPENAI_VISION_MODEL = "gpt-4.1-mini";
    public static final String DEFAULT_OPENAI_AUDIO_TRANSCRIPTION_MODEL = "gpt-4o-transcribe";
    public static final String DEFAULT_OPENAI_AUDIO_QA_MODEL = "gpt-4o-audio-preview";
    public static final String DEFAULT_ACR_BASE_URL = "https://identify-ap-southeast-1.acrcloud.com/v1/identify";
    public static final int DEFAULT_AUDIO_HTTP_TIMEOUT = 20;
    public static final int DEFAULT_MAX_AUDIO_BYTES = 25 * 1024 * 1024;

    private Object model;
    private AgentCard card;
    private String systemPrompt;
    private Object contextEngineConfig;
    private boolean enableTaskLoop;
    private boolean enableAsyncSubagent;
    private boolean addGeneralPurposeAgent;
    private int maxIterations = 15;
    private final List<Tool> tools = new ArrayList<>();
    private final List<Object> mcps = new ArrayList<>();
    private Object workspace;
    private Object skills;
    private boolean enableSkillDiscovery;
    private Object backend;
    private Object sysOperation;
    private boolean autoCreateWorkspace = true;
    private double completionTimeout = 600.0;
    private String language;
    private String promptMode;
    private VisionModelConfig visionModelConfig;
    private AudioModelConfig audioModelConfig;
    private boolean enableReadImageMultimodal = true;
    private final List<DeepAgentRail> rails = new ArrayList<>();
    private boolean enablePlanMode;
    private final Map<String, Object> modelSelection = new LinkedHashMap<>();
    private boolean progressiveToolEnabled;
    private final List<String> progressiveToolAlwaysVisibleTools = new ArrayList<>();
    private final List<String> progressiveToolDefaultVisibleTools = new ArrayList<>();
    private int progressiveToolMaxLoadedTools = 12;
    private AgentMode defaultMode = AgentMode.NORMAL;
    private PermissionsSection permissions;
    private ToolPermissionHost permissionHost;
    private final Map<String, SubAgentConfig> subagents = new LinkedHashMap<>();

    public Object getModel() {
        return model;
    }

    public void setModel(Object model) {
        this.model = model;
    }

    public AgentCard getCard() {
        return card;
    }

    public void setCard(AgentCard card) {
        this.card = card;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public Object getContextEngineConfig() {
        return contextEngineConfig;
    }

    public void setContextEngineConfig(Object contextEngineConfig) {
        this.contextEngineConfig = contextEngineConfig;
    }

    public boolean isEnableTaskLoop() {
        return enableTaskLoop;
    }

    public void setEnableTaskLoop(boolean enableTaskLoop) {
        this.enableTaskLoop = enableTaskLoop;
    }

    public boolean isEnableAsyncSubagent() {
        return enableAsyncSubagent;
    }

    public void setEnableAsyncSubagent(boolean enableAsyncSubagent) {
        this.enableAsyncSubagent = enableAsyncSubagent;
    }

    public boolean isAddGeneralPurposeAgent() {
        return addGeneralPurposeAgent;
    }

    public void setAddGeneralPurposeAgent(boolean addGeneralPurposeAgent) {
        this.addGeneralPurposeAgent = addGeneralPurposeAgent;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public List<Tool> getTools() {
        return tools;
    }

    public void setTools(List<Tool> tools) {
        this.tools.clear();
        if (tools != null) {
            this.tools.addAll(tools);
        }
    }

    public List<Object> getMcps() {
        return mcps;
    }

    public void setMcps(List<Object> mcps) {
        this.mcps.clear();
        if (mcps != null) {
            this.mcps.addAll(mcps);
        }
    }

    public Object getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Object workspace) {
        this.workspace = workspace;
    }

    public Object getSkills() {
        return skills;
    }

    public void setSkills(Object skills) {
        this.skills = skills;
    }

    public boolean isEnableSkillDiscovery() {
        return enableSkillDiscovery;
    }

    public void setEnableSkillDiscovery(boolean enableSkillDiscovery) {
        this.enableSkillDiscovery = enableSkillDiscovery;
    }

    public Object getBackend() {
        return backend;
    }

    public void setBackend(Object backend) {
        this.backend = backend;
    }

    public Object getSysOperation() {
        return sysOperation;
    }

    public void setSysOperation(Object sysOperation) {
        this.sysOperation = sysOperation;
    }

    public boolean isAutoCreateWorkspace() {
        return autoCreateWorkspace;
    }

    public void setAutoCreateWorkspace(boolean autoCreateWorkspace) {
        this.autoCreateWorkspace = autoCreateWorkspace;
    }

    public double getCompletionTimeout() {
        return completionTimeout;
    }

    public void setCompletionTimeout(double completionTimeout) {
        this.completionTimeout = completionTimeout;
    }

    public String getLanguage() {
        return language == null || language.isBlank() ? "cn" : language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getPromptMode() {
        return promptMode;
    }

    public void setPromptMode(String promptMode) {
        this.promptMode = promptMode;
    }

    public VisionModelConfig getVisionModelConfig() {
        return visionModelConfig;
    }

    public void setVisionModelConfig(VisionModelConfig visionModelConfig) {
        this.visionModelConfig = visionModelConfig;
    }

    public AudioModelConfig getAudioModelConfig() {
        return audioModelConfig;
    }

    public void setAudioModelConfig(AudioModelConfig audioModelConfig) {
        this.audioModelConfig = audioModelConfig;
    }

    public boolean isEnableReadImageMultimodal() {
        return enableReadImageMultimodal;
    }

    public void setEnableReadImageMultimodal(boolean enableReadImageMultimodal) {
        this.enableReadImageMultimodal = enableReadImageMultimodal;
    }

    public List<DeepAgentRail> getRails() {
        return rails;
    }

    public void setRails(List<DeepAgentRail> rails) {
        this.rails.clear();
        if (rails != null) {
            this.rails.addAll(rails);
        }
    }

    public boolean isEnablePlanMode() {
        return enablePlanMode;
    }

    public void setEnablePlanMode(boolean enablePlanMode) {
        this.enablePlanMode = enablePlanMode;
    }

    public Map<String, Object> getModelSelection() {
        return modelSelection;
    }

    public void setModelSelection(Map<String, Object> modelSelection) {
        this.modelSelection.clear();
        if (modelSelection != null) {
            this.modelSelection.putAll(modelSelection);
        }
    }

    public boolean isProgressiveToolEnabled() {
        return progressiveToolEnabled;
    }

    public void setProgressiveToolEnabled(boolean progressiveToolEnabled) {
        this.progressiveToolEnabled = progressiveToolEnabled;
    }

    public List<String> getProgressiveToolAlwaysVisibleTools() {
        return progressiveToolAlwaysVisibleTools;
    }

    public void setProgressiveToolAlwaysVisibleTools(List<String> tools) {
        this.progressiveToolAlwaysVisibleTools.clear();
        if (tools != null) {
            this.progressiveToolAlwaysVisibleTools.addAll(tools);
        }
    }

    public List<String> getProgressiveToolDefaultVisibleTools() {
        return progressiveToolDefaultVisibleTools;
    }

    public void setProgressiveToolDefaultVisibleTools(List<String> tools) {
        this.progressiveToolDefaultVisibleTools.clear();
        if (tools != null) {
            this.progressiveToolDefaultVisibleTools.addAll(tools);
        }
    }

    public int getProgressiveToolMaxLoadedTools() {
        return progressiveToolMaxLoadedTools;
    }

    public void setProgressiveToolMaxLoadedTools(int progressiveToolMaxLoadedTools) {
        this.progressiveToolMaxLoadedTools = progressiveToolMaxLoadedTools;
    }

    public AgentMode getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(AgentMode defaultMode) {
        this.defaultMode = defaultMode == null ? AgentMode.NORMAL : defaultMode;
    }

    public PermissionsSection getPermissions() {
        return permissions;
    }

    public void setPermissions(PermissionsSection permissions) {
        this.permissions = permissions;
    }

    public ToolPermissionHost getPermissionHost() {
        return permissionHost;
    }

    public void setPermissionHost(ToolPermissionHost permissionHost) {
        this.permissionHost = permissionHost;
    }

    public Map<String, SubAgentConfig> getSubagents() {
        return subagents;
    }

    public void setSubagents(Map<String, SubAgentConfig> subagents) {
        this.subagents.clear();
        if (subagents != null) {
            this.subagents.putAll(subagents);
        }
    }

    /**
     * Mirrors Python's {@code VisionModelConfig} in
     * {@code openjiuwen/harness/schema/config.py}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class VisionModelConfig {
        private String apiKey = "";
        private String baseUrl = DEFAULT_OPENAI_BASE_URL;
        private String model = DEFAULT_OPENAI_VISION_MODEL;
        private int maxRetries = 3;

        public static VisionModelConfig fromEnv() {
            return fromEnvironment(System.getenv());
        }

        public static VisionModelConfig fromEnvironment(Map<String, String> env) {
            VisionModelConfig config = new VisionModelConfig();
            config.apiKey = firstValue(env, "VISION_API_KEY", "OPENROUTER_API_KEY", "OPENAI_API_KEY");
            config.baseUrl = firstValueOrDefault(
                    env,
                    DEFAULT_OPENAI_BASE_URL,
                    "VISION_BASE_URL",
                    "VISION_API_BASE",
                    "OPENROUTER_BASE_URL",
                    "OPENAI_BASE_URL"
            );
            String modelName = firstValue(env, "VISION_MODEL", "VISION_MODEL_NAME");
            if (modelName == null || modelName.isBlank()) {
                modelName = config.baseUrl.contains("openrouter.ai")
                        ? DEFAULT_OPENROUTER_VISION_MODEL
                        : DEFAULT_OPENAI_VISION_MODEL;
            }
            config.model = modelName;
            config.maxRetries = parseIntFromMap(env, "VISION_MAX_RETRIES", 3);
            return config;
        }

        @JsonProperty("api_key")
        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = blankDefault(apiKey, "");
        }

        @JsonProperty("base_url")
        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = blankDefault(baseUrl, DEFAULT_OPENAI_BASE_URL);
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = blankDefault(model, DEFAULT_OPENAI_VISION_MODEL);
        }

        @JsonProperty("max_retries")
        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }

    /**
     * Mirrors Python's {@code AudioModelConfig} in
     * {@code openjiuwen/harness/schema/config.py}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class AudioModelConfig {
        private String apiKey = "";
        private String baseUrl = DEFAULT_OPENAI_BASE_URL;
        private String transcriptionModel = DEFAULT_OPENAI_AUDIO_TRANSCRIPTION_MODEL;
        private String questionAnsweringModel = DEFAULT_OPENAI_AUDIO_QA_MODEL;
        private int maxRetries = 3;
        private int httpTimeout = DEFAULT_AUDIO_HTTP_TIMEOUT;
        private int maxAudioBytes = DEFAULT_MAX_AUDIO_BYTES;
        private String acrAccessKey = "";
        private String acrAccessSecret = "";
        private String acrBaseUrl = DEFAULT_ACR_BASE_URL;

        public static AudioModelConfig fromEnv() {
            return fromEnvironment(System.getenv());
        }

        public static AudioModelConfig fromEnvironment(Map<String, String> env) {
            AudioModelConfig config = new AudioModelConfig();
            config.apiKey = firstValue(env, "AUDIO_API_KEY", "OPENAI_API_KEY");
            config.baseUrl = firstValueOrDefault(
                    env,
                    DEFAULT_OPENAI_BASE_URL,
                    "AUDIO_BASE_URL",
                    "AUDIO_API_BASE",
                    "OPENAI_BASE_URL"
            );
            config.transcriptionModel = firstValueOrDefault(
                    env,
                    DEFAULT_OPENAI_AUDIO_TRANSCRIPTION_MODEL,
                    "AUDIO_TRANSCRIPTION_MODEL",
                    "AUDIO_MODEL_NAME"
            );
            config.questionAnsweringModel = firstValueOrDefault(
                    env,
                    DEFAULT_OPENAI_AUDIO_QA_MODEL,
                    "AUDIO_QUESTION_ANSWERING_MODEL"
            );
            config.maxRetries = parseIntFromMap(env, "AUDIO_MAX_RETRIES", 3);
            config.httpTimeout = parseIntFromMap(env, "AUDIO_HTTP_TIMEOUT", DEFAULT_AUDIO_HTTP_TIMEOUT);
            config.maxAudioBytes = parseIntFromMap(env, "AUDIO_MAX_AUDIO_BYTES", DEFAULT_MAX_AUDIO_BYTES);
            config.acrAccessKey = blankDefault(mapValue(env, "ACR_ACCESS_KEY"), "");
            config.acrAccessSecret = blankDefault(mapValue(env, "ACR_ACCESS_SECRET"), "");
            config.acrBaseUrl = blankDefault(mapValue(env, "ACR_BASE_URL"), DEFAULT_ACR_BASE_URL);
            return config;
        }

        @JsonProperty("api_key")
        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = blankDefault(apiKey, "");
        }

        @JsonProperty("base_url")
        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = blankDefault(baseUrl, DEFAULT_OPENAI_BASE_URL);
        }

        @JsonProperty("transcription_model")
        public String getTranscriptionModel() {
            return transcriptionModel;
        }

        public void setTranscriptionModel(String transcriptionModel) {
            this.transcriptionModel = blankDefault(transcriptionModel, DEFAULT_OPENAI_AUDIO_TRANSCRIPTION_MODEL);
        }

        @JsonProperty("question_answering_model")
        public String getQuestionAnsweringModel() {
            return questionAnsweringModel;
        }

        public void setQuestionAnsweringModel(String questionAnsweringModel) {
            this.questionAnsweringModel = blankDefault(questionAnsweringModel, DEFAULT_OPENAI_AUDIO_QA_MODEL);
        }

        @JsonProperty("max_retries")
        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        @JsonProperty("http_timeout")
        public int getHttpTimeout() {
            return httpTimeout;
        }

        public void setHttpTimeout(int httpTimeout) {
            this.httpTimeout = httpTimeout;
        }

        @JsonProperty("max_audio_bytes")
        public int getMaxAudioBytes() {
            return maxAudioBytes;
        }

        public void setMaxAudioBytes(int maxAudioBytes) {
            this.maxAudioBytes = maxAudioBytes;
        }

        @JsonProperty("acr_access_key")
        public String getAcrAccessKey() {
            return acrAccessKey;
        }

        public void setAcrAccessKey(String acrAccessKey) {
            this.acrAccessKey = blankDefault(acrAccessKey, "");
        }

        @JsonProperty("acr_access_secret")
        public String getAcrAccessSecret() {
            return acrAccessSecret;
        }

        public void setAcrAccessSecret(String acrAccessSecret) {
            this.acrAccessSecret = blankDefault(acrAccessSecret, "");
        }

        @JsonProperty("acr_base_url")
        public String getAcrBaseUrl() {
            return acrBaseUrl;
        }

        public void setAcrBaseUrl(String acrBaseUrl) {
            this.acrBaseUrl = blankDefault(acrBaseUrl, DEFAULT_ACR_BASE_URL);
        }
    }

    /**
     * Mirrors Python's {@code SubAgentConfig} in
     * {@code openjiuwen/harness/schema/config.py}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class SubAgentConfig {
        @JsonProperty("agent_card")
        private AgentCard agentCard;
        private String systemPrompt;
        private final List<Tool> tools = new ArrayList<>();
        private final List<Object> mcps = new ArrayList<>();
        private Object model;
        private final List<DeepAgentRail> rails = new ArrayList<>();
        private final List<String> skills = new ArrayList<>();
        private Object backend;
        private Object workspace;
        private Object sysOperation;
        private String language;
        private String promptMode;
        private boolean enableTaskLoop;
        private Integer maxIterations;
        private String factoryName;
        private final Map<String, Object> factoryKwargs = new LinkedHashMap<>();
        private boolean enablePlanMode;
        private boolean restrictToWorkDir = true;
        private DeepAgentConfig config;
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        public SubAgentConfig() {
        }

        public SubAgentConfig(String name, String description, String systemPrompt) {
            this.agentCard = new AgentCard(name, name, description);
            this.systemPrompt = systemPrompt;
        }

        public AgentCard getAgentCard() {
            return agentCard;
        }

        public void setAgentCard(AgentCard agentCard) {
            this.agentCard = agentCard;
        }

        public AgentCard getCard() {
            return agentCard;
        }

        public void setCard(AgentCard card) {
            this.agentCard = card;
        }

        public String getName() {
            return agentCard == null ? null : agentCard.getName();
        }

        public void setName(String name) {
            if (agentCard == null) {
                agentCard = new AgentCard();
            }
            agentCard.setName(name);
            if (agentCard.getId() == null || agentCard.getId().isBlank()) {
                agentCard.setId(name);
            }
        }

        public String getDescription() {
            return agentCard == null ? null : agentCard.getDescription();
        }

        public void setDescription(String description) {
            if (agentCard == null) {
                agentCard = new AgentCard();
            }
            agentCard.setDescription(description);
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public List<Tool> getTools() {
            return tools;
        }

        public void setTools(List<Tool> tools) {
            this.tools.clear();
            if (tools != null) {
                this.tools.addAll(tools);
            }
        }

        public List<Object> getMcps() {
            return mcps;
        }

        public void setMcps(List<Object> mcps) {
            this.mcps.clear();
            if (mcps != null) {
                this.mcps.addAll(mcps);
            }
        }

        public Object getModel() {
            return model;
        }

        public void setModel(Object model) {
            this.model = model;
        }

        public List<DeepAgentRail> getRails() {
            return rails;
        }

        public void setRails(List<DeepAgentRail> rails) {
            this.rails.clear();
            if (rails != null) {
                this.rails.addAll(rails);
            }
        }

        public List<String> getSkills() {
            return skills;
        }

        public void setSkills(List<String> skills) {
            this.skills.clear();
            if (skills != null) {
                this.skills.addAll(skills);
            }
        }

        public Object getBackend() {
            return backend;
        }

        public void setBackend(Object backend) {
            this.backend = backend;
        }

        public Object getWorkspace() {
            return workspace;
        }

        public void setWorkspace(Object workspace) {
            this.workspace = workspace;
        }

        public Object getSysOperation() {
            return sysOperation;
        }

        public void setSysOperation(Object sysOperation) {
            this.sysOperation = sysOperation;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getPromptMode() {
            return promptMode;
        }

        public void setPromptMode(String promptMode) {
            this.promptMode = promptMode;
        }

        public boolean isEnableTaskLoop() {
            return enableTaskLoop;
        }

        public void setEnableTaskLoop(boolean enableTaskLoop) {
            this.enableTaskLoop = enableTaskLoop;
        }

        public Integer getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(Integer maxIterations) {
            this.maxIterations = maxIterations;
        }

        public String getFactoryName() {
            return factoryName;
        }

        public void setFactoryName(String factoryName) {
            this.factoryName = factoryName;
        }

        public Map<String, Object> getFactoryKwargs() {
            return factoryKwargs;
        }

        public void setFactoryKwargs(Map<String, Object> factoryKwargs) {
            this.factoryKwargs.clear();
            if (factoryKwargs != null) {
                this.factoryKwargs.putAll(factoryKwargs);
            }
        }

        public boolean isEnablePlanMode() {
            return enablePlanMode;
        }

        public void setEnablePlanMode(boolean enablePlanMode) {
            this.enablePlanMode = enablePlanMode;
        }

        public boolean isRestrictToWorkDir() {
            return restrictToWorkDir;
        }

        public void setRestrictToWorkDir(boolean restrictToWorkDir) {
            this.restrictToWorkDir = restrictToWorkDir;
        }

        public DeepAgentConfig getConfig() {
            return config;
        }

        public void setConfig(DeepAgentConfig config) {
            this.config = config;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata.clear();
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
        }
    }

    private static String firstEnv(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String firstEnvOrDefault(String defaultValue, String... names) {
        String value = firstEnv(names);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String blankDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int parseIntFromEnv(String name, int defaultValue) {
        String value = System.getenv(name);
        return parseInt(value, defaultValue);
    }

    private static String firstValue(Map<String, String> values, String... names) {
        for (String name : names) {
            String value = mapValue(values, name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String firstValueOrDefault(Map<String, String> values, String defaultValue, String... names) {
        String value = firstValue(values, names);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int parseIntFromMap(Map<String, String> values, String name, int defaultValue) {
        return parseInt(mapValue(values, name), defaultValue);
    }

    private static String mapValue(Map<String, String> values, String name) {
        return values == null ? null : values.get(name);
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
