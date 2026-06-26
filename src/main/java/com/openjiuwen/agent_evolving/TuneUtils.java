/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility functions for self-evolving operations.
 *
 * <p>Mirrors Python's {@code TuneUtils} and module helpers in
 * {@code openjiuwen/agent_evolving/utils.py}.</p>
 */
public final class TuneUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Pattern LEGACY_SKILL_MD_RE = Pattern.compile("[/\\\\]([^/\\\\]+)[/\\\\]SKILL\\.md",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILLS_PATH_RE = Pattern.compile("[/\\\\]skills[/\\\\]([^/\\\\]+)(?=[/\\\\])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_SKILL_TOOL_RE = Pattern.compile(
            "\\bskill_tool\\s*\\(\\s*skill_name\\s*=\\s*['\\\"]?([A-Za-z0-9._-]+)['\\\"]?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_BLOCK_RE = Pattern.compile("```json(.*?)```", Pattern.DOTALL);
    private static final Pattern LIST_BLOCK_RE = Pattern.compile("```list(.*?)```", Pattern.DOTALL);

    private TuneUtils() {
    }

    public static String inferSkillFromTexts(
            Collection<String> skillNames,
            Collection<?> skillToolPayloads,
            Collection<String> texts
    ) {
        Set<String> knownSkills = new LinkedHashSet<>(skillNames == null ? List.of() : skillNames);
        if (knownSkills.isEmpty()) {
            return null;
        }

        Map<String, SkillReferenceScore> hits = new LinkedHashMap<>();

        for (Object payload : skillToolPayloads == null ? List.of() : skillToolPayloads) {
            String skillName = extractSkillToolName(payload);
            if (knownSkills.contains(skillName)) {
                hits.computeIfAbsent(skillName, ignored -> new SkillReferenceScore()).incrementSkillToolHits();
            }
        }

        for (String text : texts == null ? List.<String>of() : texts) {
            for (String skillName : findSkillToolMentions(text)) {
                if (knownSkills.contains(skillName)) {
                    hits.computeIfAbsent(skillName, ignored -> new SkillReferenceScore()).incrementSkillToolHits();
                }
            }
            addRegexHits(SKILLS_PATH_RE, text, knownSkills, hits, HitKind.SKILLS_PATH);
            addRegexHits(LEGACY_SKILL_MD_RE, text, knownSkills, hits, HitKind.LEGACY_SKILL_MD);
        }

        String bestSkill = null;
        SkillReferenceScore bestScore = null;
        for (Map.Entry<String, SkillReferenceScore> entry : hits.entrySet()) {
            if (bestScore == null || compareRanking(entry.getValue(), bestScore) > 0) {
                bestSkill = entry.getKey();
                bestScore = entry.getValue();
            }
        }
        return bestSkill;
    }

    public static Map<String, String> parseTopLevelFrontmatter(String content) {
        String text = Objects.requireNonNull(content, "content").strip();
        if (!text.startsWith("---")) {
            return Map.of();
        }
        int end = text.indexOf("---", 3);
        if (end == -1) {
            return Map.of();
        }

        Map<String, String> frontmatter = new LinkedHashMap<>();
        String body = text.substring(3, end).strip();
        if (body.isEmpty()) {
            return frontmatter;
        }
        for (String line : body.split("\\R")) {
            if (line.isEmpty() || Character.isWhitespace(line.charAt(0)) || line.startsWith("-")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon == -1) {
                continue;
            }
            frontmatter.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
        }
        return frontmatter;
    }

    public static String extractSkillToolName(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object value = map.get("skill_name");
            return value == null || String.valueOf(value).isEmpty() ? "" : String.valueOf(value);
        }
        if (payload instanceof String text) {
            try {
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(text, new TypeReference<>() {
                });
                Object value = parsed.get("skill_name");
                return value == null || String.valueOf(value).isEmpty() ? "" : String.valueOf(value);
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                return "";
            }
        }
        return "";
    }

    public static List<String> findSkillToolMentions(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Matcher matcher = INLINE_SKILL_TOOL_RE.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    public static void validateDigitalParameter(double param, String paramName, double lower, double upper) {
        if (param < lower || param > upper) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR,
                    "error_msg",
                    paramName + " should be between " + lower + " and " + upper
            );
        }
    }

    public static String getInputStringFromCase(Case caseValue) {
        return convertDictToString(Objects.requireNonNull(caseValue, "caseValue").getInputs());
    }

    public static String getOutputStringFromMessage(BaseMessage message) {
        BaseMessage resolvedMessage = Objects.requireNonNull(message, "message");
        if (resolvedMessage instanceof AssistantMessage assistant
                && assistant.getToolCalls() != null
                && !assistant.getToolCalls().isEmpty()) {
            return assistant.getToolCalls().stream()
                    .map(TuneUtils::serializeToolCallForOutput)
                    .collect(Collectors.joining());
        }
        return resolvedMessage.getContentAsString();
    }

    public static String getContentStringFromTemplate(PromptTemplate template) {
        return Objects.requireNonNull(template, "template").toMessages().stream()
                .map(BaseMessage::getContentAsString)
                .collect(Collectors.joining("\n"));
    }

    public static Object parseJsonFromLlmResponse(String jsonLikeString) {
        return parseLlmResponse(jsonLikeString, JSON_BLOCK_RE);
    }

    public static List<Object> parseListFromLlmResponse(String listLikeString) {
        Object data = parseLlmResponse(listLikeString, LIST_BLOCK_RE);
        if (!(data instanceof List<?> list)) {
            return null;
        }
        return new ArrayList<>(list);
    }

    public static String convertCasesToExamples(List<?> cases) {
        if (cases == null || cases.isEmpty()) {
            return "";
        }
        List<String> examples = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            Object item = cases.get(i);
            Map<String, Object> inputs = caseInputs(item);
            Map<String, Object> label = caseLabel(item);
            examples.add("example " + (i + 1) + ":\n"
                    + "[question]: " + convertDictToString(inputs) + "\n"
                    + "[expected answer]: " + convertDictToString(label));
        }
        return String.join("\n", examples);
    }

    public static String convertDictToString(Map<?, ?> data) {
        return data.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(" | "));
    }

    public static Object parseLlmResponse(String value, Pattern pattern) {
        String matchedString = Objects.requireNonNull(value, "value");
        if (pattern != null) {
            Matcher matcher = pattern.matcher(matchedString);
            if (!matcher.find()) {
                return null;
            }
            matchedString = matcher.group(1).strip();
        }
        try {
            return OBJECT_MAPPER.readValue(matchedString, Object.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static void addRegexHits(
            Pattern pattern,
            String text,
            Set<String> knownSkills,
            Map<String, SkillReferenceScore> hits,
            HitKind hitKind
    ) {
        if (text == null) {
            return;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String skillName = matcher.group(1);
            if (!knownSkills.contains(skillName)) {
                continue;
            }
            SkillReferenceScore score = hits.computeIfAbsent(skillName, ignored -> new SkillReferenceScore());
            if (hitKind == HitKind.SKILLS_PATH) {
                score.incrementSkillsPathHits();
            } else {
                score.incrementLegacySkillMdHits();
            }
        }
    }

    private static int compareRanking(SkillReferenceScore left, SkillReferenceScore right) {
        for (int i = 0; i < left.rankingKey().size(); i++) {
            int compared = Integer.compare(left.rankingKey().get(i), right.rankingKey().get(i));
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static String serializeToolCallForOutput(ToolCall toolCall) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", toolCall.getName());
        data.put("arguments", toolCall.getArguments());
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            return String.valueOf(data);
        }
    }

    private static Map<String, Object> caseInputs(Object item) {
        if (item instanceof Case caseValue) {
            return caseValue.getInputs();
        }
        if (item instanceof EvaluatedCase evaluatedCase) {
            return evaluatedCase.getInputs();
        }
        throw new IllegalArgumentException("case item must be Case or EvaluatedCase");
    }

    private static Map<String, Object> caseLabel(Object item) {
        if (item instanceof Case caseValue) {
            return caseValue.getLabel();
        }
        if (item instanceof EvaluatedCase evaluatedCase) {
            return evaluatedCase.getLabel();
        }
        throw new IllegalArgumentException("case item must be Case or EvaluatedCase");
    }

    private enum HitKind {
        SKILLS_PATH,
        LEGACY_SKILL_MD
    }
}
