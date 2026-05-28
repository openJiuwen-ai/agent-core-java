/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience.LLMInvokePolicy;

/**
 * LLM-based patch generation and rewrite support for team skills.
 * <p>
 * Mirrors Python's {@code TeamSkillOptimizer} in
 * {@code openjiuwen.agent_evolving.optimizer.team_skill_optimizer}.
 */
public class TeamSkillOptimizer {

    private static final Logger logger = Logger.getLogger(TeamSkillOptimizer.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*\\n(.*?)```", Pattern.DOTALL);
    
    // Prompt templates (simplified for Java - full prompts loaded from resources)
    private static final Map<String, String> PROPOSE_PROMPTS = new HashMap<>();
    private static final Map<String, String> PATCH_PROMPTS = new HashMap<>();
    private static final Map<String, String> USER_PATCH_PROMPTS = new HashMap<>();
    private static final Map<String, String> TRAJECTORY_PATCH_PROMPTS = new HashMap<>();
    
    // LLM policy defaults
    private static final int DEFAULT_ATTEMPT_TIMEOUT_SECS = 120;
    private static final int DEFAULT_TOTAL_BUDGET_SECS = 420;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    
    // Character limits for retry prompts
    private static final int PATCH_RETRY_SKILL_CONTENT_CHARS = 3000;
    private static final int PATCH_RETRY_TRAJECTORY_CHARS = 6000;
    private static final int TRAJECTORY_ISSUES_RETRY_CHARS = 2000;
    private static final int USER_INTENT_RETRY_CHARS = 500;
    private static final int SUMMARY_RETRY_CHARS = 200;
    
    private Object llm;  // Model client
    private String model;
    private String language;
    private String debugDir;
    private LLMInvokePolicy patchLlmPolicy;

    /**
     * Initialize the Team Skill Optimizer.
     *
     * @param llm LLM client model
     * @param model Model name for LLM calls
     * @param language Language for prompts ("cn" or "en")
     * @param debugDir Optional directory for debug output
     * @param patchLlmPolicy Policy for LLM invocation
     */
    public TeamSkillOptimizer(
        Object llm,
        String model,
        String language,
        String debugDir,
        LLMInvokePolicy patchLlmPolicy
    ) {
        this.llm = llm;
        this.model = model != null ? model : "";
        this.language = language != null ? language : "cn";
        this.debugDir = debugDir;
        this.patchLlmPolicy = patchLlmPolicy != null ? patchLlmPolicy : 
            LLMInvokePolicy.defaultPolicy(DEFAULT_ATTEMPT_TIMEOUT_SECS, DEFAULT_TOTAL_BUDGET_SECS);
        
        // Initialize prompts
        initializePrompts();
    }

    private void initializePrompts() {
        // Load prompt templates - simplified versions for Java
        PROPOSE_PROMPTS.put("cn", getProposePromptCn());
        PROPOSE_PROMPTS.put("en", getProposePromptEn());
        PATCH_PROMPTS.put("cn", getPatchPromptCn());
        PATCH_PROMPTS.put("en", getPatchPromptEn());
    }

    // -- Properties --

    public String getLanguage() { return language; }
    public Object getLlm() { return llm; }
    public String getModel() { return model; }
    public LLMInvokePolicy getPatchLlmPolicy() { return patchLlmPolicy; }

    public void updateLlm(Object llm, String model) {
        this.llm = llm;
        this.model = model;
    }

    // -- Main Methods --

    /**
     * Analyze trajectory against existing skill and generate a patch if warranted.
     *
     * @param trajectory Trajectory to analyze
     * @param skillName Name of the skill being patched
     * @param currentSkillContent Current skill content
     * @return Optional EvolutionRecord with patch details
     */
    public CompletableFuture<Optional<EvolutionRecord>> generatePatch(
        Object trajectory,
        String skillName,
        String currentSkillContent
    ) {
        return CompletableFuture.supplyAsync(() -> {
            String summary = buildTrajectorySummary(trajectory);
            
            logger.info("[TeamSkillOptimizer] patch: skill='" + skillName + 
                "', summary_len=" + summary.length() + 
                ", content_len=" + currentSkillContent.length());

            String promptTemplate = PATCH_PROMPTS.getOrDefault(language, PATCH_PROMPTS.get("en"));
            String prompt = promptTemplate.replace("{skill_content}", 
                truncate(currentSkillContent, 15000))
                .replace("{trajectory_summary}", summary);

            String retryPromptTemplate = PATCH_PROMPTS.getOrDefault(language, PATCH_PROMPTS.get("en"));
            String retryPrompt = retryPromptTemplate
                .replace("{skill_content}", truncate(currentSkillContent, PATCH_RETRY_SKILL_CONTENT_CHARS))
                .replace("{trajectory_summary}", truncate(summary, PATCH_RETRY_TRAJECTORY_CHARS));

            int approxTokens = prompt.length() / 4;
            logger.info("[TeamSkillOptimizer] patch: prompt_len=" + prompt.length() + 
                " (~" + approxTokens + " tokens), model=" + model);

            // Call LLM
            long startTime = System.currentTimeMillis();
            String rawResponse = callLlm(prompt, retryPrompt);

            // Parse response
            Map<String, Object> parsed = parseJsonFromLlm(rawResponse);
            if (parsed == null) {
                return Optional.empty();
            }

            Boolean needPatch = (Boolean) parsed.getOrDefault("need_patch", false);
            if (!Boolean.TRUE.equals(needPatch)) {
                logger.info("[TeamSkillOptimizer] patch: skill='" + skillName + "' no patch needed");
                return Optional.empty();
            }

            String section = (String) parsed.getOrDefault("section", "");
            String content = (String) parsed.getOrDefault("content", "");
            String reason = (String) parsed.getOrDefault("reason", "");

            EvolutionPatch patch = new EvolutionPatch(section, "append", content);
            EvolutionRecord record = new EvolutionRecord(
                skillName,
                EvolutionTarget.TEAM_SKILL,
                patch,
                reason,
                System.currentTimeMillis() - startTime
            );

            logger.info("[TeamSkillOptimizer] patch: skill='" + skillName + 
                "' generated patch for section: " + section);
            
            return Optional.of(record);
        });
    }

    /**
     * Propose a new team skill from a trajectory.
     *
     * @param trajectory Trajectory to analyze
     * @param existingSkillNames List of existing skill names
     * @return Optional map with proposed skill details
     */
    public CompletableFuture<Optional<Map<String, Object>>> proposeNewSkill(
        Object trajectory,
        String existingSkillNames
    ) {
        return CompletableFuture.supplyAsync(() -> {
            String summary = buildTrajectorySummary(trajectory);
            
            String promptTemplate = PROPOSE_PROMPTS.getOrDefault(language, PROPOSE_PROMPTS.get("en"));
            String prompt = promptTemplate
                .replace("{trajectory_summary}", summary)
                .replace("{existing_skill_names}", existingSkillNames != null ? existingSkillNames : "");

            String rawResponse = callLlm(prompt, null);
            Map<String, Object> parsed = parseJsonFromLlm(rawResponse);
            
            if (parsed == null) {
                return Optional.empty();
            }

            Boolean shouldCreate = (Boolean) parsed.getOrDefault("should_create", false);
            if (!Boolean.TRUE.equals(shouldCreate)) {
                return Optional.empty();
            }

            return Optional.of(parsed);
        });
    }

    // -- Helper Methods --

    private String buildTrajectorySummary(Object trajectory) {
        // Requires Trajectory class implementation
        // Placeholder returns a basic summary
        if (trajectory == null) {
            return "";
        }
        return trajectory.toString();
    }

    private String callLlm(String prompt, String retryPrompt) {
        // Requires LLM client implementation
        // Placeholder returns empty response
        try {
            return invokeTextWithRetry(llm, model, prompt, retryPrompt, patchLlmPolicy);
        } catch (Exception e) {
            logger.warning("LLM call failed: " + e.getMessage());
            return "";
        }
    }

    private String invokeTextWithRetry(Object llm, String model, String prompt, 
            String retryPrompt, LLMInvokePolicy policy) {
        // Placeholder for LLM invocation with retry logic
        return "";
    }

    private Map<String, Object> parseJsonFromLlm(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        // Try direct parse
        Map<String, Object> result = tryParseJson(text);
        if (result != null) {
            return result;
        }

        // Fix and retry
        String fixed = fixJsonText(text);
        result = tryParseJson(fixed);
        if (result != null) {
            return result;
        }

        // Extract from code block
        java.util.regex.Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            String block = matcher.group(1).trim();
            result = tryParseJson(block);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private Map<String, Object> tryParseJson(String text) {
        try {
            return objectMapper.readValue(text, 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private String fixJsonText(String text) {
        // Strip code fences
        text = text.replaceAll("^```(?:json)?\\s*", "").trim();
        text = text.replaceAll("```\\s*$", "");
        // Remove line comments
        text = text.replaceAll("//[^\\n]*", "");
        // Remove trailing commas
        text = text.replaceAll(",\\s*([}\\]])", "$1");
        return text.trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    // -- Prompt Templates (simplified) --

    private String getProposePromptCn() {
        return "你是一个多角色协作 Skill 设计专家。根据 AgentTeam 的完整执行 trajectory，判断是否值得提炼一个可复用的 Team Skill。\n" +
               "## Trajectory 摘要\n{trajectory_summary}\n" +
               "## 已有 Team Skill 列表\n{existing_skill_names}\n" +
               "输出JSON格式的判断结果。";
    }

    private String getProposePromptEn() {
        return "You are a multi-agent collaboration Skill designer. Based on the trajectory, determine whether a reusable Team Skill should be extracted.\n" +
               "## Trajectory Summary\n{trajectory_summary}\n" +
               "## Existing Team Skills\n{existing_skill_names}\n" +
               "Output JSON format result.";
    }

    private String getPatchPromptCn() {
        return "你是 Team Skill 演进专家。对比本次 AgentTeam 执行 trajectory 与当前 Team Skill 内容，判断有没有值得沉淀的经验。\n" +
               "## 当前 Team Skill 内容\n{skill_content}\n" +
               "## 本次 Trajectory 摘要\n{trajectory_summary}\n" +
               "输出JSON格式的判断结果。";
    }

    private String getPatchPromptEn() {
        return "You are a Team Skill evolution expert. Compare the trajectory against the current Team Skill and determine if any new learnings should be captured.\n" +
               "## Current Team Skill Content\n{skill_content}\n" +
               "## Trajectory Summary\n{trajectory_summary}\n" +
               "Output JSON format result.";
    }
}