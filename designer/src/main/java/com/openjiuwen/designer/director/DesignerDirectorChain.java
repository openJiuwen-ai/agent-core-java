/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.director;

import com.fasterxml.jackson.databind.JsonNode;
import com.openjiuwen.designer.DesignerJson;
import com.openjiuwen.designer.execution.DesignerNodeException;
import com.openjiuwen.designer.schema.DesignerGraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side director chain: intent, analysis, brief, storyboard, graph, validation.
 *
 * @since 2026-11-06
 */
public final class DesignerDirectorChain {

    private static final Logger LOG = LoggerFactory.getLogger(DesignerDirectorChain.class);

    private static final int JSON_TOKENS = 32768;
    private static final int BRIEF_TOKENS = 32768;

    private static final String ANALYSIS_SYSTEM = """
            You are a film director. Return JSON only, no markdown fences.
            Shape: {"story_name":"","style_lock":{"look":"","medium":""},
            "characters":[{"id":"char_1","name":"","description":""}],
            "clips":[{"clip_index":1,"title":"","action":"","camera":"",
            "on_screen":["char_1"],"setting_id":"set_1","clip_prompt":"","timeline":"0-5s"}],
            "scenes":[{"setting_id":"set_1","name":"","description":""}]}
            Use 2-3 characters and 3-6 clips. Every on_screen entry must be a character id.
            Every clip setting_id must exist in scenes. Keep clip_prompt visual and specific.
            """;

    private static final String BRIEF_SYSTEM = """
            You are a film director. Write a concise Markdown creative brief.
            Include: creative concept, emotional arc, timed shot plan, character identity locks,
            scene geography, continuity rules, and visual style. Do not return JSON.
            """;

    private static final String REVIEW_BRIEF_SYSTEM = """
            Review the brief for missing identity, geography, motion or continuity locks.
            Return JSON only: {"ok":true,"patched_brief_markdown":"","notes":""}.
            patched_brief_markdown may be empty when no change is needed.
            """;

    private static final String STORYBOARD_SYSTEM = """
            Turn the brief into a shot list. Return JSON only:
            {"shots":[{"clip_index":1,"title":"","action":"","camera":"",
            "on_screen":["char_1"],"setting_id":"set_1","clip_prompt":"","timeline":"0-5s"}],
            "language_lock":"","bgm_lock":""}
            Keep 3-6 shots. Each shot must have a setting id and visual clip_prompt.
            """;

    private static final String REVIEW_STORYBOARD_SYSTEM = """
            Review the shot list for missing continuity, empty on_screen, or unknown setting ids.
            Return JSON only: {"ok":true,"shots":[...]} using the same shot shape.
            """;

    private final DesignerTextModelPort model;
    private final SmartGraphBuilder graphBuilder = new SmartGraphBuilder();

    /**
     * Create the director chain.
     *
     * @param model text model port
     */
    public DesignerDirectorChain(DesignerTextModelPort model) {
        this.model = model;
    }

    /**
     * Author a graph from a user prompt.
     *
     * @param prompt user prompt
     * @param projectId project id
     * @param identity identity metadata for the graph
     * @return director result
     */
    public DirectorResult run(String prompt, String projectId, Map<String, Object> identity) {
        if (prompt == null || prompt.isBlank()) {
            throw new DesignerNodeException("prompt is required");
        }
        LOG.info("Director chain start: project={}, promptChars={}", projectId, prompt.length());
        ScriptAnalysis analysis = analyze(prompt);
        String brief = authorBrief(prompt, analysis);
        brief = reviewBrief(brief);
        List<ClipSpec> shots = authorStoryboard(prompt, brief, analysis);
        shots = reviewStoryboard(shots, analysis);
        validate(analysis.characters(), shots, analysis.scenes());
        ScriptAnalysis finalAnalysis = new ScriptAnalysis(analysis.storyName(), analysis.styleLock(),
                analysis.characters(), shots, analysis.scenes());
        String storyboard = renderStoryboard(finalAnalysis);
        Map<String, Object> metadata = new LinkedHashMap<>(identity);
        metadata.put("video_settings", Map.of("aspect_ratio", "16:9", "resolution", "768P"));
        metadata.put("image_settings", Map.of("aspect_ratio", "16:9", "resolution", "1K"));
        DesignerGraph graph = graphBuilder.build(projectId, prompt, finalAnalysis, brief, storyboard,
                metadata);
        LOG.info("Director chain complete: graph={}, nodes={}, edges={}, characters={}, scenes={}, clips={}",
                graph.graphId(), graph.nodes(), graph.edges(),
                finalAnalysis.characters(), finalAnalysis.scenes(),
                finalAnalysis.clips());
        return new DirectorResult(graph, finalAnalysis, brief, storyboard);
    }

