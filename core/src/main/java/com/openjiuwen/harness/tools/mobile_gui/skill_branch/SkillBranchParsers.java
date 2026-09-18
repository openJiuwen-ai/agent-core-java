/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's parser helpers in
 * {@code openjiuwen/harness/tools/mobile_gui/skill_branch/parsers.py}.
 */
public final class SkillBranchParsers {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern CODE_BLOCK_PATTERN =
            Pattern.compile("```(?:python|json)?\\s*\\R?(.*?)```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern LOAD_SKILL_IMAGES_PATTERN =
            Pattern.compile("LOAD_SKILL_IMAGES\\((.*)\\)\\s*;?", Pattern.DOTALL);
    private static final Set<String> VALID_APPLICABILITY = Set.of("effective", "ineffective", "uncertain");
    private static final Set<String> VALID_COMPLETION_SCOPE =
            Set.of("local_only", "needs_verification", "maybe_complete");

    private SkillBranchParsers() {
    }

    public static ParseOutcome<LoadSkillImagesPayload> parseLoadSkillImagesResponse(
            String response,
            Set<String> validImageIds,
            int maxImages
    ) {
        if (trimmed(response).isEmpty()) {
            return ParseOutcome.error("Empty image-selection response.");
        }

        String body = extractFirstCodeBlock(response);
        List<String> rawLines = new ArrayList<>();
        for (String line : body.split("\\R")) {
            String stripped = trimmed(line);
            if (!stripped.isEmpty() && !stripped.startsWith("#")) {
                rawLines.add(stripped);
            }
        }
        String normalized = String.join("\n", rawLines).trim();
        Matcher match = LOAD_SKILL_IMAGES_PATTERN.matcher(normalized);
        if (!match.matches()) {
            return ParseOutcome.error("Response must be a single LOAD_SKILL_IMAGES({...}) call.");
        }

        String payloadText = trimmed(match.group(1));
        if (payloadText.isEmpty()) {
            payloadText = "{\"visual_reference_needed\": false, "
                    + "\"why_not_text_only\": \"No visual references requested.\", "
                    + "\"requests\": []}";
        }

        Object parsedRaw;
        try {
            parsedRaw = OBJECT_MAPPER.readValue(payloadText, Object.class);
        } catch (JsonProcessingException ex) {
            return ParseOutcome.error("LOAD_SKILL_IMAGES payload must be valid JSON: " + ex.getOriginalMessage());
        }

        if (!(parsedRaw instanceof Map<?, ?> rawMap)) {
            return ParseOutcome.error("LOAD_SKILL_IMAGES payload must be a JSON object.");
        }

        Boolean visualNeeded = parseBool(rawMap.get("visual_reference_needed"));
        String whyNot = trimmed(rawMap.get("why_not_text_only"));
        Object requestsValue = rawMap.containsKey("requests") ? rawMap.get("requests") : List.of();
        if (!(requestsValue instanceof List<?> requestsRaw)) {
            return ParseOutcome.error("`requests` must be a JSON list.");
        }

        if (visualNeeded == null) {
            visualNeeded = !requestsRaw.isEmpty();
        }

        if (!visualNeeded) {
            if (!requestsRaw.isEmpty()) {
                return ParseOutcome.error("When visual_reference_needed is false, requests must be empty.");
            }
            if (whyNot.isEmpty()) {
                return ParseOutcome.error("When visual_reference_needed is false, why_not_text_only is required.");
            }
            return ParseOutcome.success(new LoadSkillImagesPayload(false, whyNot, List.of()));
        }

        if (whyNot.isEmpty()) {
            return ParseOutcome.error("When visual_reference_needed is true, why_not_text_only is required.");
        }
        if (requestsRaw.isEmpty()) {
            return ParseOutcome.error("When visual_reference_needed is true, requests must be non-empty.");
        }

        LinkedHashMap<String, LoadSkillImageRequest> merged = new LinkedHashMap<>();
        for (Object item : requestsRaw) {
            if (!(item instanceof Map<?, ?> requestMap)) {
                return ParseOutcome.error("Each request must be a JSON object.");
            }

            String imageId = trimmed(requestMap.get("image_id"));
            if (imageId.isEmpty()) {
                return ParseOutcome.error("Each request must include a non-empty image_id.");
            }
            if (validImageIds != null && !validImageIds.contains(imageId)) {
                return ParseOutcome.error("Unknown image_id: " + imageId);
            }

            String reason = trimmed(requestMap.get("reason"));
            if (reason.isEmpty()) {
                return ParseOutcome.error("reason is required for image_id '" + imageId + "'.");
            }
            merged.put(imageId, new LoadSkillImageRequest(imageId, reason));
        }

        if (merged.size() > maxImages) {
            return ParseOutcome.error("Select at most " + maxImages + " images, got " + merged.size() + ".");
        }

        return ParseOutcome.success(new LoadSkillImagesPayload(true, whyNot, List.copyOf(merged.values())));
    }

    public static ParseOutcome<PlannerJsonPayload> parsePlannerJsonResponse(String response) {
        if (trimmed(response).isEmpty()) {
            return ParseOutcome.error("Empty planner response.");
        }

        String body = extractFirstCodeBlock(response);
        Object payloadRaw;
        try {
            payloadRaw = OBJECT_MAPPER.readValue(body, Object.class);
        } catch (JsonProcessingException ex) {
            return ParseOutcome.error("Planner response must be valid JSON: " + ex.getOriginalMessage());
        }

        if (!(payloadRaw instanceof Map<?, ?> payload)) {
            return ParseOutcome.error("Planner response must be a JSON object.");
        }

        String applicability = trimmed(payload.get("skill_applicability")).toLowerCase(Locale.ROOT);
        if (!VALID_APPLICABILITY.contains(applicability)) {
            return ParseOutcome.error("skill_applicability must be effective, ineffective, or uncertain.");
        }

        PlannerJsonPayload planner = new PlannerJsonPayload(
                applicability,
                trimmed(payload.get("subgoal")),
                trimmed(payload.get("plan")),
                trimmed(payload.get("do_not_do")),
                trimmed(payload.get("fallback_if_no_progress")),
                trimmed(payload.get("expected_state")),
                trimmed(payload.get("completion_scope")).toLowerCase(Locale.ROOT)
        );

        if (planner.subgoal().isEmpty()) {
            return ParseOutcome.error("The `subgoal` field must be a non-empty string.");
        }
        if (planner.plan().isEmpty()) {
            return ParseOutcome.error("The `plan` field must be a non-empty string.");
        }
        if (planner.doNotDo().isEmpty()) {
            return ParseOutcome.error("The `do_not_do` field must be a non-empty string.");
        }
        if (planner.fallbackIfNoProgress().isEmpty()) {
            return ParseOutcome.error("The `fallback_if_no_progress` field must be a non-empty string.");
        }
        if (planner.expectedState().isEmpty()) {
            return ParseOutcome.error("The `expected_state` field must be a non-empty string.");
        }
        if (!VALID_COMPLETION_SCOPE.contains(planner.completionScope())) {
            return ParseOutcome.error(
                    "completion_scope must be one of: local_only, needs_verification, maybe_complete"
            );
        }

        return ParseOutcome.success(planner);
    }

    private static String extractFirstCodeBlock(String text) {
        Matcher match = CODE_BLOCK_PATTERN.matcher(text == null ? "" : text);
        if (match.find()) {
            return trimmed(match.group(1));
        }
        return trimmed(text);
    }

    private static Boolean parseBool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String raw) {
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if (Set.of("true", "yes", "1").contains(normalized)) {
                return true;
            }
            if (Set.of("false", "no", "0").contains(normalized)) {
                return false;
            }
        }
        return null;
    }

    private static String trimmed(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record ParseOutcome<T>(T parsed, String error) {

        public static <T> ParseOutcome<T> success(T parsed) {
            return new ParseOutcome<>(parsed, null);
        }

        public static <T> ParseOutcome<T> error(String error) {
            return new ParseOutcome<>(null, error);
        }
    }

    /**
     * Mirrors Python's branch stage-1 request item in
     * {@code openjiuwen/harness/tools/mobile_gui/skill_branch/parsers.py}.
     */
    public record LoadSkillImageRequest(String imageId, String reason) {
    }

    /**
     * Mirrors Python's parsed stage-1 selection payload in
     * {@code openjiuwen/harness/tools/mobile_gui/skill_branch/parsers.py}.
     */
    public record LoadSkillImagesPayload(
            boolean visualReferenceNeeded,
            String whyNotTextOnly,
            List<LoadSkillImageRequest> requests
    ) {
    }

    /**
     * Mirrors Python's parsed planner payload in
     * {@code openjiuwen/harness/tools/mobile_gui/skill_branch/parsers.py}.
     */
    public record PlannerJsonPayload(
            String skillApplicability,
            String subgoal,
            String plan,
            String doNotDo,
            String fallbackIfNoProgress,
            String expectedState,
            String completionScope
    ) {
    }
}