    private ScriptAnalysis analyze(String prompt) {
        LOG.info("Director stage analyze: promptChars={}", prompt.length());
        String reply = model.generate(ANALYSIS_SYSTEM, prompt, JSON_TOKENS);
        JsonNode root = DirectorJson.extractObject(reply);
        String storyName = root.path("story_name").asText("Untitled");
        Map<String, Object> styleLock = map(root.path("style_lock"));
        List<CharacterSpec> characters = characters(root.path("characters"));
        List<SceneSpec> scenes = scenes(root.path("scenes"));
        List<ClipSpec> clips = clips(root.path("clips"), characters);
        if (characters.isEmpty() || clips.isEmpty()) {
            throw new DesignerNodeException("model returned no usable characters or clips");
        }
        if (scenes.isEmpty()) {
            scenes = List.of(new SceneSpec("set_1", "Scene 1", prompt));
        }
        LOG.info("Director stage analyze done: story={}, characters={}, scenes={}, clips={}",
                storyName, characters.size(), scenes.size(), clips.size());
        return new ScriptAnalysis(storyName, styleLock, characters, clips, scenes);
    }

    private String authorBrief(String prompt, ScriptAnalysis analysis) {
        LOG.info("Director stage brief start: characters={}, clips={}",
                analysis.characters().size(), analysis.clips().size());
        String user = "User brief:\n" + prompt + "\n\nAnalysis:\n" + json(analysis);
        String brief = model.generate(BRIEF_SYSTEM, user, BRIEF_TOKENS);
        if (brief == null || brief.strip().length() < 80) {
            throw new DesignerNodeException("model returned no usable brief");
        }
        String text = brief.strip();
        LOG.info("Director stage brief done: chars={}", text.length());
        return text.startsWith("#") ? text : "# Brief\n\n" + text;
    }

    private String reviewBrief(String brief) {
        try {
            JsonNode root = DirectorJson.extractObject(model.generate(REVIEW_BRIEF_SYSTEM,
                    brief, JSON_TOKENS));
            String patched = root.path("patched_brief_markdown").asText("");
            boolean replaced = patched.strip().length() > 80;
            LOG.info("Director stage brief review: replaced={}, chars={}", replaced,
                    replaced ? patched.length() : brief.length());
            return replaced ? patched.strip() : brief;
        } catch (DesignerNodeException ex) {
            LOG.info("Director stage brief review skipped: {}", ex.getMessage());
            return brief;
        }
    }

    private List<ClipSpec> authorStoryboard(String prompt, String brief, ScriptAnalysis analysis) {
        LOG.info("Director stage storyboard start: briefChars={}", brief.length());
        String user = "User brief:\n" + prompt + "\n\nApproved brief:\n" + brief
                + "\n\nCharacters and scenes:\n" + json(analysis);
        try {
            JsonNode root = DirectorJson.extractObject(model.generate(STORYBOARD_SYSTEM, user,
                    JSON_TOKENS));
            List<ClipSpec> shots = clips(root.path("shots"), analysis.characters());
            List<ClipSpec> result = shots.isEmpty() ? analysis.clips() : shots;
            LOG.info("Director stage storyboard done: shots={}", result.size());
            return result;
        } catch (DesignerNodeException ex) {
            LOG.info("Director stage storyboard fallback: {}", ex.getMessage());
            return analysis.clips();
        }
    }

    private List<ClipSpec> reviewStoryboard(List<ClipSpec> shots, ScriptAnalysis analysis) {
        try {
            String user = "Shots:\n" + json(shots) + "\n\nCharacters/scenes:\n" + json(analysis);
            JsonNode root = DirectorJson.extractObject(model.generate(REVIEW_STORYBOARD_SYSTEM,
                    user, JSON_TOKENS));
            List<ClipSpec> reviewed = clips(root.path("shots"), analysis.characters());
            List<ClipSpec> result = reviewed.isEmpty() ? shots : reviewed;
            LOG.info("Director stage storyboard review: shots={}", result.size());
            return result;
        } catch (DesignerNodeException ex) {
            LOG.info("Director stage storyboard review skipped: {}", ex.getMessage());
            return shots;
        }
    }

    private static void validate(List<CharacterSpec> characters, List<ClipSpec> clips,
            List<SceneSpec> scenes) {
        LOG.info("Director stage validate: characters={}, clips={}, scenes={}",
                characters.size(), clips.size(), scenes.size());
        if (clips.isEmpty()) {
            throw new DesignerNodeException("validate_plan: no shots");
        }
        var characterIds = characters.stream().map(CharacterSpec::id).collect(java.util.stream.Collectors.toSet());
        var settingIds = scenes.stream().map(SceneSpec::settingId).collect(java.util.stream.Collectors.toSet());
        for (ClipSpec clip : clips) {
            if (!settingIds.contains(clip.settingId())) {
                throw new DesignerNodeException("validate_plan: shot " + clip.clipIndex()
                        + " has unknown setting " + clip.settingId());
            }
            if (clip.onScreen().isEmpty()) {
                throw new DesignerNodeException("validate_plan: shot " + clip.clipIndex()
                        + " has empty on_screen");
            }
            for (String id : clip.onScreen()) {
                if (!characterIds.contains(id)) {
                    throw new DesignerNodeException("validate_plan: shot " + clip.clipIndex()
                            + " has unknown character " + id);
                }
            }
        }
    }

    private static List<CharacterSpec> characters(JsonNode array) {
        List<CharacterSpec> result = new ArrayList<>();
        if (!array.isArray()) {
            return result;
        }
        int index = 0;
        for (JsonNode item : array) {
            index++;
            String id = item.path("id").asText("char_" + index);
            String name = item.path("name").asText("Character " + index);
            String description = item.path("description").asText("");
            result.add(new CharacterSpec(id, name, description));
        }
        return result;
    }

    private static List<SceneSpec> scenes(JsonNode array) {
        List<SceneSpec> result = new ArrayList<>();
        if (!array.isArray()) {
            return result;
        }
        int index = 0;
        for (JsonNode item : array) {
            index++;
            result.add(new SceneSpec(item.path("setting_id").asText("set_" + index),
                    item.path("name").asText("Scene " + index),
                    item.path("description").asText("")));
        }
        return result;
    }

    private static List<ClipSpec> clips(JsonNode array, List<CharacterSpec> characters) {
        List<ClipSpec> result = new ArrayList<>();
        if (!array.isArray()) {
            return result;
        }
        String fallbackCharacter = characters.isEmpty() ? "char_1" : characters.get(0).id();
        int index = 0;
        for (JsonNode item : array) {
            index++;
            List<String> onScreen = new ArrayList<>();
            JsonNode rawOnScreen = item.path("on_screen");
            if (rawOnScreen.isArray()) {
                rawOnScreen.forEach(value -> onScreen.add(value.asText()));
            }
            if (onScreen.isEmpty()) {
                onScreen.add(fallbackCharacter);
            }
            result.add(new ClipSpec(item.path("clip_index").asInt(index),
                    item.path("title").asText(""),
                    item.path("action").asText(""),
                    item.path("camera").asText(""),
                    onScreen,
                    item.path("setting_id").asText("set_1"),
                    item.path("clip_prompt").asText(""),
                    item.path("timeline").asText("")));
        }
        return result;
    }

    private static Map<String, Object> map(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return result;
    }

    private static String renderStoryboard(ScriptAnalysis analysis) {
        StringBuilder builder = new StringBuilder();
        builder.append("| # | Time | Shot | Camera | Action | Cast | Setting |\n");
        builder.append("|---|---|---|---|---|---|---|\n");
        for (ClipSpec clip : analysis.clips()) {
            builder.append("| ").append(clip.clipIndex()).append(" | ")
                    .append(escape(clip.timeline())).append(" | ")
                    .append(escape(clip.title())).append(" | ")
                    .append(escape(clip.camera())).append(" | ")
                    .append(escape(clip.action())).append(" | ")
                    .append(escape(String.join(", ", clip.onScreen()))).append(" | ")
                    .append(escape(clip.settingId())).append(" |\n");
        }
        return builder.toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace('\n', ' ');
    }

    private static String json(Object value) {
        try {
            return DesignerJson.mapper().writeValueAsString(value);
        } catch (IOException ex) {
            throw new DesignerNodeException("failed to serialize director state", ex);
        }
    }
}
